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