# AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
#
# Copyright (C) 2024 Kavish Devar
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

from bluetooth import BluetoothSocket
import threading
from .Battery import BatteryNotification
from .EarDetection import EarDetectionNotification
from .ConversationalAwareness import ConversationalAwarenessNotification
from .ANC import ANCNotification
import logging

logging = logging.getLogger(__name__)

class NotificationListener:
    BATTERY_UPDATED = 0x01
    ANC_UPDATED = 0x02
    EAR_DETECTION_UPDATED = 0x03
    CA_UPDATED = 0x04
    UNKNOWN = 0x00

    def __init__(self, socket: BluetoothSocket, callback: callable):
        self.socket = socket
        self.BatteryNotification = BatteryNotification()
        self.EarDetectionNotification = EarDetectionNotification()
        self.ANCNotification = ANCNotification()
        self.ConversationalAwarenessNotification = ConversationalAwarenessNotification()
        self.callback = callback
        pass
    
    def __start(self):
        while True:
            data = self.socket.recv(1024)
            if len(data) == 0:
                break
            if self.BatteryNotification.isBatteryData(data):
                self.BatteryNotification.setBattery(data)
                self.callback(self.BATTERY_UPDATED, data)
                pass
            if self.EarDetectionNotification.isEarDetectionData(data):
                self.EarDetectionNotification.setEarDetection(data)
                self.callback(self.EAR_DETECTION_UPDATED, data)
            if self.ANCNotification.isANCData(data):
                self.ANCNotification.setData(data)
                self.callback(self.ANC_UPDATED, data)
            if self.ConversationalAwarenessNotification.isConversationalAwarenessData(data):
                self.ConversationalAwarenessNotification.setData(data)
                self.callback(self.CA_UPDATED, data)
            else:
                self.callback(self.UNKNOWN, data)
                pass
            pass
        pass
    
    def start(self):
        threading.Thread(target=self.__start).start()
        pass
    
    def stop(self):
        self.socket.close()
        pass
        