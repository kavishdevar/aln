#include <QApplication>
#include <QQmlApplicationEngine>
#include <QSystemTrayIcon>
#include <QMenu>
#include <QAction>
#include <QActionGroup>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QBluetoothLocalDevice>
#include <QBluetoothServer>
#include <QBluetoothSocket>
#include <QQuickWindow>
#include <QDebug>
#include <QInputDialog>
#include <QQmlContext>
#include <QLoggingCategory>
#include <QTimer>
#include <QPainter>
#include <QPalette>
#include <QDBusInterface>
#include <QDBusReply>
#include <QDBusConnectionInterface>
#include <QProcess>
#include <QRegularExpression>
#include <QFile>
#include <QTextStream>
#include <QStandardPaths>
#include <QBluetoothServer>
#include <QBluetoothSocket>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QBluetoothLocalDevice>
#include <QBluetoothUuid>

Q_LOGGING_CATEGORY(airpodsApp, "airpodsApp")

#define LOG_INFO(msg) qCInfo(airpodsApp) << "\033[32m" << msg << "\033[0m"
#define LOG_WARN(msg) qCWarning(airpodsApp) << "\033[33m" << msg << "\033[0m"
#define LOG_ERROR(msg) qCCritical(airpodsApp) << "\033[31m" << msg << "\033[0m"
#define LOG_DEBUG(msg) qCDebug(airpodsApp) << "\033[34m" << msg << "\033[0m"

#define PHONE_MAC_ADDRESS "22:22:F5:BB:1C:A0"

#define MANUFACTURER_ID 0x1234
#define MANUFACTURER_DATA "ALN_AirPods"

class AirPodsTrayApp : public QObject {
    Q_OBJECT

public:
    AirPodsTrayApp() {
        LOG_INFO("Initializing AirPodsTrayApp");
        trayIcon = new QSystemTrayIcon(QIcon(":/icons/airpods.png"));
        trayMenu = new QMenu();

        QAction *caToggleAction = new QAction("Toggle Conversational Awareness", trayMenu);
        trayMenu->addAction(caToggleAction);

        QAction *offAction = new QAction("Off", trayMenu);
        QAction *transparencyAction = new QAction("Transparency", trayMenu);
        QAction *adaptiveAction = new QAction("Adaptive", trayMenu);
        QAction *noiseCancellationAction = new QAction("Noise Cancellation", trayMenu);

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

        connect(offAction, &QAction::triggered, this, [this]() { setNoiseControlMode(0); });
        connect(transparencyAction, &QAction::triggered, this, [this]() { setNoiseControlMode(2); });
        connect(adaptiveAction, &QAction::triggered, this, [this]() { setNoiseControlMode(3); });
        connect(noiseCancellationAction, &QAction::triggered, this, [this]() { setNoiseControlMode(1); });

        connect(this, &AirPodsTrayApp::noiseControlModeChanged, this, &AirPodsTrayApp::updateNoiseControlMenu);
        connect(this, &AirPodsTrayApp::batteryStatusChanged, this, &AirPodsTrayApp::updateBatteryTooltip);
        connect(this, &AirPodsTrayApp::batteryStatusChanged, this, &AirPodsTrayApp::updateTrayIcon);
        connect(this, &AirPodsTrayApp::earDetectionStatusChanged, this, &AirPodsTrayApp::handleEarDetection);

        trayIcon->setContextMenu(trayMenu);
        trayIcon->show();

        connect(trayIcon, &QSystemTrayIcon::activated, this, &AirPodsTrayApp::onTrayIconActivated);

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
        initializeMprisInterface();
        connect(phoneSocket, &QBluetoothSocket::readyRead, this, &AirPodsTrayApp::onPhoneDataReceived);

        QDBusInterface iface("org.bluez", "/org/bluez", "org.bluez.Adapter1");
        QDBusReply<QVariant> reply = iface.call("GetServiceRecords", QString::fromUtf8("74ec2172-0bad-4d01-8f77-997b2be0722a"));
        if (reply.isValid()) {
            LOG_INFO("Service record found, proceeding with connection");
        } else {
            LOG_WARN("Service record not found, waiting for BLE broadcast");
        }
    }

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

    void setNoiseControlMode(int mode) {
        LOG_INFO("Setting noise control mode to: " << mode);
        QByteArray packet;
        switch (mode) {
            case 0:
                packet = QByteArray::fromHex("0400040009000D01000000");
                break;
            case 1:
                packet = QByteArray::fromHex("0400040009000D02000000");
                break;
            case 2:
                packet = QByteArray::fromHex("0400040009000D03000000");
                break;
            case 3:
                packet = QByteArray::fromHex("0400040009000D04000000");
                break;
        }
        if (socket && socket->isOpen()) {
            socket->write(packet);
            LOG_DEBUG("Noise control mode packet written: " << packet.toHex());
        } else {
            LOG_ERROR("Socket is not open, cannot write noise control mode packet");
        }
    }

    void setConversationalAwareness(bool enabled) {
        LOG_INFO("Setting conversational awareness to: " << (enabled ? "enabled" : "disabled"));
        QByteArray packet = enabled ? QByteArray::fromHex("0400040009002801000000") : QByteArray::fromHex("0400040009002802000000");
        if (socket && socket->isOpen()) {
            socket->write(packet);
            LOG_DEBUG("Conversational awareness packet written: " << packet.toHex());
        } else {
            LOG_ERROR("Socket is not open, cannot write conversational awareness packet");
        }
    }

    void updateNoiseControlMenu(int mode) {
        QList<QAction *> actions = trayMenu->actions();
        for (QAction *action : actions) {
            action->setChecked(false);
        }
        switch (mode) {
            case 0:
                actions[0]->setChecked(true);
                break;
            case 1:
                actions[3]->setChecked(true);
                break;
            case 2:
                actions[1]->setChecked(true);
                break;
            case 3:
                actions[2]->setChecked(true);
                break;
        }
    }

    void updateBatteryTooltip(const QString &status) {
        trayIcon->setToolTip(status);
    }

    void updateTrayIcon(const QString &status) {
        QStringList parts = status.split(", ");
        int leftLevel = parts[0].split(": ")[1].replace("%", "").toInt();
        int rightLevel = parts[1].split(": ")[1].replace("%", "").toInt();
        
        int minLevel;
        if (leftLevel == 0) {
            minLevel = rightLevel;
        } else if (rightLevel == 0) {
            minLevel = leftLevel;
        } else {
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

    void handleEarDetection(const QString &status) {
        static bool wasPausedByApp = false;

        QStringList parts = status.split(", ");
        bool primaryInEar = parts[0].contains("In Ear");
        bool secondaryInEar = parts[1].contains("In Ear");

        if (primaryInEar && secondaryInEar) {
            if (wasPausedByApp && isActiveOutputDeviceAirPods()) {
                QProcess::execute("playerctl", QStringList() << "play");
                LOG_INFO("Resumed playback via Playerctl");
                wasPausedByApp = false;
            }
            LOG_INFO("Both AirPods are in ear");
            activateA2dpProfile();
        } else {
            LOG_INFO("At least one AirPod is out of ear");
            if (isActiveOutputDeviceAirPods()) {
                QProcess process;
                process.start("playerctl", QStringList() << "status");
                process.waitForFinished();
                QString playbackStatus = process.readAllStandardOutput().trimmed();
                LOG_DEBUG("Playback status: " << playbackStatus);
                if (playbackStatus == "Playing") {
                    QProcess::execute("playerctl", QStringList() << "pause");
                    LOG_INFO("Paused playback via Playerctl");
                    wasPausedByApp = true;
                }
            }
            if (!primaryInEar && !secondaryInEar) {
                removeAudioOutputDevice();
            }
        }
    }

    void activateA2dpProfile() {
        LOG_INFO("Activating A2DP profile for AirPods");
        QProcess::execute("pactl", QStringList() << "set-card-profile" << "bluez_card." + connectedDeviceMacAddress << "a2dp-sink");
    }

    void removeAudioOutputDevice() {
        LOG_INFO("Removing AirPods as audio output device");
        QProcess::execute("pactl", QStringList() << "set-card-profile" << "bluez_card." + connectedDeviceMacAddress << "off");
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

    void onDeviceDisconnected(const QBluetoothAddress &address) {
        LOG_INFO("Device disconnected: " << address.toString());
        if (socket) {
            LOG_WARN("Socket is still open, closing it");
            socket->close();
            socket = nullptr;
        }
        if (phoneSocket && phoneSocket->isOpen()) {
            QByteArray airpodsDisconnectedPacket = QByteArray::fromHex("00010000");
            phoneSocket->write(airpodsDisconnectedPacket);
            LOG_DEBUG("AIRPODS_DISCONNECTED packet written: " << airpodsDisconnectedPacket.toHex());
        }
    }

    void connectToDevice(const QBluetoothDeviceInfo &device) {
        if (socket && socket->isOpen() && socket->peerAddress() == device.address()) {
            LOG_INFO("Already connected to the device: " << device.name());
            return;
        }

        LOG_INFO("Connecting to device: " << device.name());
        QBluetoothSocket *localSocket = new QBluetoothSocket(QBluetoothServiceInfo::RfcommProtocol);
        connect(localSocket, &QBluetoothSocket::connected, this, [this, localSocket]() {
            LOG_INFO("Connected to device, sending initial packets");
            discoveryAgent->stop();

            QByteArray handshakePacket = QByteArray::fromHex("00000400010002000000000000000000");
            QByteArray setSpecificFeaturesPacket = QByteArray::fromHex("040004004d00ff00000000000000");
            QByteArray requestNotificationsPacket = QByteArray::fromHex("040004000f00ffffffffff");

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
                LOG_DEBUG("Data received: " << data.toHex());
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
    }

    void parseData(const QByteArray &data) {
        LOG_DEBUG("Parsing data: " << data.toHex() << "Size: " << data.size());
        if (data.size() == 11 && data.startsWith(QByteArray::fromHex("0400040009000D"))) {
            int mode = data[7] - 1;
            LOG_INFO("Noise control mode: " << mode);
            if (mode >= 0 && mode <= 3) {
                emit noiseControlModeChanged(mode);
            } else {
                LOG_ERROR("Invalid noise control mode value received: " << mode);
            }
        } else if (data.size() == 8 && data.startsWith(QByteArray::fromHex("040004000600"))) {
            bool primaryInEar = data[6] == 0x00;
            bool secondaryInEar = data[7] == 0x00;
            QString earDetectionStatus = QString("Primary: %1, Secondary: %2")
                .arg(primaryInEar ? "In Ear" : "Out of Ear")
                .arg(secondaryInEar ? "In Ear" : "Out of Ear");
            LOG_INFO("Ear detection status: " << earDetectionStatus);
            emit earDetectionStatusChanged(earDetectionStatus);
        } else if (data.size() == 22 && data.startsWith(QByteArray::fromHex("040004000400"))) {
            int leftLevel = data[9];
            int rightLevel = data[14];
            int caseLevel = data[19];
            QString batteryStatus = QString("Left: %1%, Right: %2%, Case: %3%")
                .arg(leftLevel)
                .arg(rightLevel)
                .arg(caseLevel);
            LOG_INFO("Battery status: " << batteryStatus);
            emit batteryStatusChanged(batteryStatus);
                                                                    
        } else if (data.size() == 10 && data.startsWith(QByteArray::fromHex("040004004B00020001"))) {
            LOG_INFO("Received conversational awareness data");
            handleConversationalAwareness(data);
        }
    }

    void handleConversationalAwareness(const QByteArray &data) {
        LOG_DEBUG("Handling conversational awareness data: " << data.toHex());
        static int initialVolume = -1;
        bool lowered = data[9] == 0x01;
        LOG_INFO("Conversational awareness: " << (lowered ? "enabled" : "disabled"));

        if (lowered) {
            if (initialVolume == -1 && isActiveOutputDeviceAirPods()) {
                QProcess process;
                process.start("pactl", QStringList() << "get-sink-volume" << "@DEFAULT_SINK@");
                process.waitForFinished();
                QString output = process.readAllStandardOutput();
                QRegularExpression re("front-left: \\d+ /\\s*(\\d+)%");
                QRegularExpressionMatch match = re.match(output);
                if (match.hasMatch()) {
                    LOG_DEBUG("Matched: " << match.captured(1));
                    initialVolume = match.captured(1).toInt();
                } else {
                    LOG_ERROR("Failed to parse initial volume from output: " << output);
                    return;
                }
            }
            QProcess::execute("pactl", QStringList() << "set-sink-volume" << "@DEFAULT_SINK@" << QString::number(initialVolume * 0.20) + "%");
            LOG_INFO("Volume lowered to 0.20 of initial which is " << initialVolume * 0.20 << "%");
        } else {
            if (initialVolume != -1 && isActiveOutputDeviceAirPods()) {
                QProcess::execute("pactl", QStringList() << "set-sink-volume" << "@DEFAULT_SINK@" << QString::number(initialVolume) + "%");
                LOG_INFO("Volume restored to " << initialVolume << "%");
                initialVolume = -1;
            }
        }
    }

    bool isActiveOutputDeviceAirPods() {
        QProcess process;
        process.start("pactl", QStringList() << "get-default-sink");
        process.waitForFinished();
        QString output = process.readAllStandardOutput().trimmed();
        LOG_DEBUG("Default sink: " << output);
        return (output.contains("bluez_card.") || output.contains("bluez_output.")) && output.contains(connectedDeviceMacAddress);
    }

    void initializeMprisInterface() {
        QStringList services = QDBusConnection::sessionBus().interface()->registeredServiceNames();
        QString mprisService;

        foreach (const QString &service, services) {
            if (service.startsWith("org.mpris.MediaPlayer2.") && service != "org.mpris.MediaPlayer2") {
                mprisService = service;
                break;
            }
        }

        if (!mprisService.isEmpty()) {
            mprisInterface = new QDBusInterface(mprisService,
                                                "/org/mpris/MediaPlayer2",
                                                "org.mpris.MediaPlayer2.Player",
                                                QDBusConnection::sessionBus(),
                                                this);
            if (!mprisInterface->isValid()) {
                LOG_ERROR("Failed to initialize MPRIS interface for service: " << mprisService);
            } else {
                LOG_INFO("Connected to MPRIS service: " << mprisService);
            }
        } else {
            LOG_WARN("No active MPRIS media players found");
        }
        connectToPhone();
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
        });

        connect(phoneSocket, QOverload<QBluetoothSocket::SocketError>::of(&QBluetoothSocket::errorOccurred), this, [this](QBluetoothSocket::SocketError error) {
            LOG_ERROR("Phone socket error: " << error << ", " << phoneSocket->errorString());
        });

        phoneSocket->connectToService(phoneAddress, QBluetoothUuid("1abbb9a4-10e4-4000-a75c-8953c5471342"));
    }

    void relayPacketToPhone(const QByteArray &packet) {
        if (phoneSocket && phoneSocket->isOpen()) {
            QByteArray header = QByteArray::fromHex("00040001");
            phoneSocket->write(header + packet);
            LOG_DEBUG("Relayed packet to phone with header: " << (header + packet).toHex());
        } else {
            connectToPhone();
            LOG_WARN("Phone socket is not open, cannot relay packet");
        }
    }

    void handlePhonePacket(const QByteArray &packet) {
        if (packet.startsWith(QByteArray::fromHex("00040001"))) {
            QByteArray airpodsPacket = packet.mid(4);
            if (socket && socket->isOpen()) {
                socket->write(airpodsPacket);
                LOG_DEBUG("Relayed packet to AirPods: " << airpodsPacket.toHex());
            } else {
                LOG_ERROR("Socket is not open, cannot relay packet to AirPods");
            }
        } else if (packet.startsWith(QByteArray::fromHex("00010001"))) {
            LOG_INFO("AirPods connected");
        } else if (packet.startsWith(QByteArray::fromHex("00010000"))) {
            LOG_INFO("AirPods disconnected");
        } else if (packet.startsWith(QByteArray::fromHex("00020003"))) {
            LOG_INFO("Connection status request received");
            QByteArray response = (socket && socket->isOpen()) ? QByteArray::fromHex("00010001") : QByteArray::fromHex("00010000");
            phoneSocket->write(response);
            LOG_DEBUG("Sent connection status response: " << response.toHex());
        } else if (packet.startsWith(QByteArray::fromHex("00020000"))) {
            LOG_INFO("Disconnect request received");
            if (socket && socket->isOpen()) {
                socket->close();
                LOG_INFO("Disconnected from AirPods");
                QProcess process;
                process.start("bluetoothctl", QStringList() << "disconnect" << connectedDeviceMacAddress.replace("_", ":"));
                process.waitForFinished();
                QString output = process.readAllStandardOutput().trimmed();
                LOG_INFO("Bluetoothctl output: " << output);
            }
        } else {
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

    public: void followMediaChanges() {
        QProcess *playerctlProcess = new QProcess(this);
        connect(playerctlProcess, &QProcess::readyReadStandardOutput, this, [this, playerctlProcess]() {
            QString output = playerctlProcess->readAllStandardOutput().trimmed();
            LOG_DEBUG("Playerctl output: " << output);
            if (output == "Playing" && isPhoneConnected()) {
                LOG_INFO("Media started playing, connecting to AirPods");
                connectToAirPods();
            }
        });
        playerctlProcess->start("playerctl", QStringList() << "metadata" << "--follow" << "status");
    }

    bool isPhoneConnected() {
        return phoneSocket && phoneSocket->isOpen();
    }

    void connectToAirPods() {
        QBluetoothLocalDevice localDevice;
        const QList<QBluetoothAddress> connectedDevices = localDevice.connectedDevices();
        for (const QBluetoothAddress &address : connectedDevices) {
            QBluetoothDeviceInfo device(address, "", 0);
            if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
                connectToDevice(device);
                return;
            }
        }
        LOG_WARN("AirPods not found among connected devices");
    }

signals:
    void noiseControlModeChanged(int mode);
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
};

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);

    QQmlApplicationEngine engine;
    AirPodsTrayApp trayApp;
    engine.rootContext()->setContextProperty("airPodsTrayApp", &trayApp);
    engine.loadFromModule("linux", "Main");

    QObject::connect(&trayApp, &AirPodsTrayApp::earDetectionStatusChanged, [&engine](const QString &status) {
        LOG_DEBUG("Received earDetectionStatusChanged signal with status: " << status);
        QObject *rootObject = engine.rootObjects().first();
        if (rootObject) {
            LOG_DEBUG("Root object found");
            QObject *earDetectionStatus = rootObject->findChild<QObject*>("earDetectionStatus");
            if (earDetectionStatus) {
                LOG_DEBUG("earDetectionStatus object found");
                earDetectionStatus->setProperty("text", "Ear Detection Status: " + status);
            } else {
                LOG_ERROR("earDetectionStatus object not found");
            }
        } else {
            LOG_ERROR("Root object not found");
        }
    });

    QObject::connect(&trayApp, &AirPodsTrayApp::batteryStatusChanged, [&engine](const QString &status) {
        LOG_DEBUG("Received batteryStatusChanged signal with status: " << status);
        QObject *rootObject = engine.rootObjects().first();
        if (rootObject) {
            LOG_DEBUG("Root object found");
            QObject *batteryStatus = rootObject->findChild<QObject*>("batteryStatus");
            if (batteryStatus) {
                LOG_DEBUG("batteryStatus object found");
                batteryStatus->setProperty("text", "Battery Status: " + status);
            } else {
                LOG_ERROR("batteryStatus object not found");
            }
        } else {
            LOG_ERROR("Root object not found");
        }
    });

    trayApp.followMediaChanges();

    return app.exec();
}


#include "main.moc"
