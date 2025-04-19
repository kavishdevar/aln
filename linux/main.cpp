#include <QSettings>

#include "main.h"
#include "airpods_packets.h"
#include "logger.h"
#include "mediacontroller.h"
#include "trayiconmanager.h"
#include "enums.h"
#include "battery.hpp"
#include "BluetoothMonitor.h"

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
    Q_PROPERTY(QString podIcon READ podIcon NOTIFY modelChanged)
    Q_PROPERTY(QString caseIcon READ caseIcon NOTIFY modelChanged)
    Q_PROPERTY(bool leftPodInEar READ isLeftPodInEar NOTIFY primaryChanged)
    Q_PROPERTY(bool rightPodInEar READ isRightPodInEar NOTIFY primaryChanged)
    Q_PROPERTY(bool airpodsConnected READ areAirpodsConnected NOTIFY airPodsStatusChanged)
    Q_PROPERTY(int earDetectionBehavior READ earDetectionBehavior WRITE setEarDetectionBehavior NOTIFY earDetectionBehaviorChanged)
    Q_PROPERTY(bool crossDeviceEnabled READ crossDeviceEnabled WRITE setCrossDeviceEnabled NOTIFY crossDeviceEnabledChanged)

public:
    AirPodsTrayApp(bool debugMode) 
      : debugMode(debugMode)
      , m_battery(new Battery(this)) 
      , monitor(new BluetoothMonitor(this))
      , m_settings(new QSettings("AirPodsTrayApp", "AirPodsTrayApp")){
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

        connect(monitor, &BluetoothMonitor::deviceConnected, this, &AirPodsTrayApp::bluezDeviceConnected);
        connect(monitor, &BluetoothMonitor::deviceDisconnected, this, &AirPodsTrayApp::bluezDeviceDisconnected);

        connect(m_battery, &Battery::primaryChanged, this, &AirPodsTrayApp::primaryChanged);

        // Load settings
        CrossDevice.isEnabled = loadCrossDeviceEnabled();
        setEarDetectionBehavior(loadEarDetectionSettings());

        monitor->checkAlreadyConnectedDevices();
        LOG_INFO("AirPodsTrayApp initialized");

        QBluetoothLocalDevice localDevice;

        const QList<QBluetoothAddress> connectedDevices = localDevice.connectedDevices();
        for (const QBluetoothAddress &address : connectedDevices) {
            QBluetoothDeviceInfo device(address, "", 0);
            if (isAirPodsDevice(device)) {
                connectToDevice(device);
                return;
            }
        }

        initializeDBus();
        initializeBluetooth();
    }

    ~AirPodsTrayApp() {
        saveCrossDeviceEnabled();
        saveEarDetectionSettings();

        delete trayIcon;
        delete trayMenu;
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
    QString podIcon() const { return getModelIcon(m_model).first; }
    QString caseIcon() const { return getModelIcon(m_model).second; }
    bool isLeftPodInEar() const { 
        if (m_battery->getPrimaryPod() == Battery::Component::Left) {
            return m_primaryInEar;
        } else {
            return m_secoundaryInEar;
        }
    }
    bool isRightPodInEar() const { 
        if (m_battery->getPrimaryPod() == Battery::Component::Right) {
            return m_primaryInEar;
        } else {
            return m_secoundaryInEar;
        }
    }
    bool areAirpodsConnected() const { return socket && socket->isOpen() && socket->state() == QBluetoothSocket::SocketState::ConnectedState; }
    int earDetectionBehavior() const { return mediaController->getEarDetectionBehavior(); }
    bool crossDeviceEnabled() const { return CrossDevice.isEnabled; }

private:
    bool debugMode;
    bool isConnectedLocally = false;
    struct {
        bool isAvailable = true;
        bool isEnabled = true; // Ability to disable the feature
    } CrossDevice;

    void initializeDBus() { }

    bool isAirPodsDevice(const QBluetoothDeviceInfo &device)
    {
        return device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"));
    }

    void notifyAndroidDevice()
    {
        if (!CrossDevice.isEnabled) {
            return;
        }

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

    void disconnectDevice(const QString &devicePath) {
        LOG_INFO("Disconnecting device at " << devicePath);
    }

public slots:
    void connectToDevice(const QString &address) {
        LOG_INFO("Connecting to device with address: " << address);
        QBluetoothAddress btAddress(address);
        QBluetoothDeviceInfo device(btAddress, "", 0);
        connectToDevice(device);
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
    }

    void initiateMagicPairing()
    {
        if (!socket || !socket->isOpen())
        {
            LOG_ERROR("Socket nicht offen, Magic Pairing kann nicht gestartet werden");
            return;
        }

        writePacketToSocket(AirPodsPackets::MagicPairing::REQUEST_MAGIC_CLOUD_KEYS, "Magic Pairing packet written: ");
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

    void setEarDetectionBehavior(int behavior)
    {
        if (behavior == earDetectionBehavior())
        {
            LOG_INFO("Ear detection behavior is already set to: " << behavior);
            return;
        }

        mediaController->setEarDetectionBehavior(static_cast<MediaController::EarDetectionBehavior>(behavior));
        saveEarDetectionSettings();
        emit earDetectionBehaviorChanged(behavior);
    }

    void setCrossDeviceEnabled(bool enabled)
    {
        if (CrossDevice.isEnabled == enabled)
        {
            LOG_INFO("Cross-device feature is already " << (enabled ? "enabled" : "disabled"));
            return;
        }

        CrossDevice.isEnabled = enabled;
        saveCrossDeviceEnabled();
        connectToPhone();
        emit crossDeviceEnabledChanged(enabled);
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

    bool loadCrossDeviceEnabled() { return m_settings->value("crossdevice/enabled", false).toBool(); }
    void saveCrossDeviceEnabled() { m_settings->setValue("crossdevice/enabled", CrossDevice.isEnabled); }

    int loadEarDetectionSettings() { return m_settings->value("earDetection/setting", MediaController::EarDetectionBehavior::PauseWhenOneRemoved).toInt(); }
    void saveEarDetectionSettings() { m_settings->setValue("earDetection/setting", mediaController->getEarDetectionBehavior()); }

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

    void sendHandshake() {
        LOG_INFO("Connected to device, sending initial packets");
        writePacketToSocket(AirPodsPackets::Connection::HANDSHAKE, "Handshake packet written: ");
    }

    void bluezDeviceConnected(const QString &address, const QString &name) 
    {
        QBluetoothDeviceInfo device(QBluetoothAddress(address), name, 0);
        connectToDevice(device);
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

        // Clear the device name and model
        m_deviceName.clear();
        connectedDeviceMacAddress.clear();
        mediaController->setConnectedDeviceMacAddress(connectedDeviceMacAddress);
        m_model = AirPodsModel::Unknown;
        emit deviceNameChanged(m_deviceName);
        emit modelChanged();

        // Reset battery status
        m_battery->reset();
        m_batteryStatus.clear();
        emit batteryStatusChanged(m_batteryStatus);

        // Reset ear detection
        m_earDetectionStatus.clear();
        m_primaryInEar = false;
        m_secoundaryInEar = false;
        emit earDetectionStatusChanged(m_earDetectionStatus);
        emit primaryChanged();

        // Reset noise control mode
        m_noiseControlMode = NoiseControlMode::Off;
        emit noiseControlModeChanged(m_noiseControlMode);

        mediaController->pause(); // Since the device is deconnected, we don't know if it was the active output device. Pause to be safe
        emit airPodsStatusChanged();

        // Show system notification
        trayManager->showNotification(
            tr("AirPods Disconnected"),
            tr("Your AirPods have been disconnected"));
    }

    void bluezDeviceDisconnected(const QString &address, const QString &name)
    {
        if (address == connectedDeviceMacAddress.replace("_", ":"))
        {
            onDeviceDisconnected(QBluetoothAddress(address));        }
        else {
            LOG_WARN("Disconnected device does not match connected device: " << address << " != " << connectedDeviceMacAddress);
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

        m_model = parseModelNumber(modelNumber);

        emit modelChanged();
        m_model = parseModelNumber(modelNumber);

        emit modelChanged();
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

    QString getEarStatus(char value)
    {
        return (value == 0x00) ? "In Ear" : (value == 0x01) ? "Out of Ear"
                                                            : "In case";
    }

    void connectToDevice(const QBluetoothDeviceInfo &device)
    {
        if (socket && socket->isOpen() && socket->peerAddress() == device.address())
        {
            LOG_INFO("Already connected to the device: " << device.name());
            return;
        }

        LOG_INFO("Connecting to device: " << device.name());

        // Clean up any existing socket
        if (socket)
        {
            socket->close();
            socket->deleteLater();
            socket = nullptr;
        }

        QBluetoothSocket *localSocket = new QBluetoothSocket(QBluetoothServiceInfo::L2capProtocol);
        socket = localSocket;

        // Connection handler
        auto handleConnection = [this, localSocket]()
        {
            connect(localSocket, &QBluetoothSocket::readyRead, this, [this, localSocket]()
                    {
            QByteArray data = localSocket->readAll();
            QMetaObject::invokeMethod(this, "parseData", Qt::QueuedConnection, Q_ARG(QByteArray, data));
            QMetaObject::invokeMethod(this, "relayPacketToPhone", Qt::QueuedConnection, Q_ARG(QByteArray, data)); });
            sendHandshake();
        };

        // Error handler with retry
        auto handleError = [this, device, localSocket](QBluetoothSocket::SocketError error)
        {
            LOG_ERROR("Socket error: " << error << ", " << localSocket->errorString());

            static int retryCount = 0;
            if (retryCount < 3)
            {
                retryCount++;
                LOG_INFO("Retrying connection (attempt " << retryCount << ")");
                QTimer::singleShot(1500, this, [this, device]()
                                   { connectToDevice(device); });
            }
            else 
            {
                LOG_ERROR("Failed to connect after 3 attempts");
                retryCount = 0;
            }
        };

        connect(localSocket, &QBluetoothSocket::connected, this, handleConnection);
        connect(localSocket, QOverload<QBluetoothSocket::SocketError>::of(&QBluetoothSocket::errorOccurred),
                this, handleError);

        localSocket->connectToService(device.address(), QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"));
        connectedDeviceMacAddress = device.address().toString().replace(":", "_");
        notifyAndroidDevice();
    }

    void parseData(const QByteArray &data)
    {
        LOG_DEBUG("Received: " << data.toHex());

        if (data.startsWith(AirPodsPackets::Parse::HANDSHAKE_ACK))
        {
            writePacketToSocket(AirPodsPackets::Connection::SET_SPECIFIC_FEATURES, "Set specific features packet written: ");
        }
        else if (data.startsWith(AirPodsPackets::Parse::FEATURES_ACK))
        {
            writePacketToSocket(AirPodsPackets::Connection::REQUEST_NOTIFICATIONS, "Request notifications packet written: ");
            
            QTimer::singleShot(2000, this, [this]() {
                if (m_batteryStatus.isEmpty()) {
                    writePacketToSocket(AirPodsPackets::Connection::REQUEST_NOTIFICATIONS, "Request notifications packet written: ");
                }
            });
        }
        // Magic Cloud Keys Response
        else if (data.startsWith(AirPodsPackets::MagicPairing::MAGIC_CLOUD_KEYS_HEADER))
        {
            auto keys = AirPodsPackets::MagicPairing::parseMagicCloudKeysPacket(data);
            LOG_INFO("Received Magic Cloud Keys:");
            LOG_INFO("MagicAccIRK: " << keys.magicAccIRK.toHex());
            LOG_INFO("MagicAccEncKey: " << keys.magicAccEncKey.toHex());

            // Store the keys for later use if needed
            m_magicAccIRK = keys.magicAccIRK;
            m_magicAccEncKey = keys.magicAccEncKey;
        }
        // Get CA state
        else if (data.startsWith(AirPodsPackets::ConversationalAwareness::HEADER)) {
            auto result = AirPodsPackets::ConversationalAwareness::parseCAState(data);
            if (result.has_value()) {
                m_conversationalAwareness = result.value();
                LOG_INFO("Conversational awareness state received: " << m_conversationalAwareness);
                emit conversationalAwarenessChanged(m_conversationalAwareness);
            } else {
                LOG_ERROR("Failed to parse conversational awareness state");
            }
        }
        // Noise Control Mode
        else if (data.size() == 11 && data.startsWith(AirPodsPackets::NoiseControl::HEADER))
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
            m_primaryInEar = data[6] == 0x00;
            m_secoundaryInEar = data[7] == 0x00;
            m_earDetectionStatus = QString("Primary: %1, Secondary: %2")
                                       .arg(getEarStatus(primary), getEarStatus(secondary));
            LOG_INFO("Ear detection status: " << m_earDetectionStatus);
            emit earDetectionStatusChanged(m_earDetectionStatus);
            emit primaryChanged();
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
            initiateMagicPairing();
            mediaController->setConnectedDeviceMacAddress(connectedDeviceMacAddress);
            if (isLeftPodInEar() || isRightPodInEar()) // AirPods get added as output device only after this
            {
                mediaController->activateA2dpProfile();
            }
            emit airPodsStatusChanged();
        }
        else
        {
            LOG_DEBUG("Unrecognized packet format: " << data.toHex());
        }
    }

    void connectToPhone() {
        if (!CrossDevice.isEnabled) {
            return;
        }

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
        if (!CrossDevice.isEnabled) {
            return;
        }
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
        if (!CrossDevice.isEnabled) return;

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
    void modelChanged();
    void primaryChanged();
    void airPodsStatusChanged();
    void earDetectionBehaviorChanged(int behavior);
    void crossDeviceEnabledChanged(bool enabled);

    private : QSystemTrayIcon *trayIcon;
    QMenu *trayMenu;
    QBluetoothSocket *socket = nullptr;
    QBluetoothSocket *phoneSocket = nullptr;
    QString connectedDeviceMacAddress;
    QByteArray lastBatteryStatus;
    QByteArray lastEarDetectionStatus;
    MediaController* mediaController;
    TrayIconManager *trayManager;
    BluetoothMonitor *monitor;
    QSettings *m_settings;

    QString m_batteryStatus;
    QString m_earDetectionStatus;
    NoiseControlMode m_noiseControlMode = NoiseControlMode::Off;
    bool m_conversationalAwareness = false;
    int m_adaptiveNoiseLevel = 50;
    QString m_deviceName;
    Battery *m_battery;
    AirPodsModel m_model = AirPodsModel::Unknown;
    bool m_primaryInEar = false;
    bool m_secoundaryInEar = false;
    QByteArray m_magicAccIRK;
    QByteArray m_magicAccEncKey;
};

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
    app.setQuitOnLastWindowClosed(false);

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
