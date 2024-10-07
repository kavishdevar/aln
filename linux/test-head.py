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
import threading

sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)

bt_addr = "28:2D:7F:C2:05:5B" # sys.argv[1]
psm = 0x1001  # AAP

print(f"Trying to connect to {bt_addr} on PSM 0x{psm:04X}...")

sock.connect((bt_addr, psm))
running = threading.Event()

def listen():
    global running
    while not running.is_set():
        res = sock.recv(1024)
        print(f"Response: {res.hex()}")

t = threading.Thread(target=listen)
t.start()

print("Connected. Type something...")
try:
    byts = bytes(int(b, 16) for b in "00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00".split(" "))
    sock.send(byts)
    byts = bytes(int(b, 16) for b in "04 00 04 00 0F 00 FF FF FE FF".split(" "))
    sock.send(byts)
    byts = bytes(int(b, 16) for b in "04 00 04 00 17 00 00 00 10 00 11 00 08 7C 10 02 42 0B 08 4E 10 02 1A 05 01 40 9C 00 00".split(" "))
    sock.send(byts)
    import time
    time.sleep(5)
    byts = bytes(int(b, 16) for b in "04 00 04 00 17 00 00 00 10 00 11 00 08 7E 10 02 42 0B 08 4E 10 02 1A 05 01 00 00 00 00".split(" "))
    sock.send(byts)
except:
    ...
running.set()
sock.close()
