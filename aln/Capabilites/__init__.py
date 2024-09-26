class NoiseCancellation:
    Off = b"\x01"
    On = b"\x02"
    Transparency = b"\x03"
    Adaptive = b"\x04"

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