// airpods_packets.h
#ifndef AIRPODS_PACKETS_H
#define AIRPODS_PACKETS_H

#include <QByteArray>
#include "enums.h"

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

        static const QByteArray getPacketForMode(AirpodsTrayApp::Enums::NoiseControlMode mode)
        {
            using NoiseControlMode = AirpodsTrayApp::Enums::NoiseControlMode;
            switch (mode)
            {
            case NoiseControlMode::Off:
                return OFF;
            case NoiseControlMode::NoiseCancellation:
                return NOISE_CANCELLATION;
            case NoiseControlMode::Transparency:
                return TRANSPARENCY;
            case NoiseControlMode::Adaptive:
                return ADAPTIVE;
            default:
                return QByteArray();
            }
        }
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

    // Adaptive Noise Packets
    namespace AdaptiveNoise
    {
        const QByteArray HEADER = QByteArray::fromHex("0400040009002E");

        inline QByteArray getPacket(int level)
        {
            return HEADER + static_cast<char>(level) + QByteArray::fromHex("000000");
        }
    }

    namespace Rename
    {
        static QByteArray getPacket(const QString &newName)
        {
            QByteArray nameBytes = newName.toUtf8();                   // Convert name to UTF-8
            quint8 size = static_cast<char>(nameBytes.size());         // Name length (1 byte)
            QByteArray packet = QByteArray::fromHex("040004001A0001"); // Header
            packet.append(size);                                       // Append size byte
            packet.append('\0');                                       // Append null byte
            packet.append(nameBytes);                                  // Append name bytes
            return packet;
        }
    }

    namespace MagicPairing {
        static const QByteArray REQUEST_MAGIC_CLOUD_KEYS = QByteArray::fromHex("0400040030000500");
        static const QByteArray MAGIC_CLOUD_KEYS_HEADER = QByteArray::fromHex("04000400310002");

        struct MagicCloudKeys {
            QByteArray magicAccIRK;      // 16 bytes
            QByteArray magicAccEncKey;    // 16 bytes
        };

        inline MagicCloudKeys parseMagicCloudKeysPacket(const QByteArray &data)
        {
            MagicCloudKeys keys;

            // Expected size: header (7 bytes) + (1 (tag) + 2 (length) + 1 (reserved) + 16 (value)) * 2 = 47 bytes.
            if (data.size() < 47)
            {
                return keys; // or handle error as needed
            }

            // Check header
            if (!data.startsWith(MAGIC_CLOUD_KEYS_HEADER))
            {
                return keys; // header mismatch
            }

            int index = MAGIC_CLOUD_KEYS_HEADER.size(); // Start after header (index 7)

            // --- TLV Block 1 (MagicAccIRK) ---
            // Tag should be 0x01
            if (static_cast<quint8>(data.at(index)) != 0x01)
            {
                return keys; // unexpected tag
            }
            index += 1;

            // Read length (2 bytes, big-endian)
            quint16 len1 = (static_cast<quint8>(data.at(index)) << 8) | static_cast<quint8>(data.at(index + 1));
            if (len1 != 16)
            {
                return keys; // invalid length
            }
            index += 2;

            // Skip reserved byte
            index += 1;

            // Extract MagicAccIRK (16 bytes)
            keys.magicAccIRK = data.mid(index, 16);
            index += 16;

            // --- TLV Block 2 (MagicAccEncKey) ---
            // Tag should be 0x04
            if (static_cast<quint8>(data.at(index)) != 0x04)
            {
                return keys; // unexpected tag
            }
            index += 1;

            // Read length (2 bytes, big-endian)
            quint16 len2 = (static_cast<quint8>(data.at(index)) << 8) | static_cast<quint8>(data.at(index + 1));
            if (len2 != 16)
            {
                return keys; // invalid length
            }
            index += 2;

            // Skip reserved byte
            index += 1;

            // Extract MagicAccEncKey (16 bytes)
            keys.magicAccEncKey = data.mid(index, 16);
            index += 16;

            return keys;
        }
    }

    // Parsing Headers
    namespace Parse
    {
        static const QByteArray EAR_DETECTION = QByteArray::fromHex("040004000600");
        static const QByteArray BATTERY_STATUS = QByteArray::fromHex("040004000400");
        static const QByteArray METADATA = QByteArray::fromHex("040004001d");
    }
}

#endif // AIRPODS_PACKETS_H