#include <QSettings>

#include "main.h"
#include "airpods_packets.h"
#include "logger.h"
#include "mediacontroller.h"
#include "trayiconmanager.h"
#include "enums.h"
#include "battery.hpp"

using namespace AirpodsTrayApp::Enums;

Q_LOGGING_CATEGORY(airpodsApp, "airpodsApp")

class AirPodsTrayApp : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString batteryStatus READ batteryStatus NOTIFY batteryStatusChanged)
    Q_PROPERTY(QString earDetectionStatus READ earDetectionStatus NOTIFY earDetectionStatusChanged)
    Q_PROPERTY(int noiseControlMode READ noiseControlMode WRITE setNoiseControlMode NOTIFY noiseControlModeChanged)
    Q_PROPERTY(bool conversationalAwareness READ conversationalAwareness WRITE setConversationalAwareness NOTIFY conversationalAwarenessChanged)
    Q_PROPERTY(int adaptiveNoiseLevel READ adaptiveNoiseLevel WRITE setAdaptiveNoiseLevel NOTIFY adaptiveNoiseLevelChanged)
    Q_PROPERTY(bool adaptiveModeActive READ adaptiveModeActive NOTIFY noiseControlModeChanged)
    Q_PROPERTY(QString deviceName READ deviceName NOTIFY deviceNameChanged)
    Q_PROPERTY(Battery* battery READ getBattery NOTIFY batteryStatusChanged)
    Q_PROPERTY(bool oneOrMorePodsInCase READ oneOrMorePodsInCase NOTIFY earDetectionStatusChanged)

public:
    AirPodsTrayApp(bool debugMode) 
      : debugMode(debugMode)
      , m_battery(new Battery(this)) {
        if (debugMode) {
            QLoggingCategory::setFilterRules("airpodsApp.debug=true");
        } else {
            QLoggingCategory::setFilterRules("airpodsApp.debug=false");
        }
        LOG_INFO("Initializing AirPodsTrayApp");

        // Initialize tray icon and connect signals
        trayManager = new TrayIconManager(this);
        connect(trayManager, &TrayIconManager::trayClicked, this, &AirPodsTrayApp::onTrayIconActivated);
        connect(trayManager, &TrayIconManager::noiseControlChanged, this, qOverload<NoiseControlMode>(&AirPodsTrayApp::setNoiseControlMode));
        connect(trayManager, &TrayIconManager::conversationalAwarenessToggled, this, &AirPodsTrayApp::setConversationalAwareness);
        connect(this, &AirPodsTrayApp::batteryStatusChanged, trayManager, &TrayIconManager::updateBatteryStatus);
        connect(this, &AirPodsTrayApp::noiseControlModeChanged, trayManager, &TrayIconManager::updateNoiseControlState);
        connect(this, &AirPodsTrayApp::conversationalAwarenessChanged, trayManager, &TrayIconManager::updateConversationalAwareness);

        // Initialize MediaController and connect signals
        mediaController = new MediaController(this);
        connect(this, &AirPodsTrayApp::earDetectionStatusChanged, mediaController, &MediaController::handleEarDetection);
        connect(mediaController, &MediaController::mediaStateChanged, this, &AirPodsTrayApp::handleMediaStateChange);
        mediaController->initializeMprisInterface();
        mediaController->followMediaChanges();

        // load conversational awareness state
        setConversationalAwareness(loadConversationalAwarenessState());

        discoveryAgent = new QBluetoothDeviceDiscoveryAgent();
        discoveryAgent->setLowEnergyDiscoveryTimeout(15000);

        connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::deviceDiscovered, this, &AirPodsTrayApp::onDeviceDiscovered);
        connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::finished, this, &AirPodsTrayApp::onDiscoveryFinished);
        discoveryAgent->start();
        LOG_INFO("AirPodsTrayApp initialized and started device discovery");

        QBluetoothLocalDevice localDevice;
        connect(&localDevice, &QBluetoothLocalDevice::deviceConnected, this, &AirPodsTrayApp::onDeviceConnected);
        connect(&localDevice, &QBluetoothLocalDevice::deviceDisconnected, this, &AirPodsTrayApp::onDeviceDisconnected);

        const QList<QBluetoothAddress> connectedDevices = localDevice.connectedDevices();
        for (const QBluetoothAddress &address : connectedDevices) {
            QBluetoothDeviceInfo device(address, "", 0);
            if (isAirPodsDevice(device)) {
                connectToDevice(device);
                return;
            }
        }
        connect(phoneSocket, &QBluetoothSocket::readyRead, this, &AirPodsTrayApp::onPhoneDataReceived);

        QDBusInterface iface("org.bluez", "/org/bluez", "org.bluez.Adapter1");
        QDBusReply<QVariant> reply = iface.call("GetServiceRecords", QString::fromUtf8("74ec2172-0bad-4d01-8f77-997b2be0722a"));
        if (reply.isValid()) {
            LOG_INFO("Service record found, proceeding with connection");
        } else {
            LOG_WARN("Service record not found, waiting for BLE broadcast");
        }

        listenForDeviceConnections();
        initializeDBus();
        initializeBluetooth();
    }

    ~AirPodsTrayApp() {
        delete trayIcon;
        delete trayMenu;
        delete discoveryAgent;
        delete bluezInterface;
        delete mprisInterface;
        delete socket;
        delete phoneSocket;
    }

    QString batteryStatus() const { return m_batteryStatus; }
    QString earDetectionStatus() const { return m_earDetectionStatus; }
    int noiseControlMode() const { return static_cast<int>(m_noiseControlMode); }
    bool conversationalAwareness() const { return m_conversationalAwareness; }
    bool adaptiveModeActive() const { return m_noiseControlMode == NoiseControlMode::Adaptive; }
    int adaptiveNoiseLevel() const { return m_adaptiveNoiseLevel; }
    QString deviceName() const { return m_deviceName; }
    Battery *getBattery() const { return m_battery; }
    bool oneOrMorePodsInCase() const { return m_earDetectionStatus.contains("In case"); }

private:
    bool debugMode;
    bool isConnectedLocally = false;
    struct {
        bool isAvailable = true;
    } CrossDevice;

    void initializeDBus() {
        QDBusConnection systemBus = QDBusConnection::systemBus();
        if (!systemBus.isConnected()) {
        }

        bluezInterface = new QDBusInterface("org.bluez",
                                        "/",
                                        "org.freedesktop.DBus.ObjectManager",
                                        systemBus,
                                        this);

        if (!bluezInterface->isValid()) {
            LOG_ERROR("Failed to connect to org.bluez DBus interface.");
            return;
        }

        connect(systemBus.interface(), &QDBusConnectionInterface::NameOwnerChanged,
                this, &AirPodsTrayApp::onNameOwnerChanged);

        systemBus.connect(QString(), QString(), "org.freedesktop.DBus.Properties", "PropertiesChanged",
                        this, SLOT(onDevicePropertiesChanged(QString, QVariantMap, QStringList)));

        systemBus.connect(QString(), QString(), "org.freedesktop.DBus.ObjectManager", "InterfacesAdded",
                        this, SLOT(onInterfacesAdded(QString, QVariantMap)));

        QDBusMessage msg = bluezInterface->call("GetManagedObjects");
        if (msg.type() == QDBusMessage::ErrorMessage) {
            LOG_ERROR("Error getting managed objects: " << msg.errorMessage());
            return;
        }

        QVariantMap objects = qdbus_cast<QVariantMap>(msg.arguments().at(0));
        for (auto it = objects.begin(); it != objects.end(); ++it) {
            if (it.key().startsWith("/org/bluez/hci0/dev_")) {
                LOG_INFO("Existing device: " << it.key());
            }
        }
        QDBusConnection::systemBus().registerObject("/me/kavishdevar/aln", this);
        QDBusConnection::systemBus().registerService("me.kavishdevar.aln");
    }

    bool isAirPodsDevice(const QBluetoothDeviceInfo &device)
    {
        return device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"));
    }

    void notifyAndroidDevice()
    {
        if (phoneSocket && phoneSocket->isOpen())
        {
            phoneSocket->write(AirPodsPackets::Phone::NOTIFICATION);
            LOG_DEBUG("Sent notification packet to Android: " << AirPodsPackets::Phone::NOTIFICATION.toHex());
        }
        else
        {
            LOG_WARN("Phone socket is not open, cannot send notification packet");
        }
    }
    void onNameOwnerChanged(const QString &name, const QString &oldOwner, const QString &newOwner) {
        if (name == "org.bluez") {
            if (newOwner.isEmpty()) {
                LOG_WARN("BlueZ has been stopped.");
            } else {
                LOG_INFO("BlueZ started.");
            }
        }
    }

    void onDevicePropertiesChanged(const QString &interface, const QVariantMap &changed, const QStringList &invalidated) {
        if (interface != "org.bluez.Device1")
            return;

        if (changed.contains("Connected")) {
            bool connected = changed.value("Connected").toBool();
            QString devicePath = sender()->objectName();
            LOG_INFO(QString("Device %1 connected: %2").arg(devicePath, connected ? "Yes" : "No"));

            if (connected) {
                const QBluetoothAddress address = QBluetoothAddress(devicePath.split("/").last().replace("_", ":"));
                QBluetoothDeviceInfo device(address, "", 0);
                if (isAirPodsDevice(device)) {
                    connectToDevice(device);
                }
            } else {
                disconnectDevice(devicePath);
            }
        }
    }

    void disconnectDevice(const QString &devicePath) {
        LOG_INFO("Disconnecting device at " << devicePath);
    }

    QDBusInterface *bluezInterface = nullptr;

public slots:
    void connectToDevice(const QString &address) {
        LOG_INFO("Connecting to device with address: " << address);
        QBluetoothAddress btAddress(address);
        QBluetoothDeviceInfo device(btAddress, "", 0);
        connectToDevice(device);
    }

    void showAvailableDevices() {
        LOG_INFO("Showing available devices");
        QStringList devices;
        const QList<QBluetoothDeviceInfo> discoveredDevices = discoveryAgent->discoveredDevices();
        for (const QBluetoothDeviceInfo &device : discoveredDevices) {
            devices << device.address().toString() + " - " + device.name();
        }
        bool ok;
        QString selectedDevice = QInputDialog::getItem(nullptr, "Select Device", "Devices:", devices, 0, false, &ok);
        if (ok && !selectedDevice.isEmpty()) {
            QString address = selectedDevice.split(" - ").first();
            connectToDevice(address);
        }
    }

    void setNoiseControlMode(NoiseControlMode mode)
    {
        LOG_INFO("Setting noise control mode to: " << mode);
        if (m_noiseControlMode == mode)
        {
            LOG_INFO("Noise control mode is already " << mode);
            return;
        }
        QByteArray packet = AirPodsPackets::NoiseControl::getPacketForMode(mode);
        writePacketToSocket(packet, "Noise control mode packet written: ");
    }
    void setNoiseControlMode(int mode)
    {
        setNoiseControlMode(static_cast<NoiseControlMode>(mode));
    }

    void setConversationalAwareness(bool enabled)
    {
        if (m_conversationalAwareness == enabled)
        {
            LOG_INFO("Conversational awareness is already " << (enabled ? "enabled" : "disabled"));
            return;
        }

        LOG_INFO("Setting conversational awareness to: " << (enabled ? "enabled" : "disabled"));
        QByteArray packet = enabled ? AirPodsPackets::ConversationalAwareness::ENABLED
                                    : AirPodsPackets::ConversationalAwareness::DISABLED;

        writePacketToSocket(packet, "Conversational awareness packet written: ");
        m_conversationalAwareness = enabled;
        emit conversationalAwarenessChanged(enabled);
        saveConversationalAwarenessState();
    }

    void setAdaptiveNoiseLevel(int level)
    {
        level = qBound(0, level, 100);
        if (m_adaptiveNoiseLevel != level && adaptiveModeActive())
        {
            m_adaptiveNoiseLevel = level;
            QByteArray packet = AirPodsPackets::AdaptiveNoise::getPacket(level);
            writePacketToSocket(packet, "Adaptive noise level packet written: ");
            emit adaptiveNoiseLevelChanged(level);
        }
    }

    void renameAirPods(const QString &newName)
    {
        if (newName.isEmpty())
        {
            LOG_WARN("Cannot set empty name");
            return;
        }
        if (newName.size() > 32)
        {
            LOG_WARN("Name is too long, must be 32 characters or less");
            return;
        }
        if (newName == m_deviceName)
        {
            LOG_INFO("Name is already set to: " << newName);
            return;
        }

        QByteArray packet = AirPodsPackets::Rename::getPacket(newName);
        if (writePacketToSocket(packet, "Rename packet written: "))
        {
            LOG_INFO("Sent rename command for new name: " << newName);
            m_deviceName = newName;
            emit deviceNameChanged(newName);
        }
        else
        {
            LOG_ERROR("Failed to send rename command: socket not open");
        }
    }

    bool writePacketToSocket(const QByteArray &packet, const QString &logMessage)
    {
        if (socket && socket->isOpen())
        {
            socket->write(packet);
            LOG_DEBUG(logMessage << packet.toHex());
            return true;
        }
        else
        {
            LOG_ERROR("Socket is not open, cannot write packet");
            return false;
        }
    }

    bool loadConversationalAwarenessState()
    {
        QSettings settings;
        return settings.value("conversationalAwareness", false).toBool();
    }

    void saveConversationalAwarenessState()
    {
        QSettings settings;
        settings.setValue("conversationalAwareness", m_conversationalAwareness);
        settings.sync();
    }

private slots:
    void onTrayIconActivated()
    {
        QQuickWindow *window = qobject_cast<QQuickWindow *>(
            QGuiApplication::topLevelWindows().constFirst());
        if (window)
        {
            window->show();
            window->raise();
            window->requestActivate();
        }
    }

    void onDeviceDiscovered(const QBluetoothDeviceInfo &device) {
        QByteArray manufacturerData = device.manufacturerData(MANUFACTURER_ID);
        if (manufacturerData.startsWith(MANUFACTURER_DATA)) {
            LOG_INFO("Detected AirPods via BLE manufacturer data");
            connectToDevice(device.address().toString());
        }
        LOG_INFO("Device discovered: " << device.name() << " (" << device.address().toString() << ")");
        if (isAirPodsDevice(device)) {
            LOG_DEBUG("Found AirPods device: " + device.name());
            connectToDevice(device);
        }
    }

    void onDiscoveryFinished() {
        LOG_INFO("Device discovery finished");
        discoveryAgent->start();
        const QList<QBluetoothDeviceInfo> discoveredDevices = discoveryAgent->discoveredDevices();
        for (const QBluetoothDeviceInfo &device : discoveredDevices) {
            if (isAirPodsDevice(device)) {
                connectToDevice(device);
                return;
            }
        }
        LOG_WARN("No device with the specified UUID found");
    }

    void onDeviceConnected(const QBluetoothAddress &address) {
        LOG_INFO("Device connected: " << address.toString());
        QBluetoothDeviceInfo device(address, "", 0);
        if (isAirPodsDevice(device)) {
            connectToDevice(device);
        }
    }

    void onDeviceDisconnected(const QBluetoothAddress &address)
    {
        LOG_INFO("Device disconnected: " << address.toString());
        if (socket)
        {
            LOG_WARN("Socket is still open, closing it");
            socket->close();
            socket = nullptr;
        }
        if (phoneSocket && phoneSocket->isOpen())
        {
            phoneSocket->write(AirPodsPackets::Connection::AIRPODS_DISCONNECTED);
            LOG_DEBUG("AIRPODS_DISCONNECTED packet written: " << AirPodsPackets::Connection::AIRPODS_DISCONNECTED.toHex());
        }
    }

    void parseMetadata(const QByteArray &data)
    {
        // Verify the data starts with the METADATA header
        if (!data.startsWith(AirPodsPackets::Parse::METADATA))
        {
            LOG_ERROR("Invalid metadata packet: Incorrect header");
            return;
        }

        int pos = AirPodsPackets::Parse::METADATA.size(); // Start after the header

        // Check if there is enough data to skip the initial bytes (based on example structure)
        if (data.size() < pos + 6)
        {
            LOG_ERROR("Metadata packet too short to parse initial bytes");
            return;
        }
        pos += 6; // Skip 6 bytes after the header as per example structure

        auto extractString = [&data, &pos]() -> QString
        {
            if (pos >= data.size())
            {
                return QString();
            }
            int start = pos;
            while (pos < data.size() && data.at(pos) != '\0')
            {
                ++pos;
            }
            QString str = QString::fromUtf8(data.mid(start, pos - start));
            if (pos < data.size())
            {
                ++pos; // Move past the null terminator
            }
            return str;
        };

        m_deviceName = extractString();
        QString modelNumber = extractString();
        QString manufacturer = extractString();
        QString hardwareVersion = extractString();
        QString firmwareVersion = extractString();
        QString firmwareVersion2 = extractString();
        QString softwareVersion = extractString();
        QString appIdentifier = extractString();
        QString serialNumber1 = extractString();
        QString serialNumber2 = extractString();
        QString unknownNumeric = extractString();
        QString unknownHash = extractString();
        QString trailingByte = extractString();

        emit deviceNameChanged(m_deviceName);

        // Log extracted metadata
        LOG_INFO("Parsed AirPods metadata:");
        LOG_INFO("Device Name: " << m_deviceName);
        LOG_INFO("Model Number: " << modelNumber);
        LOG_INFO("Manufacturer: " << manufacturer);
        LOG_INFO("Hardware Version: " << hardwareVersion);
        LOG_INFO("Firmware Version: " << firmwareVersion);
        LOG_INFO("Firmware Version2: " << firmwareVersion2);
        LOG_INFO("Software Version: " << softwareVersion);
        LOG_INFO("App Identifier: " << appIdentifier);
        LOG_INFO("Serial Number 1: " << serialNumber1);
        LOG_INFO("Serial Number 2: " << serialNumber2);
        LOG_INFO("Unknown Numeric: " << unknownNumeric);
        LOG_INFO("Unknown Hash: " << unknownHash);
        LOG_INFO("Trailing Byte: " << trailingByte);
    }

    void connectToDevice(const QBluetoothDeviceInfo &device) {
        if (socket && socket->isOpen() && socket->peerAddress() == device.address()) {
            LOG_INFO("Already connected to the device: " << device.name());
            return;
        }

        LOG_INFO("Connecting to device: " << device.name());
        QBluetoothSocket *localSocket = new QBluetoothSocket(QBluetoothServiceInfo::L2capProtocol);
        connect(localSocket, &QBluetoothSocket::connected, this, [this, localSocket]() {
            LOG_INFO("Connected to device, sending initial packets");
            discoveryAgent->stop();

            QByteArray handshakePacket = AirPodsPackets::Connection::HANDSHAKE;
            QByteArray setSpecificFeaturesPacket = AirPodsPackets::Connection::SET_SPECIFIC_FEATURES;
            QByteArray requestNotificationsPacket = AirPodsPackets::Connection::REQUEST_NOTIFICATIONS;

            qint64 bytesWritten = localSocket->write(handshakePacket);
            LOG_DEBUG("Handshake packet written: " << handshakePacket.toHex() << ", bytes written: " << bytesWritten);
            localSocket->write(setSpecificFeaturesPacket);
            LOG_DEBUG("Set specific features packet written: " << setSpecificFeaturesPacket.toHex());
            localSocket->write(requestNotificationsPacket);
            LOG_DEBUG("Request notifications packet written: " << requestNotificationsPacket.toHex());
            connect(localSocket, &QBluetoothSocket::bytesWritten, this, [this, localSocket, setSpecificFeaturesPacket, requestNotificationsPacket](qint64 bytes) {
                LOG_INFO("Bytes written: " << bytes);
                if (bytes > 0) {
                    static int step = 0;
                    switch (step) {
                        case 0:
                            localSocket->write(setSpecificFeaturesPacket);
                            LOG_DEBUG("Set specific features packet written: " << setSpecificFeaturesPacket.toHex());
                            step++;
                            break;
                        case 1:
                            localSocket->write(requestNotificationsPacket);
                            LOG_DEBUG("Request notifications packet written: " << requestNotificationsPacket.toHex());
                            step++;
                            break;
                    }
                }
            });

            connect(localSocket, &QBluetoothSocket::readyRead, this, [this, localSocket]() {
                QByteArray data = localSocket->readAll();
                QMetaObject::invokeMethod(this, "parseData", Qt::QueuedConnection, Q_ARG(QByteArray, data));
                QMetaObject::invokeMethod(this, "relayPacketToPhone", Qt::QueuedConnection, Q_ARG(QByteArray, data));
            });

            QTimer::singleShot(500, this, [localSocket, setSpecificFeaturesPacket, requestNotificationsPacket]() {
                if (localSocket->isOpen()) {
                    localSocket->write(setSpecificFeaturesPacket);
                    LOG_DEBUG("Resent set specific features packet: " << setSpecificFeaturesPacket.toHex());
                    localSocket->write(requestNotificationsPacket);
                    LOG_DEBUG("Resent request notifications packet: " << requestNotificationsPacket.toHex());
                } else {
                    LOG_WARN("Socket is not open, cannot resend packets");
                }
            });
        });

        connect(localSocket, QOverload<QBluetoothSocket::SocketError>::of(&QBluetoothSocket::errorOccurred), this, [this, localSocket](QBluetoothSocket::SocketError error) {
            LOG_ERROR("Socket error: " << error << ", " << localSocket->errorString());
        });

        localSocket->connectToService(device.address(), QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"));
        socket = localSocket;
        connectedDeviceMacAddress = device.address().toString().replace(":", "_");
        mediaController->setConnectedDeviceMacAddress(connectedDeviceMacAddress);
        notifyAndroidDevice();
    }

    QString getEarStatus(char value)
    {
        return (value == 0x00) ? "In Ear" : (value == 0x01) ? "Out of Ear"
                                                            : "In case";
    }

    void parseData(const QByteArray &data)
    {
        LOG_DEBUG("Received: " << data.toHex());

        // Noise Control Mode
        if (data.size() == 11 && data.startsWith(AirPodsPackets::NoiseControl::HEADER))
        {
            quint8 rawMode = data[7] - 1; // Offset still needed due to protocol
            if (rawMode >= (int)NoiseControlMode::MinValue && rawMode <= (int)NoiseControlMode::MaxValue)
            {
                m_noiseControlMode = static_cast<NoiseControlMode>(rawMode);
                LOG_INFO("Noise control mode: " << rawMode);
                emit noiseControlModeChanged(m_noiseControlMode);
            }
            else
            {
                LOG_ERROR("Invalid noise control mode value received: " << rawMode);
            }
        }
        // Ear Detection
        else if (data.size() == 8 && data.startsWith(AirPodsPackets::Parse::EAR_DETECTION))
        {
            char primary = data[6];
            char secondary = data[7];
            m_earDetectionStatus = QString("Primary: %1, Secondary: %2")
                                       .arg(getEarStatus(primary), getEarStatus(secondary));
            LOG_INFO("Ear detection status: " << m_earDetectionStatus);
            emit earDetectionStatusChanged(m_earDetectionStatus);
        }
        // Battery Status
        else if (data.size() == 22 && data.startsWith(AirPodsPackets::Parse::BATTERY_STATUS))
        {
            m_battery->parsePacket(data);

            int leftLevel = m_battery->getState(Battery::Component::Left).level;
            int rightLevel = m_battery->getState(Battery::Component::Right).level;
            int caseLevel = m_battery->getState(Battery::Component::Case).level;
            m_batteryStatus = QString("Left: %1%, Right: %2%, Case: %3%")
                                  .arg(leftLevel)
                                  .arg(rightLevel)
                                  .arg(caseLevel);
            LOG_INFO("Battery status: " << m_batteryStatus);
            emit batteryStatusChanged(m_batteryStatus);
        }
        // Conversational Awareness Data
        else if (data.size() == 10 && data.startsWith(AirPodsPackets::ConversationalAwareness::DATA_HEADER))
        {
            LOG_INFO("Received conversational awareness data");
            mediaController->handleConversationalAwareness(data);
        }
        else if (data.startsWith(AirPodsPackets::Parse::METADATA))
        {
            parseMetadata(data);
        }
        else
        {
            LOG_DEBUG("Unrecognized packet format: " << data.toHex());
        }
    }

    void connectToPhone() {
        if (phoneSocket && phoneSocket->isOpen()) {
            LOG_INFO("Already connected to the phone");
            return;
        }

        QBluetoothAddress phoneAddress(PHONE_MAC_ADDRESS);
        phoneSocket = new QBluetoothSocket(QBluetoothServiceInfo::L2capProtocol);
        connect(phoneSocket, &QBluetoothSocket::connected, this, [this]() {
            LOG_INFO("Connected to phone");
            if (!lastBatteryStatus.isEmpty()) {
                phoneSocket->write(lastBatteryStatus);
                LOG_DEBUG("Sent last battery status to phone: " << lastBatteryStatus.toHex());
            }
            if (!lastEarDetectionStatus.isEmpty()) {
                phoneSocket->write(lastEarDetectionStatus);
                LOG_DEBUG("Sent last ear detection status to phone: " << lastEarDetectionStatus.toHex());
            }
        });

        connect(phoneSocket, QOverload<QBluetoothSocket::SocketError>::of(&QBluetoothSocket::errorOccurred), this, [this](QBluetoothSocket::SocketError error) {
            LOG_ERROR("Phone socket error: " << error << ", " << phoneSocket->errorString());
        });

        phoneSocket->connectToService(phoneAddress, QBluetoothUuid("1abbb9a4-10e4-4000-a75c-8953c5471342"));
    }

    void relayPacketToPhone(const QByteArray &packet)
    {
        if (phoneSocket && phoneSocket->isOpen())
        {
            phoneSocket->write(AirPodsPackets::Phone::NOTIFICATION + packet);
        }
        else
        {
            connectToPhone();
            LOG_WARN("Phone socket is not open, cannot relay packet");
        }
    }

    void handlePhonePacket(const QByteArray &packet) {
        if (packet.startsWith(AirPodsPackets::Phone::NOTIFICATION))
        {
            QByteArray airpodsPacket = packet.mid(4);
            if (socket && socket->isOpen()) {
                socket->write(airpodsPacket);
                LOG_DEBUG("Relayed packet to AirPods: " << airpodsPacket.toHex());
            } else {
                LOG_ERROR("Socket is not open, cannot relay packet to AirPods");
            }
        }
        else if (packet.startsWith(AirPodsPackets::Phone::CONNECTED))
        {
            LOG_INFO("AirPods connected");
            isConnectedLocally = true;
            CrossDevice.isAvailable = false;
        }
        else if (packet.startsWith(AirPodsPackets::Phone::DISCONNECTED))
        {
            LOG_INFO("AirPods disconnected");
            isConnectedLocally = false;
            CrossDevice.isAvailable = true;
        }
        else if (packet.startsWith(AirPodsPackets::Phone::STATUS_REQUEST))
        {
            LOG_INFO("Connection status request received");
            QByteArray response = (socket && socket->isOpen()) ? AirPodsPackets::Phone::CONNECTED
                                                               : AirPodsPackets::Phone::DISCONNECTED;
            phoneSocket->write(response);
            LOG_DEBUG("Sent connection status response: " << response.toHex());
        }
        else if (packet.startsWith(AirPodsPackets::Phone::DISCONNECT_REQUEST))
        {
            LOG_INFO("Disconnect request received");
            if (socket && socket->isOpen()) {
                socket->close();
                LOG_INFO("Disconnected from AirPods");
                QProcess process;
                process.start("bluetoothctl", QStringList() << "disconnect" << connectedDeviceMacAddress.replace("_", ":"));
                process.waitForFinished();
                QString output = process.readAllStandardOutput().trimmed();
                LOG_INFO("Bluetoothctl output: " << output);
                isConnectedLocally = false;
                CrossDevice.isAvailable = true;
            }
        }
        else
        {
            if (socket && socket->isOpen()) {
                socket->write(packet);
                LOG_DEBUG("Relayed packet to AirPods: " << packet.toHex());
            } else {
                LOG_ERROR("Socket is not open, cannot relay packet to AirPods");
            }
        }
    }

    void onPhoneDataReceived() {
        QByteArray data = phoneSocket->readAll();
        LOG_DEBUG("Data received from phone: " << data.toHex());
        QMetaObject::invokeMethod(this, "handlePhonePacket", Qt::QueuedConnection, Q_ARG(QByteArray, data));
    }

    void listenForDeviceConnections() {
        QDBusConnection systemBus = QDBusConnection::systemBus();
        systemBus.connect(QString(), QString(), "org.freedesktop.DBus.Properties", "PropertiesChanged", this, SLOT(onDevicePropertiesChanged(QString, QVariantMap, QStringList)));
        systemBus.connect(QString(), QString(), "org.freedesktop.DBus.ObjectManager", "InterfacesAdded", this, SLOT(onInterfacesAdded(QString, QVariantMap)));
    }

    void onInterfacesAdded(QString path, QVariantMap interfaces) {
        if (interfaces.contains("org.bluez.Device1")) {
            QVariantMap deviceProps = interfaces["org.bluez.Device1"].toMap();
            if (deviceProps.contains("Connected") && deviceProps["Connected"].toBool()) {
                QString addr = deviceProps["Address"].toString();
                QBluetoothAddress btAddress(addr);
                QBluetoothDeviceInfo device(btAddress, "", 0);
                if (isAirPodsDevice(device)) {
                    connectToDevice(device);
                }
            }
        }
    }

    public:
        void handleMediaStateChange(MediaController::MediaState state) {
            if (state == MediaController::MediaState::Playing) {
                LOG_INFO("Media started playing, sending disconnect request to Android and taking over audio");
                sendDisconnectRequestToAndroid();
                connectToAirPods(true);
            }
        }

    void sendDisconnectRequestToAndroid()
    {
        if (phoneSocket && phoneSocket->isOpen())
        {
            phoneSocket->write(AirPodsPackets::Phone::DISCONNECT_REQUEST);
            LOG_DEBUG("Sent disconnect request to Android: " << AirPodsPackets::Phone::DISCONNECT_REQUEST.toHex());
        }
        else
        {
            LOG_WARN("Phone socket is not open, cannot send disconnect request");
        }
    }

    bool isPhoneConnected() {
        return phoneSocket && phoneSocket->isOpen();
    }

    void connectToAirPods(bool force) {
        if (socket && socket->isOpen()) {
            LOG_INFO("Already connected to AirPods");
            return;
        }

        if (force) {
            LOG_INFO("Forcing connection to AirPods");
            QProcess process;
            process.start("bluetoothctl", QStringList() << "connect" << connectedDeviceMacAddress.replace("_", ":"));
            process.waitForFinished();
            QString output = process.readAllStandardOutput().trimmed();
            LOG_INFO("Bluetoothctl output: " << output);
            if (output.contains("Connection successful")) {
                LOG_INFO("Connection successful, proceeding with L2CAP connection");
                QBluetoothAddress btAddress(connectedDeviceMacAddress.replace("_", ":"));
                forceL2capConnection(btAddress);
            } else {
                LOG_ERROR("Connection failed, cannot proceed with L2CAP connection");
            }
        }
        QBluetoothLocalDevice localDevice;
        const QList<QBluetoothAddress> connectedDevices = localDevice.connectedDevices();
        for (const QBluetoothAddress &address : connectedDevices) {
            QBluetoothDeviceInfo device(address, "", 0);
            LOG_DEBUG("Connected device: " << device.name() << " (" << device.address().toString() << ")");
            if (isAirPodsDevice(device)) {
                connectToDevice(device);
                return;
            }
        }
        LOG_WARN("AirPods not found among connected devices");
    }

    void forceL2capConnection(const QBluetoothAddress &address) {
        LOG_INFO("Retrying L2CAP connection for up to 10 seconds...");
        QBluetoothDeviceInfo device(address, "", 0);
        QElapsedTimer timer;
        timer.start();
        while (timer.elapsed() < 10000) {
            QProcess bcProcess;
            bcProcess.start("bluetoothctl", QStringList() << "connect" << address.toString());
            bcProcess.waitForFinished();
            QString output = bcProcess.readAllStandardOutput().trimmed();
            LOG_INFO("Bluetoothctl output: " << output);
            if (output.contains("Connection successful")) {
                connectToDevice(device);
                QThread::sleep(1);
                if (socket && socket->isOpen()) {
                    LOG_INFO("Successfully connected to device: " << address.toString());
                    return;
                }
            } else {
                LOG_WARN("Connection attempt failed, retrying...");
            }
        }
        LOG_ERROR("Failed to connect to device within 10 seconds: " << address.toString());
    }

    void initializeBluetooth() {
        connectToPhone();
    }

signals:
    void noiseControlModeChanged(NoiseControlMode mode);
    void earDetectionStatusChanged(const QString &status);
    void batteryStatusChanged(const QString &status);
    void conversationalAwarenessChanged(bool enabled);
    void adaptiveNoiseLevelChanged(int level);
    void deviceNameChanged(const QString &name);

private:
    QSystemTrayIcon *trayIcon;
    QMenu *trayMenu;
    QBluetoothDeviceDiscoveryAgent *discoveryAgent;
    QBluetoothSocket *socket = nullptr;
    QBluetoothSocket *phoneSocket = nullptr;
    QDBusInterface *mprisInterface;
    QString connectedDeviceMacAddress;
    QByteArray lastBatteryStatus;
    QByteArray lastEarDetectionStatus;
    MediaController* mediaController;
    TrayIconManager *trayManager;

    QString m_batteryStatus;
    QString m_earDetectionStatus;
    NoiseControlMode m_noiseControlMode = NoiseControlMode::Off;
    bool m_conversationalAwareness = false;
    int m_adaptiveNoiseLevel = 50;
    QString m_deviceName;
    Battery *m_battery;
};

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);

    bool debugMode = false;
    for (int i = 1; i < argc; ++i) {
        if (QString(argv[i]) == "--debug") {
            debugMode = true;
            break;
        }
    }

    QQmlApplicationEngine engine;
    qmlRegisterType<Battery>("me.kavishdevar.Battery", 1, 0, "Battery");
    AirPodsTrayApp trayApp(debugMode);
    engine.rootContext()->setContextProperty("airPodsTrayApp", &trayApp);
    engine.loadFromModule("linux", "Main");

    return app.exec();
}

#include "main.moc"
