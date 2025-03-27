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
        quint8 level = 0; // Battery level (0-100), 0 if unknown
        BatteryStatus status = BatteryStatus::Unknown;
    };

    // Constructor: Initialize all components to unknown state
    Battery()
    {
        states[Component::Left] = {};
        states[Component::Right] = {};
        states[Component::Case] = {};
    }

    // Parse the battery status packet and detect primary/secondary pods
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

        QMap<Component, BatteryState> newStates = states;

        // Track pods to determine primary and secondary based on order
        QList<Component> podsInPacket;
        podsInPacket.reserve(2);

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

            Component comp = static_cast<Component>(type);
            auto level = static_cast<quint8>(packet[offset + 2]);
            auto status = static_cast<BatteryStatus>(packet[offset + 3]);

            newStates[comp] = {level, status};

            // If this is a pod (Left or Right), add it to the list
            if (comp == Component::Left || comp == Component::Right)
            {
                podsInPacket.append(comp);
            }
        }

        // Update states
        states = newStates;

        // Set primary and secondary pods based on order
        if (!podsInPacket.isEmpty())
        {
            primaryPod = podsInPacket[0]; // First pod is primary
        }
        if (podsInPacket.size() >= 2)
        {
            secondaryPod = podsInPacket[1]; // Second pod is secondary
        }

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
        if (state.level == 0)
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

    Component getPrimaryPod() const { return primaryPod; }
    Component getSecondaryPod() const { return secondaryPod; }

private:
    QMap<Component, BatteryState> states;
    Component primaryPod;
    Component secondaryPod;
};