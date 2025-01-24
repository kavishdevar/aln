#pragma once

#include <QBluetoothDeviceInfo>
#include <QBluetoothSocket>
#include <QBluetoothDeviceDiscoveryAgent>

class BluetoothHandler : public QObject {
    Q_OBJECT

public:
    BluetoothHandler();
    void connectToDevice(const QBluetoothDeviceInfo &device);
    void parseData(const QByteArray &data);

signals:
    void noiseControlModeChanged(int mode);
    void earDetectionStatusChanged(const QString &status);
    void batteryStatusChanged(const QString &status);

private:
    QBluetoothSocket *socket = nullptr;
    QBluetoothDeviceDiscoveryAgent *discoveryAgent;
};
