#include <QByteArray>
#include <QMap>
#include <QString>

#include "airpods_packets.h"

class Battery
{
public:
    // Enum for AirPods components
    enum class Component
    {
        Right = 0x02,
        Left = 0x04,
        Case = 0x08,
    };

    enum class BatteryStatus
    {
        Unknown = 0,
        Charging = 0x01,
        Discharging = 0x02,
        Disconnected = 0x04,
    };

    // Struct to hold battery level and status
    struct BatteryState
    {
        int level = 0;  // Battery level (0-100), -1 if unknown
        BatteryStatus status = BatteryStatus::Unknown;
    };

    // Constructor: Initialize all components to unknown state
    Battery()
    {
        states[Component::Left] = {};
        states[Component::Right] = {};
        states[Component::Case] = {};
    }

    // Parse the battery status packet
    bool parsePacket(const QByteArray &packet)
    {
        if (!packet.startsWith(AirPodsPackets::Parse::BATTERY_STATUS))
        {
            return false;
        }

        // Get battery count (number of components)
        quint8 batteryCount = static_cast<quint8>(packet[6]);
        if (batteryCount > 3 || packet.size() != 7 + 5 * batteryCount)
        {
            return false; // Invalid count or size mismatch
        }

        // Copy current states; only included components will be updated
        QMap<Component, BatteryState> newStates = states;

        // Parse each component
        for (quint8 i = 0; i < batteryCount; ++i)
        {
            int offset = 7 + (5 * i);
            quint8 type = static_cast<quint8>(packet[offset]);

            // Verify spacer and end bytes
            if (static_cast<quint8>(packet[offset + 1]) != 0x01 ||
                static_cast<quint8>(packet[offset + 4]) != 0x01)
            {
                return false;
            }

            // Map byte value to component
            Component comp = static_cast<Component>(type);

            // Extract level and status
            int level = static_cast<quint8>(packet[offset + 2]);
            auto status = static_cast<BatteryStatus>(packet[offset + 3]);

            // Update the state for this component
            newStates[comp] = {level, status};
        }

        // Apply updates; unmentioned components retain old states
        states = newStates;
        return true;
    }

    // Get the raw state for a component
    BatteryState getState(Component comp) const
    {
        return states.value(comp, {});
    }

    // Get a formatted status string including charging state
    QString getComponentStatus(Component comp) const
    {
        BatteryState state = getState(comp);
        if (state.level == -1)
        {
            return "Unknown";
        }

        QString statusStr;
        switch (state.status)
        {
        case BatteryStatus::Unknown:
            statusStr = "Unknown";
            break;
        case BatteryStatus::Charging:
            statusStr = "Charging";
            break;
        case BatteryStatus::Discharging:
            statusStr = "Discharging";
            break;
        case BatteryStatus::Disconnected:
            statusStr = "Disconnected";
            break;
        default:
            statusStr = "Invalid";
            break;
        }
        return QString("%1% (%2)").arg(state.level).arg(statusStr);
    }

private:
    QMap<Component, BatteryState> states;
};