// airpods_packets.h
#ifndef AIRPODS_PACKETS_H
#define AIRPODS_PACKETS_H

#include <QByteArray>

namespace AirPodsPackets
{
    // Noise Control Mode Packets
    namespace NoiseControl
    {
        static const QByteArray HEADER = QByteArray::fromHex("0400040009000D"); // Added for parsing
        static const QByteArray OFF = HEADER + QByteArray::fromHex("01000000");
        static const QByteArray NOISE_CANCELLATION = HEADER + QByteArray::fromHex("02000000");
        static const QByteArray TRANSPARENCY = HEADER + QByteArray::fromHex("03000000");
        static const QByteArray ADAPTIVE = HEADER + QByteArray::fromHex("04000000");
    }

    // Conversational Awareness Packets
    namespace ConversationalAwareness
    {
        static const QByteArray HEADER = QByteArray::fromHex("04000400090028");          // Added for parsing
        static const QByteArray ENABLED = HEADER + QByteArray::fromHex("01000000");
        static const QByteArray DISABLED = HEADER + QByteArray::fromHex("02000000");
        static const QByteArray DATA_HEADER = QByteArray::fromHex("040004004B00020001"); // For received data
    }

    // Connection Packets
    namespace Connection
    {
        static const QByteArray HANDSHAKE = QByteArray::fromHex("00000400010002000000000000000000");
        static const QByteArray SET_SPECIFIC_FEATURES = QByteArray::fromHex("040004004d00ff00000000000000");
        static const QByteArray REQUEST_NOTIFICATIONS = QByteArray::fromHex("040004000f00ffffffffff");
        static const QByteArray AIRPODS_DISCONNECTED = QByteArray::fromHex("00010000");
    }

    // Phone Communication Packets
    namespace Phone
    {
        static const QByteArray NOTIFICATION = QByteArray::fromHex("00040001");
        static const QByteArray CONNECTED = QByteArray::fromHex("00010001");
        static const QByteArray DISCONNECTED = QByteArray::fromHex("00010000");
        static const QByteArray STATUS_REQUEST = QByteArray::fromHex("00020003");
        static const QByteArray DISCONNECT_REQUEST = QByteArray::fromHex("00020000");
    }

    // Parsing Headers
    namespace Parse
    {
        static const QByteArray EAR_DETECTION = QByteArray::fromHex("040004000600");
        static const QByteArray BATTERY_STATUS = QByteArray::fromHex("040004000400");
    }
}

#endif // AIRPODS_PACKETS_H