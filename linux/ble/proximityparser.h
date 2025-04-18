#ifndef PROXIMITYPARSER_H
#define PROXIMITYPARSER_H

#include <QByteArray>
#include <QString>
#include <QDateTime>

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
        OPEN = 0x0,
        CLOSED = 0x1,
        UNKNOWN,
    };
    LidState lidState = LidState::UNKNOWN;

    // Connection state enumeration
    enum class ConnectionState : uint8_t
    {
        DISCONNECTED = 0x00,
        IDLE = 0x04,
        MUSIC = 0x05,
        CALL = 0x06,
        RINGING = 0x07,
        HANGING_UP = 0x09,
        UNKNOWN = 0xFF // Using 0xFF for representing null in the original
    };
    ConnectionState connectionState = ConnectionState::UNKNOWN;

    QDateTime lastSeen; // Timestamp of last detection
};

class ProximityParser
{
public:
    static bool parseAppleProximityData(const QByteArray &data, DeviceInfo &deviceInfo);
};

#endif // PROXIMITYPARSER_H