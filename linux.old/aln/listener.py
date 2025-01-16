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
import time

class BluetoothListener:
    def __init__(self):
        self.connected_devices = set()

    def scan_devices(self):
        nearby_devices = bluetooth.discover_devices(lookup_names=True, lookup_class=True, device_id=-1, duration=8, flush_cache=True)
        return nearby_devices

    def start_listening(self):
        print("Listening for Bluetooth devices")
        while True:
            nearby_devices = self.scan_devices()
            current_devices = set()

            for addr, name, device_class in nearby_devices:
                current_devices.add(addr)
                if addr not in self.connected_devices:
                    print(f"Device connected: {name} [{addr}]")
            
            for addr in self.connected_devices - current_devices:
                print(f"Device disconnected: [{addr}]")

            self.connected_devices = current_devices
            time.sleep(5)

if __name__ == "__main__":
    listener = BluetoothListener()
    listener.start_listening()