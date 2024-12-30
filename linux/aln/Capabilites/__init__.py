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

class NoiseCancellation:
    OFF = b"\x01"
    ON = b"\x02"
    TRANSPARENCY = b"\x03"
    ADAPTIVE = b"\x04"

class ConversationAwareness:
    OFF = b"\x02"
    ON = b"\x01"

class Capabilites:
    NOISE_CANCELLATION = b"\x0d"
    CONVERSATION_AWARENESS = b"\x28"
    CUSTOMIZABLE_ADAPTIVE_TRANSPARENCY = b"\x01\x02"
    EAR_DETECTION = b"\x06"
    
    NoiseCancellation = NoiseCancellation
    ConversationAwareness = ConversationAwareness