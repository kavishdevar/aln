from .Notifications import Notifications
from .Capabilites import Capabilites
from .enums import enums

import bluetooth
import logging

logging = logging.getLogger("Connection Handler")

class Connection:
    def __init__(self, mac_address: str):
        self.mac_address = mac_address
        self.socket = bluetooth.BluetoothSocket(bluetooth.L2CAP)
    def connect(self):
        try:
            self.socket.connect((self.mac_address, 0x1001))
        except bluetooth.btcommon.BluetoothError as e:
            logging.error(f'Failed to connect to {self.mac_address}: {e}')
            return False
        return True
    
    def initialize_notifications(self, callback: callable = None):
        if callback is None:
            callback = self.notification_callback
        self.notifications = Notifications(self.socket, callback)
        self.notificationListener = self.notifications.notificationListener
        self.BatteryNotification = self.notifications.BatteryNotification
        self.notifications.initialize()

    def send(self, data: bytes):
        try:
            self.socket.send(data)
        except bluetooth.btcommon.BluetoothError as e:
            logging.error(f'Failed to send data to {self.mac_address}: {e}')
            return False
        return True
    
    def notification_callback(self, notification_type: int, data: bytes):
        import logging
        if notification_type == Notifications.BATTERY_UPDATED:
            logging = logging.getLogger("Battery Status")
            for i in self.notificationListener.BatteryNotification.getBattery():
                logging.debug(f'{i.get_component()} - {i.get_status()}: {i.get_level()}')
            pass
        elif notification_type == Notifications.EAR_DETECTION_UPDATED:
            logging = logging.getLogger("In-Ear Status")
            logging.debug(f'{self.notificationListener.EarDetectionNotification.getEarDetection()}')
            pass
        elif notification_type == Notifications.UNKNOWN:
            logging = logging.getLogger("Unknown Notification")
            hex_data = ' '.join(f'{byte:02x}' for byte in data)
            logging.debug(f'{hex_data}')
            pass
        pass

    def disconnect(self):
        self.socket.close()
        pass

    def __del__(self):
        self.socket.close()
        pass