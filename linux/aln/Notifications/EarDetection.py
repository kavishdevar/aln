from ..Capabilites import Capabilites
from ..enums import enums
from typing import Literal

class EarDetectionNotification:
    NOTIFICATION_BIT = Capabilites.EAR_DETECTION
    NOTIFICATION_PREFIX = enums.PREFIX + NOTIFICATION_BIT
    IN_EAR = 0x00
    OUT_OF_EAR = 0x01
    def __init__(self):
        pass
    
    def isEarDetectionData(self, data: bytes):
        if len(data) != 8:
            return False
        if data.hex().startswith(self.NOTIFICATION_PREFIX.hex()):
            return True
    
    def setEarDetection(self, data: bytes):
        self.first = data[6]
        self.second = data[7]

    def getEarDetection(self):
        return [self.first, self.second]
        pass
        