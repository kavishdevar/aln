#include "main.h"
#include "airpods_packets.h"
#include "logger.h"
#include "mediacontroller.h"

Q_LOGGING_CATEGORY(airpodsApp, "airpodsApp")

class AirPodsTrayApp : public QObject {
    Q_OBJECT

public:
    enum NoiseControlMode : quint8
    {
        Off = 0,
        NoiseCancellation = 1,
        Transparency = 2,
        Adaptive = 3,

        MinValue = Off,
        MaxValue = Adaptive,
    };
    Q_ENUM(NoiseControlMode)

    AirPodsTrayApp(bool debugMode) : debugMode(debugMode) {
        if (debugMode) {
            QLoggingCategory::setFilterRules("airpodsApp.debug=true");
        } else {
            QLoggingCategory::setFilterRules("airpodsApp.debug=false");
        }
        LOG_INFO("Initializing AirPodsTrayApp");
        trayIcon = new QSystemTrayIcon(QIcon(":/icons/airpods.png"));
        trayMenu = new QMenu();

        bool caState = loadConversationalAwarenessState();
        QAction *caToggleAction = new QAction("Toggle Conversational Awareness", trayMenu);
        caToggleAction->setCheckable(true);
        caToggleAction->setChecked(caState);
        connect(caToggleAction, &QAction::triggered, this, [this, caToggleAction]() {
            bool newState = !caToggleAction->isChecked();
            setConversationalAwareness(newState);
            saveConversationalAwarenessState(newState);
            caToggleAction->setChecked(newState);
        });
        trayMenu->addAction(caToggleAction);

        QAction *offAction = new QAction("Off", trayMenu);
        QAction *transparencyAction = new QAction("Transparency", trayMenu);
        QAction *adaptiveAction = new QAction("Adaptive", trayMenu);
        QAction *noiseCancellationAction = new QAction("Noise Cancellation", trayMenu);

        offAction->setData(NoiseControlMode::Off);
        transparencyAction->setData(NoiseControlMode::Transparency);
        adaptiveAction->setData(NoiseControlMode::Adaptive);
        noiseCancellationAction->setData(NoiseControlMode::NoiseCancellation);

        offAction->setCheckable(true);
        transparencyAction->setCheckable(true);
        adaptiveAction->setCheckable(true);
        noiseCancellationAction->setCheckable(true);

        trayMenu->addAction(offAction);
        trayMenu->addAction(transparencyAction);
        trayMenu->addAction(adaptiveAction);
        trayMenu->addAction(noiseCancellationAction);

        QActionGroup *noiseControlGroup = new QActionGroup(trayMenu);
        noiseControlGroup->addAction(offAction);
        noiseControlGroup->addAction(transparencyAction);
        noiseControlGroup->addAction(adaptiveAction);
        noiseControlGroup->addAction(noiseCancellationAction);

        connect(offAction, &QAction::triggered, this, [this]()
                { setNoiseControlMode(NoiseControlMode::Off); });
        connect(transparencyAction, &QAction::triggered, this, [this]()
                { setNoiseControlMode(NoiseControlMode::Transparency); });
        connect(adaptiveAction, &QAction::triggered, this, [this]()
                { setNoiseControlMode(NoiseControlMode::Adaptive); });
        connect(noiseCancellationAction, &QAction::triggered, this, [this]()
                { setNoiseControlMode(NoiseControlMode::NoiseCancellation); });

        connect(this, &AirPodsTrayApp::noiseControlModeChanged, this, &AirPodsTrayApp::updateNoiseControlMenu);
        connect(this, &AirPodsTrayApp::batteryStatusChanged, this, &AirPodsTrayApp::updateBatteryTooltip);
        connect(this, &AirPodsTrayApp::batteryStatusChanged, this, &AirPodsTrayApp::updateTrayIcon);

        trayIcon->setContextMenu(trayMenu);
        trayIcon->show();

        connect(trayIcon, &QSystemTrayIcon::activated, this, &AirPodsTrayApp::onTrayIconActivated);

        // Initialize MediaController and connect signals
        mediaController = new MediaController(this);
        connect(this, &AirPodsTrayApp::earDetectionStatusChanged, mediaController, &MediaController::handleEarDetection);
        connect(mediaController, &MediaController::mediaStateChanged, this, &AirPodsTrayApp::handleMediaStateChange);
        mediaController->initializeMprisInterface();
        mediaController->followMediaChanges();

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
            if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
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
                if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
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
        QByteArray packet;
        switch (mode)
        {
        case Off:
            packet = AirPodsPackets::NoiseControl::OFF;
            break;
        case NoiseCancellation:
            packet = AirPodsPackets::NoiseControl::NOISE_CANCELLATION;
            break;
        case Transparency:
            packet = AirPodsPackets::NoiseControl::TRANSPARENCY;
            break;
        case Adaptive:
            packet = AirPodsPackets::NoiseControl::ADAPTIVE;
            break;
        }
        if (socket && socket->isOpen())
        {
            socket->write(packet);
            LOG_DEBUG("Noise control mode packet written: " << packet.toHex());
        }
        else
        {
            LOG_ERROR("Socket is not open, cannot write noise control mode packet");
        }
    }

    void setConversationalAwareness(bool enabled)
    {
        LOG_INFO("Setting conversational awareness to: " << (enabled ? "enabled" : "disabled"));
        QByteArray packet = enabled ? AirPodsPackets::ConversationalAwareness::ENABLED
                                    : AirPodsPackets::ConversationalAwareness::DISABLED;
        if (socket && socket->isOpen())
        {
            socket->write(packet);
            LOG_DEBUG("Conversational awareness packet written: " << packet.toHex());
        }
        else
        {
            LOG_ERROR("Socket is not open, cannot write conversational awareness packet");
        }
    }

    void updateNoiseControlMenu(NoiseControlMode mode) {
        QList<QAction *> actions = trayMenu->actions();
        for (QAction *action : actions) {
            action->setChecked(action->data().toInt() == mode);
        }
    }

    void updateBatteryTooltip(const QString &status) {
        trayIcon->setToolTip("Battery Status: " + status);
    }

    void updateTrayIcon(const QString &status) {
        QStringList parts = status.split(", ");
        int leftLevel = parts[0].split(": ")[1].replace("%", "").toInt();
        int rightLevel = parts[1].split(": ")[1].replace("%", "").toInt();
        
        int minLevel;
        if (leftLevel == 0)
        {
            minLevel = rightLevel;
        }
        else if (rightLevel == 0)
        {
            minLevel = leftLevel;
        }
        else
        {
            minLevel = qMin(leftLevel, rightLevel);
        }


        QPixmap pixmap(32, 32);
        pixmap.fill(Qt::transparent);

        QPainter painter(&pixmap);
        QColor textColor = QApplication::palette().color(QPalette::WindowText);
        painter.setPen(textColor);
        painter.setFont(QFont("Arial", 12, QFont::Bold));
        painter.drawText(pixmap.rect(), Qt::AlignCenter, QString::number(minLevel) + "%");
        painter.end();

        trayIcon->setIcon(QIcon(pixmap));
    }

    bool loadConversationalAwarenessState() {
        QFile file(QStandardPaths::writableLocation(QStandardPaths::AppDataLocation) + "/ca_state.txt");
        if (file.open(QIODevice::ReadOnly)) {
            QTextStream in(&file);
            QString state = in.readLine();
            file.close();
            return state == "true";
        }
        return false;
    }

    void saveConversationalAwarenessState(bool state) {
        QFile file(QStandardPaths::writableLocation(QStandardPaths::AppDataLocation) + "/ca_state.txt");
        if (file.open(QIODevice::WriteOnly)) {
            QTextStream out(&file);
            out << (state ? "true" : "false");
            file.close();
        }
    }
    private slots:
    void onTrayIconActivated(QSystemTrayIcon::ActivationReason reason) {
        if (reason == QSystemTrayIcon::Trigger) {
            LOG_INFO("Tray icon activated");
            QQuickWindow *window = qobject_cast<QQuickWindow *>(
                QGuiApplication::topLevelWindows().constFirst());
            if (window) {
                window->show();
                window->raise();
                window->requestActivate();
            }
        }
    }

    void onDeviceDiscovered(const QBluetoothDeviceInfo &device) {
        QByteArray manufacturerData = device.manufacturerData(MANUFACTURER_ID);
        if (manufacturerData.startsWith(MANUFACTURER_DATA)) {
            LOG_INFO("Detected AirPods via BLE manufacturer data");
            connectToDevice(device.address().toString());
        }
        LOG_INFO("Device discovered: " << device.name() << " (" << device.address().toString() << ")");
        if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
            LOG_DEBUG("Found AirPods device: " + device.name());
            connectToDevice(device);
        }
    }

    void onDiscoveryFinished() {
        LOG_INFO("Device discovery finished");
        discoveryAgent->start();
        const QList<QBluetoothDeviceInfo> discoveredDevices = discoveryAgent->discoveredDevices();
        for (const QBluetoothDeviceInfo &device : discoveredDevices) {
            if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
                connectToDevice(device);
                return;
            }
        }
        LOG_WARN("No device with the specified UUID found");
    }

    void onDeviceConnected(const QBluetoothAddress &address) {
        LOG_INFO("Device connected: " << address.toString());
        QBluetoothDeviceInfo device(address, "", 0);
        if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
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
            if (rawMode >= NoiseControlMode::MinValue && rawMode <= NoiseControlMode::MaxValue)
            {
                NoiseControlMode mode = static_cast<NoiseControlMode>(rawMode);
                LOG_INFO("Noise control mode: " << rawMode);
                emit noiseControlModeChanged(mode);
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
            QString earDetectionStatus = QString("Primary: %1, Secondary: %2")
                                             .arg(getEarStatus(primary), getEarStatus(secondary));
            LOG_INFO("Ear detection status: " << earDetectionStatus);
            emit earDetectionStatusChanged(earDetectionStatus);
        }
        // Battery Status
        else if (data.size() == 22 && data.startsWith(AirPodsPackets::Parse::BATTERY_STATUS))
        {
            int leftLevel = data[9];
            int rightLevel = data[14];
            int caseLevel = data[19];
            QString batteryStatus = QString("Left: %1%, Right: %2%, Case: %3%")
                                        .arg(leftLevel)
                                        .arg(rightLevel)
                                        .arg(caseLevel);
            LOG_INFO("Battery status: " << batteryStatus);
            emit batteryStatusChanged(batteryStatus);
        }
        // Conversational Awareness Data
        else if (data.size() == 10 && data.startsWith(AirPodsPackets::ConversationalAwareness::DATA_HEADER))
        {
            LOG_INFO("Received conversational awareness data");
            mediaController->handleConversationalAwareness(data);
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
                if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
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
            if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
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
    AirPodsTrayApp trayApp(debugMode);
    engine.rootContext()->setContextProperty("airPodsTrayApp", &trayApp);
    engine.loadFromModule("linux", "Main");

    QObject::connect(&trayApp, &AirPodsTrayApp::noiseControlModeChanged, &engine, [&engine](int mode) {
        QObject *rootObject = engine.rootObjects().constFirst();

        if (rootObject) {
            QObject *noiseControlMode = rootObject->findChild<QObject*>("noiseControlMode");
            if (noiseControlMode) {
                if (mode >= 0 && mode <= 3) {
                    QMetaObject::invokeMethod(noiseControlMode, "setCurrentIndex", Q_ARG(int, mode));
                } else {
                    LOG_ERROR("Invalid mode value: " << mode);
                }
            }
        } else {
            LOG_ERROR("Root object not found");
        }
    });

    QObject::connect(&trayApp, &AirPodsTrayApp::earDetectionStatusChanged, [&engine](const QString &status) {
        QObject *rootObject = engine.rootObjects().first();
        if (rootObject) {
            QObject *earDetectionStatus = rootObject->findChild<QObject*>("earDetectionStatus");
            if (earDetectionStatus) {
                earDetectionStatus->setProperty("text", "Ear Detection Status: " + status);
            }
        } else {
            LOG_ERROR("Root object not found");
        }
    });

    QObject::connect(&trayApp, &AirPodsTrayApp::batteryStatusChanged, [&engine](const QString &status) {
        QObject *rootObject = engine.rootObjects().first();
        if (rootObject) {
            QObject *batteryStatus = rootObject->findChild<QObject*>("batteryStatus");
            if (batteryStatus) {
                batteryStatus->setProperty("text", "Battery Status: " + status);
            }
        } else {
            LOG_ERROR("Root object not found");
        }
    });

    return app.exec();
}

#include "main.moc"
