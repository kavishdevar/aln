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

class BatteryComponent:
    LEFT = 4
    RIGHT = 2
    CASE = 8
    pass
class BatteryStatus:
    CHARGING = 1
    NOT_CHARGING = 2
    DISCONNECTED = 4
    pass

class Battery:

    def get_name(self, cls, value):
        for key, val in cls.__dict__.items():
            if val == value:
                return key
        return None
    
    def get_component(self):
        return self.get_name(BatteryComponent, self.component)
    
    def get_level(self):
        return self.level
    
    def get_status(self):
        return self.get_name(BatteryStatus, self.status)
    
    def __init__(self, component: int, level: int, status: int):
        self.component = component
        self.level = level
        self.status = status
        pass
    
class BatteryNotification:
    def __init__(self):
        self.first = Battery(BatteryComponent.LEFT, 0, BatteryStatus.DISCONNECTED)
        self.second = Battery(BatteryComponent.RIGHT, 0, BatteryStatus.DISCONNECTED)
        self.case = Battery(BatteryComponent.CASE, 0, BatteryStatus.DISCONNECTED)
        pass
    
    def isBatteryData(self, data):
        if len(data) != 22:
            return False
        if data[0] == 0x04 and data[1] == 0x00 and data[2] == 0x04 and data[3] == 0x00 and data[4] == 0x04 and data[5] == 0x00:
            return True
        else:
            return False
    def setBattery(self, data):
        self.count = data[6]
        self.first = Battery(data[7], data[9], data[10])
        self.second = Battery(data[12], data[14], data[15])
        self.case = Battery(data[17], data[19], data[20])
        pass
    
    def getBattery(self):
        if self.first.component == BatteryComponent.LEFT:
            self.left = self.first
            self.right = self.second
        else:
            self.left = self.second
            self.right = self.first
        self.case = self.case
        return [self.left, self.right, self.case]