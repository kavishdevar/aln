# 04 00 04 00 4b 00 02 00 01 [level]

from ..enums import enums

class ConversationalAwarenessNotification:
    NOTIFICATION_PREFIX = enums.CONVERSATION_AWARENESS_RECEIVE_PREFIX
    
    def __init__(self):
        pass
    
    def isConversationalAwarenessData(self, data: bytes):
        if len(data) != 10:
            return False
        if data.hex().startswith(self.NOTIFICATION_PREFIX.hex()):
            return True
    
    def setData(self, data: bytes):
        self.status = data[9]
        pass