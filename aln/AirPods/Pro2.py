from ..Capabilites import Capabilites
class Pro2:
    def __init__(self):
        self.name = 'AirPods Pro 2'
        self.capabilites = {
            Capabilites.NOISE_CANCELLATION: [
                Capabilites.NoiseCancellation.OFF,
                Capabilites.NoiseCancellation.ON,
                Capabilites.NoiseCancellation.TRANSPARENCY,
                Capabilites.NoiseCancellation.ADAPTIVE,
            ],
            Capabilites.CONVERSATION_AWARENESS: True,
            Capabilites.CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY: True
        }