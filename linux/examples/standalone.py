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

import threading
import bluetooth
import subprocess
import time
import threading
import os

# Bluetooth MAC address of AirPods
AIRPODS_MAC = "28:2D:7F:C2:05:5B"

class initL2CAP():
    lastEarStatus = ""
    earStatus = ""
    wasMusicPlayingInBoth = False
    wasMusicPlayingInSingle = False

    def pauseMusic(self):
        subprocess.call(("playerctl", "pause", "--ignore-player", "OnePlus_7"))

    def playMusic(self):
        subprocess.call(("playerctl", "play", "--ignore-player", "OnePlus_7"))

    def getMusicStatus(self):
        return subprocess.getoutput("playerctl status --ignore-player OnePlus_7").strip()

    # Change to MAC address of your AirPods

    connected = False

    cmd_off = b"\x04\x00\x04\x00\x09\x00\x0d\x01\x00\x00\x00"
    cmd_on = b"\x04\x00\x04\x00\x09\x00\x0d\x02\x00\x00\x00"
    cmd_transparency = b"\x04\x00\x04\x00\x09\x00\x0d\x03\x00\x00\x00"
    cmd_adaptive =     b"\x04\x00\x04\x00\x09\x00\x0d\x04\x00\x00\x00"
    cmd_ca_off = b"\x04\x00\x04\x00\x09\x00\x28\x02\x00\x00\x00"
    cmd_ca_on = b"\x04\x00\x04\x00\x09\x00\x28\x01\x00\x00\x00"

    def start(self):
        cmd_handshake = b"\x00\x00\x04\x00\x01\x00\x02\x00\x00\x00\x00\x00\x00\x00\x00\x00"
        # cmd_smth0 = b"\x04\x00\x04\x00\x0f\x00\xff\xff\xfe\xff"
        cmd_smth1 = b"\x04\x00\x04\x00\x0f\x00\xff\xff\xff\xff"

        address = "28:2D:7F:C2:05:5B"

        aap_service = "74EC2172-0BAD-4D01-8F77-997B2BE0722A"
        aap_port = 0x1001

        services = bluetooth.find_service(address=address)

        service = [s for s in services if s["service-classes"] == [aap_service]]

        if not service:
            print("Device does not have AAP service")
            exit()

        self.sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)
        sock = self.sock
        sock.connect((address, aap_port))

        print("Connected to AirPods")
        self.connected = True
        print("Sending handshake...")
        print(sock.type)

        sock.send(cmd_handshake)
        # sock.send(cmd_smth0)
        sock.send(cmd_smth1)
        
        threading.Thread(target=self.listen).start()
        
    # battery info: 04 00 04 00 04 00 03 02 01 64 01 01 04 01 64 01 01 08 01 34 02 01

    def parse_battery_status(self, data):
        if len(data) != 22:
            return
        self.left_bud_level = data[9]
        self.left_bud_status = data[10]
        
        self.right_bud_level = data[14]
        self.right_bud_status = data[15]
        
        self.case_level = data[19]
        self.case_status = data[20]
        
        # Interpret the status
        def interpret_status(status):
            if status == 1:
                return "Charging"
            elif status == 2:
                return "Not charging"
            elif status == 4:
                return "Disconnected"
            else:
                return "Unknown"
        
        # Print the results
        print(f"Left Bud: {self.left_bud_level}% - {interpret_status(self.left_bud_status)}")
        print(f"Right Bud: {self.right_bud_level}% - {interpret_status(self.right_bud_status)}")
        print(f"Case: {self.case_level}% - {interpret_status(self.case_status)}")


    def parse_anc_status(self, data):
        # 04 00 04 00 09 00 0d 03 00 00 00
        if len(data) != 11 and data.hex().startswith("040004000600"):
            return
        if data[7] == 1:
            return "Off"
        elif data[7] == 2:
            return "On"
        elif data[7] == 3:
            return "Transparency"
        elif data[7] == 4:
            return "Adaptive"

    firstEarOutTime = 0
    stop_thread_event = threading.Event()

    def parse_inear_status(self, data):
        if len(data) != 8:
            return

        second_status = data[6]
        first_status = data[7]

        def delayed_action(self, s):
            print(s)
            if not self.stop_thread_event.is_set():
                print("Delayed action")
                if self.wasMusicPlayingInSingle:
                    self.playMusic()
                    self.wasMusicPlayingInBoth = False
                elif self.wasMusicPlayingInBoth or s == "Playing":
                    self.wasMusicPlayingInBoth = True
                self.wasMusicPlayingInSingle = False    
        
        if first_status and second_status:
            if self.earStatus != "Both out":
                s = self.getMusicStatus()
                self.pauseMusic()
                os.system("pacmd set-card-profile bluez_card.28_2D_7F_C2_05_5B off")
                if self.earStatus == "Only one in":
                    if self.firstEarOutTime != 0 and time.time() - self.firstEarOutTime < 0.3:
                        print("Only one in called with both out")
                        self.wasMusicPlayingInSingle = True
                        self.wasMusicPlayingInBoth = True
                        self.stop_thread_event.set()
                    else:
                        if s == "Playing":
                           self.wasMusicPlayingInSingle = True
                        else:
                            self.wasMusicPlayingInSingle = False
                    # wasMusicPlayingInSingle = True
                elif self.earStatus == "Both in":
                    # should be unreachable
                    s = self.getMusicStatus()
                    if s == "Playing":
                        self.wasMusicPlayingInBoth = True
                        self.wasMusicPlayingInSingle = False
                    else:
                        self.wasMusicPlayingInSingle = False
                self.earStatus = "Both out"
            return "Both out"
        elif not first_status and not second_status:
            if self.earStatus != "Both in":
                if self.earStatus == "Both out":
                    os.system("pacmd set-card-profile bluez_card.28_2D_7F_C2_05_5B a2dp_sink")
                elif self.earStatus == "Only one in":
                    self.stop_thread_event.set()
                    s = self.getMusicStatus()
                    if s == "Playing":
                        self.wasMusicPlayingInBoth = True
                if self.wasMusicPlayingInSingle or self.wasMusicPlayingInBoth:
                    self.playMusic()
                    self.wasMusicPlayingInBoth = True
                self.wasMusicPlayingInSingle = False
                self.earStatus = "Both in"
            return "Both in"
        elif (first_status and not second_status) or (not first_status and second_status):
            if self.earStatus != "Only one in":
                self.stop_thread_event.clear()
                s = self.getMusicStatus()
                self.pauseMusic()
                delayed_thread = threading.Timer(0.3, delayed_action, args=[self, s])
                delayed_thread.start()
                self.firstEarOutTime = time.time()
                if self.earStatus == "Both out":
                    os.system("pacmd set-card-profile bluez_card.28_2D_7F_C2_05_5B a2dp_sink")
                self.earStatus = "Only one in"
                        
            return "Only one in"

    def listen(self):
        while True:
            res = self.sock.recv(1024)
            print(f"Response: {res.hex()}")
            self.battery_status = self.parse_battery_status(res)
            self.inear_status = self.parse_inear_status(res)
            # anc_status = parse_anc_status(res)
            # if anc_status:
            #     print("ANC: ", anc_status)
            if self.battery_status:
                print(self.battery_status)
            if self.inear_status:
                print(self.inear_status)
    

    # while True:
    #     print("Select command:")
    #     print("1. Turn off")
    #     print("2. Turn on")
    #     print("3. Toggle transparency")
    #     print("4. Toggle Adaptive")
    #     print("5. Conversational Awareness On")
    #     print("6. Conversational Awareness Off")
    #     print("0. Exit")

    #     cmd = input("Enter command: ")

    #     if cmd == "0":
    #         break
    #     elif cmd == "1":
    #         self.sock.send(cmd_off)
    #     elif cmd == "2":
    #         self.sock.send(cmd_on)
    #     elif cmd == "3":
    #         self.sock.send(cmd_transparency)
    #     elif cmd == "4":
    #         self.sock.send(cmd_adaptive)
    #     elif cmd == "5":
    #         self.sock.send(cmd_ca_on)
    #     elif cmd == "6":
    #         self.sock.send(cmd_ca_off)
            
    def stop(self):
        self.connected = False
        self.sock.close()



def is_bluetooth_connected():
    try:
        result = subprocess.run(["bluetoothctl", "info", AIRPODS_MAC], capture_output=True, text=True)
        return "Connected: yes" in result.stdout
    except Exception as e:
        print(f"Error checking Bluetooth connection status: {e}")
        return False

# Connect to Bluetooth device using bluetoothctl if not already connected
def connect_bluetooth_device():
    if is_bluetooth_connected():
        print("AirPods are already connected.")
        return

    print("Checking if AirPods are available...")
    result = subprocess.run(["bluetoothctl", "devices"], capture_output=True, text=True)
    if AIRPODS_MAC in result.stdout:
        print("AirPods are available. Connecting...")
        subprocess.run(["bluetoothctl", "connect", AIRPODS_MAC])
    else:
        print("AirPods are not available.")

    time.sleep(2)  # Wait for the connection to establish

    # Switch audio output to AirPods (PulseAudio)
    try:
        result = subprocess.run(["pactl", "list", "short", "sinks"], capture_output=True, text=True)
        sink_name = next((line.split()[1] for line in result.stdout.splitlines() if "bluez_sink" in line), None)
        if sink_name:
            subprocess.run(["pactl", "set-default-sink", sink_name])
            print(f"Switched audio to AirPods: {sink_name}")
            
        else:
            print("Failed to switch audio to AirPods.")
    except Exception as e:
        print(f"Error switching audio: {e}")

# Disconnect from Bluetooth device if connected
def disconnect_bluetooth_device():
    if not is_bluetooth_connected():
        print("AirPods are already disconnected.")
        return

    print("Disconnecting from AirPods...")
    subprocess.run(["bluetoothctl", "disconnect", AIRPODS_MAC])

l2cap = initL2CAP()

# Function to listen to `playerctl --follow` and react to status changes
def mediaListener():
    try:
        # Run playerctl --follow in a subprocess
        process = subprocess.Popen(
            ["playerctl", "--follow", "status"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )

        # Continuously read from the subprocess stdout
        for line in process.stdout:
            if line:  # Make sure the line is not empty
                line = line.strip()  # Remove any extraneous whitespace
                print(f"Received event from playerctl: {line}")
                
                if "Playing" in line:
                    print("Media started playing")
                    connect_bluetooth_device()
                    if not l2cap.connected:
                        l2cap.start()
                elif "Paused" in line or "Stopped" in line:
                    print("Media paused or stopped")
                    # disconnect_bluetooth_device()

        # Check for any errors in stderr
        stderr = process.stderr.read()
        if stderr:
            print(f"Error: {stderr}")

    except Exception as e:
        print(f"An error occurred in mediaListener: {e}")

mediaListener()

# thread = threading.Thread(target=mediaListener)
# thread.start()

# thread.stop()
