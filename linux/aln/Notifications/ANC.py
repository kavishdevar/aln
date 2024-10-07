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
        
    def setData(self, data: bytes):
        self.status = data[7]
        pass