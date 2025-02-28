/*
 * AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
 *
 * Copyright (C) 2024 Kavish Devar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */


@file:Suppress("unused")

package me.kavishdevar.aln.utils

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize

enum class Enums(val value: ByteArray) {
    NOISE_CANCELLATION(Capabilities.NOISE_CANCELLATION),
    CONVERSATION_AWARENESS(Capabilities.CONVERSATION_AWARENESS),
    CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY(Capabilities.CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY),
    PREFIX(byteArrayOf(0x04, 0x00, 0x04, 0x00)),
    SETTINGS(byteArrayOf(0x09, 0x00)),
    SUFFIX(byteArrayOf(0x00, 0x00, 0x00)),
    NOTIFICATION_FILTER(byteArrayOf(0x0f)),
    HANDSHAKE(byteArrayOf(0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
    SPECIFIC_FEATURES(byteArrayOf(0x4d)),
    SET_SPECIFIC_FEATURES(PREFIX.value + SPECIFIC_FEATURES.value + byteArrayOf(0x00,
        0xff.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
    REQUEST_NOTIFICATIONS(PREFIX.value + NOTIFICATION_FILTER.value + byteArrayOf(0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte())),
    NOISE_CANCELLATION_PREFIX(PREFIX.value + SETTINGS.value + NOISE_CANCELLATION.value),
    NOISE_CANCELLATION_OFF(NOISE_CANCELLATION_PREFIX.value + Capabilities.NoiseCancellation.OFF.value + SUFFIX.value),
    NOISE_CANCELLATION_ON(NOISE_CANCELLATION_PREFIX.value + Capabilities.NoiseCancellation.ON.value + SUFFIX.value),
    NOISE_CANCELLATION_TRANSPARENCY(NOISE_CANCELLATION_PREFIX.value + Capabilities.NoiseCancellation.TRANSPARENCY.value + SUFFIX.value),
    NOISE_CANCELLATION_ADAPTIVE(NOISE_CANCELLATION_PREFIX.value + Capabilities.NoiseCancellation.ADAPTIVE.value + SUFFIX.value),
    SET_CONVERSATION_AWARENESS_OFF(PREFIX.value + SETTINGS.value + CONVERSATION_AWARENESS.value + Capabilities.ConversationAwareness.OFF.value + SUFFIX.value),
    SET_CONVERSATION_AWARENESS_ON(PREFIX.value + SETTINGS.value + CONVERSATION_AWARENESS.value + Capabilities.ConversationAwareness.ON.value + SUFFIX.value),
    CONVERSATION_AWARENESS_RECEIVE_PREFIX(PREFIX.value + byteArrayOf(0x4b, 0x00, 0x02, 0x00));
}

object BatteryComponent {
    const val LEFT = 4
    const val RIGHT = 2
    const val CASE = 8
}

object BatteryStatus {
    const val CHARGING = 1
    const val NOT_CHARGING = 2
    const val DISCONNECTED = 4
}

@Parcelize
data class Battery(val component: Int, val level: Int, val status: Int) : Parcelable {
    fun getComponentName(): String? {
        return when (component) {
            BatteryComponent.LEFT -> "LEFT"
            BatteryComponent.RIGHT -> "RIGHT"
            BatteryComponent.CASE -> "CASE"
            else -> null
        }
    }

    fun getStatusName(): String? {
        return when (status) {
            BatteryStatus.CHARGING -> "CHARGING"
            BatteryStatus.NOT_CHARGING -> "NOT_CHARGING"
            BatteryStatus.DISCONNECTED -> "DISCONNECTED"
            else -> null
        }
    }
}

enum class NoiseControlMode {
    OFF,  NOISE_CANCELLATION, TRANSPARENCY, ADAPTIVE
}

class AirPodsNotifications {
    companion object {
        const val AIRPODS_CONNECTED = "me.kavishdevar.aln.AIRPODS_CONNECTED"
        const val AIRPODS_DATA = "me.kavishdevar.aln.AIRPODS_DATA"
        const val EAR_DETECTION_DATA = "me.kavishdevar.aln.EAR_DETECTION_DATA"
        const val ANC_DATA = "me.kavishdevar.aln.ANC_DATA"
        const val BATTERY_DATA = "me.kavishdevar.aln.BATTERY_DATA"
        const val CA_DATA = "me.kavishdevar.aln.CA_DATA"
        const val AIRPODS_DISCONNECTED = "me.kavishdevar.aln.AIRPODS_DISCONNECTED"
        const val AIRPODS_CONNECTION_DETECTED = "me.kavishdevar.aln.AIRPODS_CONNECTION_DETECTED"
        const val DISCONNECT_RECEIVERS = "me.kavishdevar.aln.DISCONNECT_RECEIVERS"
    }

    class EarDetection {
        private val notificationBit = Capabilities.EAR_DETECTION
        private val notificationPrefix = Enums.PREFIX.value + notificationBit

        var status: List<Byte> = listOf(0x01, 0x01)

        fun setStatus(data: ByteArray) {
            status = listOf(data[6], data[7])
        }

        fun isEarDetectionData(data: ByteArray): Boolean {
            if (data.size != 8) {
                return false
            }
            val prefixHex = notificationPrefix.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }
    }

    class ANC {
        private val notificationPrefix = Enums.NOISE_CANCELLATION_PREFIX.value

        var status: Int = 1
            private set

        fun isANCData(data: ByteArray): Boolean {
            if (data.size != 11) {
                return false
            }
            val prefixHex = notificationPrefix.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }

        fun setStatus(data: ByteArray) {
            if (data.size != 11) {
                return
            }
            status = data[7].toInt()
        }

        val name: String =
             when (status) {
                1 -> "OFF"
                2 -> "ON"
                3 -> "TRANSPARENCY"
                4 -> "ADAPTIVE"
                else -> "UNKNOWN"
            }

    }

    class BatteryNotification {
        private var first: Battery = Battery(BatteryComponent.LEFT, 0, BatteryStatus.DISCONNECTED)
        private var second: Battery = Battery(BatteryComponent.RIGHT, 0, BatteryStatus.DISCONNECTED)
        private var case: Battery = Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED)

        fun isBatteryData(data: ByteArray): Boolean {
            if (data.joinToString("") { "%02x".format(it) }.startsWith("040004000400")) {
                Log.d("BatteryNotification", "Battery data starts with 040004000400. Most likely is a battery packet.")
            } else {
                return false
            }
            if (data.size != 22) {
                Log.d("BatteryNotification", "Battery data size is not 22, probably being used with Airpods with fewer or more battery count.")
                return false
            }
            Log.d("BatteryNotification", data.joinToString("") { "%02x".format(it) }.startsWith("040004000400").toString())
            return data.joinToString("") { "%02x".format(it) }.startsWith("040004000400")
        }

        fun setBattery(data: ByteArray) {
            if (data.size != 22) {
                return
            }
            first = if (data[10].toInt() == BatteryStatus.DISCONNECTED) {
                Battery(first.component, first.level, data[10].toInt())
            } else {
                Battery(data[7].toInt(), data[9].toInt(), data[10].toInt())
            }
            second = if (data[15].toInt() == BatteryStatus.DISCONNECTED) {
                Battery(second.component, second.level, data[15].toInt())
            } else {
                Battery(data[12].toInt(), data[14].toInt(), data[15].toInt())
            }
            case = if (data[20].toInt() == BatteryStatus.DISCONNECTED && case.status != BatteryStatus.DISCONNECTED) {
                Battery(case.component, case.level, data[20].toInt())
            } else {
                Battery(data[17].toInt(), data[19].toInt(), data[20].toInt())
            }
        }

        fun getBattery(): List<Battery> {
            val left = if (first.component == BatteryComponent.LEFT) first else second
            val right = if (first.component == BatteryComponent.LEFT) second else first
            return listOf(left, right, case)
        }
    }

    class ConversationalAwarenessNotification {
        @Suppress("PrivatePropertyName")
        private val NOTIFICATION_PREFIX = Enums.CONVERSATION_AWARENESS_RECEIVE_PREFIX.value

        var status: Byte = 0
            private set

        fun isConversationalAwarenessData(data: ByteArray): Boolean {
            if (data.size != 10) {
                return false
            }
            val prefixHex = NOTIFICATION_PREFIX.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }

        fun setData(data: ByteArray) {
            status = data[9]
        }
    }
}

class Capabilities {
    companion object {
        val NOISE_CANCELLATION = byteArrayOf(0x0d)
        val CONVERSATION_AWARENESS = byteArrayOf(0x28)
        val CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY = byteArrayOf(0x01, 0x02)
        val EAR_DETECTION = byteArrayOf(0x06)
    }

    enum class NoiseCancellation(val value: ByteArray) {
        OFF(byteArrayOf(0x01)),
        ON(byteArrayOf(0x02)),
        TRANSPARENCY(byteArrayOf(0x03)),
        ADAPTIVE(byteArrayOf(0x04));
    }

    enum class ConversationAwareness(val value: ByteArray) {
        OFF(byteArrayOf(0x02)),
        ON(byteArrayOf(0x01));
    }
}

enum class LongPressMode {
    OFF, TRANSPARENCY, ADAPTIVE, ANC
}

data class LongPressPacket(val modes: Set<LongPressMode>) {
    val value: ByteArray
        get() {
            val baseArray = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x1A)
            val modeByte = calculateModeByte()
            return baseArray + byteArrayOf(modeByte, 0x00, 0x00, 0x00)
        }

    private fun calculateModeByte(): Byte {
        var modeByte: Byte = 0x00
        modes.forEach { mode ->
            modeByte = when (mode) {
                LongPressMode.OFF -> (modeByte + 0x01).toByte()
                LongPressMode.TRANSPARENCY -> (modeByte + 0x02).toByte()
                LongPressMode.ADAPTIVE -> (modeByte + 0x04).toByte()
                LongPressMode.ANC -> (modeByte + 0x08).toByte()
            }
        }
        return modeByte
    }
}

fun determinePacket(changedIndex: Int, newEnabled: Boolean, oldModes: Set<LongPressMode>, newModes: Set<LongPressMode>): ByteArray? {
    return if (newEnabled) {
        LongPressPacket(oldModes + newModes.elementAt(changedIndex)).value
    } else {
        LongPressPacket(oldModes - newModes.elementAt(changedIndex)).value
    }
}
