#ifndef BLEMANAGER_H
#define BLEMANAGER_H

#include <QObject>
#include <QBluetoothDeviceDiscoveryAgent>
#include <QMap>
#include <QString>

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

private:
    QBluetoothDeviceDiscoveryAgent *discoveryAgent;
    QMap<QString, DeviceInfo> devices;
};

#endif // BLEMANAGER_H