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

Q_LOGGING_CATEGORY(airpodsApp, "airpodsApp")

#define LOG_INFO(msg) qCInfo(airpodsApp) << "\033[32m" << msg << "\033[0m"
#define LOG_WARN(msg) qCWarning(airpodsApp) << "\033[33m" << msg << "\033[0m"
#define LOG_ERROR(msg) qCCritical(airpodsApp) << "\033[31m" << msg << "\033[0m"
#define LOG_DEBUG(msg) qCDebug(airpodsApp) << "\033[34m" << msg << "\033[0m"

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

        trayIcon->setContextMenu(trayMenu);
        trayIcon->show();

        connect(trayIcon, &QSystemTrayIcon::activated, this, &AirPodsTrayApp::onTrayIconActivated);

        discoveryAgent = new QBluetoothDeviceDiscoveryAgent();
        connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::deviceDiscovered, this, &AirPodsTrayApp::onDeviceDiscovered);
        connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::finished, this, &AirPodsTrayApp::onDiscoveryFinished);
        discoveryAgent->start();
        LOG_INFO("AirPodsTrayApp initialized and started device discovery");

        // Check for already connected devices
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
            case 0: // Off
                packet = QByteArray::fromHex("0400040009000D01000000");
                break;
            case 1: // Noise Cancellation
                packet = QByteArray::fromHex("0400040009000D02000000");
                break;
            case 2: // Transparency
                packet = QByteArray::fromHex("0400040009000D03000000");
                break;
            case 3: // Adaptive
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
        if (mode >= 0 && mode < actions.size()) {
            actions[mode]->setChecked(true);
        }
    }

    void updateBatteryTooltip(const QString &status) {
        trayIcon->setToolTip(status);
    }

    void updateTrayIcon(const QString &status) {
        QStringList parts = status.split(", ");
        int leftLevel = parts[0].split(": ")[1].replace("%", "").toInt();
        int rightLevel = parts[1].split(": ")[1].replace("%", "").toInt();
        int minLevel = qMin(leftLevel, rightLevel);

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

private slots:
    void onTrayIconActivated(QSystemTrayIcon::ActivationReason reason) {
        if (reason == QSystemTrayIcon::Trigger) {
            LOG_INFO("Tray icon activated");
            // Show settings window
            QQuickWindow *window = qobject_cast<QQuickWindow *>(QGuiApplication::topLevelWindows().first());
            if (window) {
                window->show();
                window->raise();
                window->requestActivate();
            }
        }
    }

    void onDeviceDiscovered(const QBluetoothDeviceInfo &device) {
        LOG_INFO("Device discovered: " << device.name() << " (" << device.address().toString() << ")");
        if (device.serviceUuids().contains(QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
            LOG_DEBUG("Found AirPods device" + device.name());
            connectToDevice(device);
        }
    }

    void onDiscoveryFinished() {
        LOG_INFO("Device discovery finished");
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
    }

    void connectToDevice(const QBluetoothDeviceInfo &device) {
        if (socket && socket->isOpen() && socket->peerAddress() == device.address()) {
            LOG_INFO("Already connected to the device: " << device.name());
            return;
        }

        LOG_INFO("Connecting to device: " << device.name() << " (" << device.address().toString() << ")");
        QBluetoothSocket *localSocket = new QBluetoothSocket(QBluetoothServiceInfo::L2capProtocol);
        connect(localSocket, &QBluetoothSocket::connected, this, [this, localSocket]() {
            LOG_INFO("Connected to device, sending initial packets");
            discoveryAgent->stop(); // Stop discovering once connected

            QByteArray handshakePacket = QByteArray::fromHex("00000400010002000000000000000000");
            QByteArray setSpecificFeaturesPacket = QByteArray::fromHex("040004004d00ff00000000000000");
            QByteArray requestNotificationsPacket = QByteArray::fromHex("040004000f00ffffffffff");

            qint64 bytesWritten = localSocket->write(handshakePacket);
            LOG_DEBUG("Handshake packet written: " << handshakePacket.toHex() << ", bytes written: " << bytesWritten);

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
                parseData(data);
            });
        });

        connect(localSocket, QOverload<QBluetoothSocket::SocketError>::of(&QBluetoothSocket::errorOccurred), this, [this, localSocket](QBluetoothSocket::SocketError error) {
            LOG_ERROR("Socket error: " << error << ", " << localSocket->errorString());
        });

        localSocket->connectToService(device.address(), QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"));
        socket = localSocket;
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
        }
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
};

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);

    QQmlApplicationEngine engine;
    engine.loadFromModule("linux", "Main");

    AirPodsTrayApp trayApp;

    engine.rootContext()->setContextProperty("airPodsTrayApp", &trayApp);

    QObject::connect(&trayApp, &AirPodsTrayApp::noiseControlModeChanged, [&engine](int mode) {
        LOG_DEBUG("Received noiseControlModeChanged signal with mode: " << mode);
        QObject *rootObject = engine.rootObjects().first();
    
        if (rootObject) {
            LOG_DEBUG("Root object found");
            QObject *noiseControlMode = rootObject->findChild<QObject*>("noiseControlMode");
            if (noiseControlMode) {
                LOG_DEBUG("noiseControlMode object found");
                if (mode >= 0 && mode <= 3) {
                    QMetaObject::invokeMethod(noiseControlMode, "setCurrentIndex", Q_ARG(int, mode));
                } else {
                    LOG_ERROR("Invalid mode value: " << mode);
                }
            } else {
                LOG_ERROR("noiseControlMode object not found");
            }
        } else {
            LOG_ERROR("Root object not found");
        }
    });

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

    return app.exec();
}

#include "main.moc"
