# AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
# Copyright (C) 2024 Kavish Devar
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

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
        