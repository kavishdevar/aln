#include "BluetoothHandler.h"
#include "PacketDefinitions.h"
#include <QLoggingCategory>

Q_LOGGING_CATEGORY(bluetoothHandler, "bluetoothHandler")

#define LOG_INFO(msg) qCInfo(bluetoothHandler) << "\033[32m" << msg << "\033[0m"
#define LOG_WARN(msg) qCWarning(bluetoothHandler) << "\033[33m" << msg << "\033[0m"
#define LOG_ERROR(msg) qCCritical(bluetoothHandler) << "\033[31m" << msg << "\033[0m"
#define LOG_DEBUG(msg) qCDebug(bluetoothHandler) << "\033[34m" << msg << "\033[0m"

BluetoothHandler::BluetoothHandler() {
    discoveryAgent = new QBluetoothDeviceDiscoveryAgent();
    discoveryAgent->setLowEnergyDiscoveryTimeout(5000);

    connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::deviceDiscovered, this, &BluetoothHandler::onDeviceDiscovered);
    connect(discoveryAgent, &QBluetoothDeviceDiscoveryAgent::finished, this, &BluetoothHandler::onDiscoveryFinished);
    discoveryAgent->start();
    LOG_INFO("BluetoothHandler initialized and started device discovery");
}

void BluetoothHandler::connectToDevice(const QBluetoothDeviceInfo &device) {
    if (socket && socket->isOpen() && socket->peerAddress() == device.address()) {
        LOG_INFO("Already connected to the device: " << device.name());
        return;
    }

    LOG_INFO("Connecting to device: " << device.name());
    QBluetoothSocket *localSocket = new QBluetoothSocket(QBluetoothServiceInfo::L2capProtocol);
    connect(localSocket, &QBluetoothSocket::connected, this, [this, localSocket]() {
        LOG_INFO("Connected to device, sending initial packets");
        discoveryAgent->stop();

        QByteArray handshakePacket = QByteArray::fromHex("00000400010002000000000000000000");
        QByteArray setSpecificFeaturesPacket = QByteArray::fromHex("040004004d00ff00000000000000");
        QByteArray requestNotificationsPacket = QByteArray::fromHex("040004000f00ffffffffff");

        qint64 bytesWritten = localSocket->write(handshakePacket);
        LOG_DEBUG("Handshake packet written: " << handshakePacket.toHex() << ", bytes written: " << bytesWritten);

        QByteArray airpodsConnectedPacket = QByteArray::fromHex("000400010001");
        phoneSocket->write(airpodsConnectedPacket);
        LOG_DEBUG("AIRPODS_CONNECTED packet written: " << airpodsConnectedPacket.toHex());

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
            relayPacketToPhone(data);
        });
    });

    connect(localSocket, QOverload<QBluetoothSocket::SocketError>::of(&QBluetoothSocket::errorOccurred), this, [this, localSocket](QBluetoothSocket::SocketError error) {
        LOG_ERROR("Socket error: " << error << ", " << localSocket->errorString());
    });

    localSocket->connectToService(device.address(), QBluetoothUuid("74ec2172-0bad-4d01-8f77-997b2be0722a"));
    socket = localSocket;
    connectedDeviceMacAddress = device.address().toString().replace(":", "_");
}

void BluetoothHandler::parseData(const QByteArray &data) {
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
    } else if (data.size() == 10 &&