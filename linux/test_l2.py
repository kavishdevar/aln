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

import bluetooth

address="28:2D:7F:C2:05:5B"

try:
    sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)
    sock.connect((address, 0x1001))
    sock.send(b"\x00\x00\x04\x00\x01\x00\x02\x00\x00\x00\x00\x00\x00\x00\x00\x00")
except bluetooth.btcommon.BluetoothError as e:
    print(f"Error: {e}")
