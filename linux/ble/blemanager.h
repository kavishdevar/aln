#ifndef BLEMANAGER_H
#define BLEMANAGER_H

#include <QObject>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QMap>
#include <QString>
#include <QDateTime>

class QTimer;

class DeviceInfo
{
public:
    QString name;
    QString address;
    int leftPodBattery = -1; // -1 indicates not available
    int rightPodBattery = -1;
    int caseBattery = -1;
    bool leftCharging = false;
    bool rightCharging = false;
    bool caseCharging = false;
    quint16 deviceModel = 0;
    quint8 lidOpenCounter = 0;
    quint8 deviceColor = 0;
    quint8 status = 0;
    QByteArray rawData;

    // Additional status flags from Kotlin version
    bool isLeftPodInEar = false;
    bool isRightPodInEar = false;
    bool isLeftPodMicrophone = false;
    bool isRightPodMicrophone = false;
    bool isThisPodInTheCase = false;
    bool isOnePodInCase = false;
    bool areBothPodsInCase = false;

    // Lid state enumeration
    enum class LidState
    {
        OPEN,
        CLOSED,
        NOT_IN_CASE,
        UNKNOWN
    };
    LidState lidState = LidState::UNKNOWN;

    QDateTime lastSeen; // Timestamp of last detection
};

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