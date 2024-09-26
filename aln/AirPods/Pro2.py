from ..Capabilites import Capabilites
class Pro2:
    def __init__(self):
        self.name = 'AirPods Pro 2'
        self.capabilites = {
            Capabilites.NOISE_CANCELLATION: [
                Capabilites.NoiseCancellation.Off,
                Capabilites.NoiseCancellation.On,
                Capabilites.NoiseCancellation.Transparency,
                Capabilites.NoiseCancellation.Adaptive,
            ],
            Capabilites.CONVERSATION_AWARENESS: True,
            Capabilites.CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY: True
        }