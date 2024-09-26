from bluetooth import BluetoothSocket
import threading
from .Battery import BatteryNotification
from .EarDetection import EarDetectionNotification
import logging

logging = logging.getLogger(__name__)

class NotificationListener:
    BATTERY_UPDATED = 0x01
    ANC_UPDATED = 0x02
    EAR_DETECTION_UPDATED = 0x03

    def __init__(self, socket: BluetoothSocket, callback: callable):
        self.socket = socket
        self.BatteryNotification = BatteryNotification()
        self.EarDetectionNotification = EarDetectionNotification()
        self.callback = callback
        pass
    
    def __start(self):
        while True:
            data = self.socket.recv(1024)
            if len(data) == 0:
                break
            if self.BatteryNotification.isBatteryData(data):
                self.BatteryNotification.setBattery(data)
                self.callback(self.BATTERY_UPDATED)
                pass
            if self.EarDetectionNotification.isEarDetectionData(data):
                self.EarDetectionNotification.setEarDetection(data)
                self.callback(self.EAR_DETECTION_UPDATED)
            pass
        pass
    
    def start(self):
        threading.Thread(target=self.__start).start()
        pass
    
    def stop(self):
        self.socket.close()
        pass
        