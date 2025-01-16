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

from .Listener import NotificationListener
from ..enums import enums
import bluetooth
import logging

logging = logging.getLogger(__name__)

enums = enums()

class Notifications:
    BATTERY_UPDATED = NotificationListener.BATTERY_UPDATED
    ANC_UPDATED = NotificationListener.ANC_UPDATED
    CA_UPDATED = NotificationListener.CA_UPDATED
    EAR_DETECTION_UPDATED = NotificationListener.EAR_DETECTION_UPDATED
    UNKNOWN = NotificationListener.UNKNOWN
    def __init__(self, socket: bluetooth.BluetoothSocket, callback: callable):
        self.socket = socket
        self.notificationListener = NotificationListener(self.socket, callback)
        self.BatteryNotification = self.notificationListener.BatteryNotification
        self.EarDetectionNotification = self.notificationListener.EarDetectionNotification
        self.ANCNotification = self.notificationListener.ANCNotification
        self.ConversationalAwarenessNotification = self.notificationListener.ConversationalAwarenessNotification
        pass

    def initialize(self):
        try:
            self.socket.send(enums.SET_SPECIFIC_FEATURES)
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