class NoiseCancellation:
    OFF = b"\x01"
    ON = b"\x02"
    TRANSPARENCY = b"\x03"
    ADAPTIVE = b"\x04"

class ConversationAwareness:
    Off = b"\x02"
    On = b"\x01"

class Capabilites:
    NOISE_CANCELLATION = b"\x0d"
    CONVERSATION_AWARENESS = b"\x28"
    CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY = b"\x01\x02"
    EAR_DETECTION = b"\x06"
    NoiseCancellation = NoiseCancellation
    ConversationAwareness = ConversationAwareness