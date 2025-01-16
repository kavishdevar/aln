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