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