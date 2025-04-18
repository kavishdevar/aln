// proximityparser.cpp
#include "proximityparser.h"
#include <QDebug>

bool ProximityParser::parseAppleProximityData(const QByteArray &data, DeviceInfo &deviceInfo)
{
    // Ensure data is long enough and starts with prefix 0x07 (indicates Proximity Pairing Message)
    if (data.size() < 10 || data[0] != 0x07)
    {
        return false;
    }

    // Set device type
    deviceInfo.deviceType = DeviceInfo::DeviceType::PROXIMITY;

    // data[1] is the length of the data, so we can skip it

    // Check if pairing mode is paired (0x01) or pairing (0x00)
    if (data[2] == 0x00)
    {
        return false; // Skip pairing mode devices (the values are differently structured)
    }

    // Parse device model (big-endian: high byte at data[3], low byte at data[4])
    deviceInfo.deviceModel = static_cast<quint16>(data[4]) | (static_cast<quint8>(data[3]) << 8);

    // Status byte for primary pod and other flags
    quint8 status = static_cast<quint8>(data[5]);
    deviceInfo.status = status;

    // Pods battery byte (upper nibble: one pod, lower nibble: other pod)
    quint8 podsBatteryByte = static_cast<quint8>(data[6]);

    // Flags and case battery byte (upper nibble: case battery, lower nibble: flags)
    quint8 flagsAndCaseBattery = static_cast<quint8>(data[7]);

    // Lid open counter and device color
    quint8 lidIndicator = static_cast<quint8>(data[8]);
    deviceInfo.deviceColor = static_cast<quint8>(data[9]);

    deviceInfo.connectionState = static_cast<DeviceInfo::ConnectionState>(data[10]);

    // Determine primary pod (bit 5 of status) and value flipping
    bool primaryLeft = (status & 0x20) != 0; // Bit 5: 1 = left primary, 0 = right primary
    bool areValuesFlipped = !primaryLeft;    // Flipped when right pod is primary

    // Parse battery levels
    int leftNibble = areValuesFlipped ? (podsBatteryByte >> 4) & 0x0F : podsBatteryByte & 0x0F;
    int rightNibble = areValuesFlipped ? podsBatteryByte & 0x0F : (podsBatteryByte >> 4) & 0x0F;
    deviceInfo.leftPodBattery = (leftNibble == 15) ? -1 : leftNibble * 10;
    deviceInfo.rightPodBattery = (rightNibble == 15) ? -1 : rightNibble * 10;
    int caseNibble = flagsAndCaseBattery & 0x0F; // Extracts lower nibble
    deviceInfo.caseBattery = (caseNibble == 15) ? -1 : caseNibble * 10;

    // Parse charging statuses from flags (upper 4 bits of data[7])
    quint8 flags = (flagsAndCaseBattery >> 4) & 0x0F;                                        // Extracts lower nibble
    deviceInfo.rightCharging = areValuesFlipped ? (flags & 0x01) != 0 : (flags & 0x02) != 0; // Depending on primary, bit 0 or 1
    deviceInfo.leftCharging = areValuesFlipped ? (flags & 0x02) != 0 : (flags & 0x01) != 0;  // Depending on primary, bit 1 or 0
    deviceInfo.caseCharging = (flags & 0x04) != 0;                                           // bit 2

    // Additional status flags from status byte (data[5])
    deviceInfo.isThisPodInTheCase = (status & 0x40) != 0; // Bit 6
    deviceInfo.isOnePodInCase = (status & 0x10) != 0;     // Bit 4
    deviceInfo.areBothPodsInCase = (status & 0x04) != 0;  // Bit 2

    // In-ear detection with XOR logic
    bool xorFactor = areValuesFlipped ^ deviceInfo.isThisPodInTheCase;
    deviceInfo.isLeftPodInEar = xorFactor ? (status & 0x08) != 0 : (status & 0x02) != 0;  // Bit 3 or 1
    deviceInfo.isRightPodInEar = xorFactor ? (status & 0x02) != 0 : (status & 0x08) != 0; // Bit 1 or 3

    // Microphone status
    deviceInfo.isLeftPodMicrophone = primaryLeft ^ deviceInfo.isThisPodInTheCase;
    deviceInfo.isRightPodMicrophone = !primaryLeft ^ deviceInfo.isThisPodInTheCase;

    deviceInfo.lidOpenCounter = lidIndicator & 0x07;                   // Extract bits 0-2 (count)
    quint8 lidState = static_cast<quint8>((lidIndicator >> 3) & 0x01); // Extract bit 3 (lid state)
    if (deviceInfo.isThisPodInTheCase)
    {
        deviceInfo.lidState = static_cast<DeviceInfo::LidState>(lidState);
    }

    return true;
}

bool ProximityParser::parseAppleFindMyData(const QByteArray &data, DeviceInfo &deviceInfo)
{
    // Minimum length for Find My message
    if (data.size() < 26 || data[0] != 0x12 || data[1] != 0x19)
    {
        return false;
    }

    // Set device type
    deviceInfo.deviceType = DeviceInfo::DeviceType::FIND_MY;

    // Parse status byte
    quint8 status = static_cast<quint8>(data[2]);
    deviceInfo.status = status;

    // Check maintained bit (bit 2)
    bool isMaintained = (status & 0x04) != 0;
    deviceInfo.isMaintained = isMaintained;

    // Parse battery level if maintained
    if (isMaintained)
    {
        int batteryLevel = (status >> 6) & 0x03;
        switch (batteryLevel)
        {
        case 0:
            deviceInfo.batteryLevel = DeviceInfo::BatteryLevel::CRITICALLY_LOW;
            break;
        case 1:
            deviceInfo.batteryLevel = DeviceInfo::BatteryLevel::LOW;
            break;
        case 2:
            deviceInfo.batteryLevel = DeviceInfo::BatteryLevel::MEDIUM;
            break;
        case 3:
            deviceInfo.batteryLevel = DeviceInfo::BatteryLevel::FULL;
            break;
        }
    }
    else
    {
        deviceInfo.batteryLevel = DeviceInfo::BatteryLevel::UNKNOWN;
    }

    // Extract public key (bytes 3-24)
    deviceInfo.publicKey = data.mid(3, 22);

    // Extract public key bits (byte 25)
    deviceInfo.publicKeyBits = static_cast<quint8>(data[25]);

    // Extract hint (byte 26)
    deviceInfo.hint = static_cast<quint8>(data[26]);

    return true;
}