from .Listener import NotificationListener
from ..enums import enums
import bluetooth
import logging

logging = logging.getLogger(__name__)

enums = enums()

class Notifications:
    BATTERY_UPDATED = NotificationListener.BATTERY_UPDATED
    ANC_UPDATED = NotificationListener.ANC_UPDATED
    EAR_DETECTION_UPDATED = NotificationListener.EAR_DETECTION_UPDATED
    UNKNOWN = NotificationListener.UNKNOWN
    def __init__(self, socket: bluetooth.BluetoothSocket, callback: callable):
        self.socket = socket
        self.notificationListener = NotificationListener(self.socket, callback)
        self.BatteryNotification = self.notificationListener.BatteryNotification
        self.EarDetectionNotification = self.notificationListener.EarDetectionNotification
        pass

    def initialize(self):
        try:
            self.socket.send(enums.REQUEST_NOTIFICATIONS)
            self.notificationListener.start()
            
        except bluetooth.btcommon.BluetoothError as e:
            logging.error(f'Failed to send data to {self.mac_address}: {e}')
            return False
        return True

    def __del__(self):
        self.notificationListener.stop()
        self.socket.close()
        pass
    pass