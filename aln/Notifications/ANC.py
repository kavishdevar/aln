from ..enums import enums
from ..Capabilites import Capabilites
class ANCNotification:
    NOTIFICATION_PREFIX = enums.NOISE_CANCELLATION_PREFIX
    OFF = Capabilites.NoiseCancellation.OFF
    ON = Capabilites.NoiseCancellation.ON
    TRANSPARENCY = Capabilites.NoiseCancellation.TRANSPARENCY
    ADAPTIVE = Capabilites.NoiseCancellation.ADAPTIVE
    
    def __init__(self):
        pass
    
    def isANCData(self, data: bytes):
        # 04 00 04 00 09 00 0D 01 00 00 00
        if len(data) != 11:
            return False
        
        if data.hex().startswith(self.NOTIFICATION_PREFIX.hex()):
            return True
        else:
            return False
        
    def setANC(self, data: bytes):
        self.status = data[7]
        pass
    
    def getANC(self, returnString: bool = False, fromInt: int = None):
        if fromInt is not None:
            fromInt = bytes([fromInt])
            if fromInt == self.OFF:
                return "Off"
            elif fromInt == self.ON:
                return "On"
            elif fromInt == self.TRANSPARENCY:
                return "Transparency"
            elif fromInt == self.ADAPTIVE:
                return "Adaptive"
            pass
        if returnString:
            return self.status
        else:
            if self.status == self.OFF:
                return "Off"
            elif self.status == self.ON:
                return "On"
            elif self.status == self.TRANSPARENCY:
                return "Transparency"
            elif self.status == self.ADAPTIVE:
                return "Adaptive"
            pass