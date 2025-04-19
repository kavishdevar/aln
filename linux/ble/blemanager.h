#ifndef BLEMANAGER_H
#define BLEMANAGER_H

#include "proximityparser.h"

#include <QObject>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QMap>
#include <QString>
#include <QDateTime>

class QTimer;

class BleManager : public QObject
{
    Q_OBJECT
public:
    explicit BleManager(QObject *parent = nullptr);
    ~BleManager();

    void startScan();
    void stopScan();
    const QMap<QString, DeviceInfo> &getDevices() const;

private slots:
    void onDeviceDiscovered(const QBluetoothDeviceInfo &info);
    void onScanFinished();
    void onErrorOccurred(QBluetoothDeviceDiscoveryAgent::Error error);
    void pruneOldDevices();

private:
    QBluetoothDeviceDiscoveryAgent *discoveryAgent;
    QMap<QString, DeviceInfo> devices;

    QTimer *pruneTimer;                         // Timer for periodic pruning
    static const int PRUNE_INTERVAL_MS = 5000;  // Check every 5 seconds
    static const int DEVICE_TIMEOUT_MS = 10000; // Remove after 10 seconds
};

#endif // BLEMANAGER_H