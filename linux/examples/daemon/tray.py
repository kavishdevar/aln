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

import sys
import socket
import json
import signal
import threading
from PyQt5.QtWidgets import QApplication, QSystemTrayIcon, QMenu, QAction, QMessageBox
from PyQt5.QtGui import QIcon
from PyQt5.QtCore import QObject, pyqtSignal
import logging
import subprocess
import time
import os


class CustomFormatter(logging.Formatter):
    # Define color codes for different log levels
    COLORS = {
        logging.DEBUG: "\033[48;5;240;38;5;15m%s\033[1;0m",  # Grey background, white bold text
        logging.INFO: "\033[48;5;34;38;5;15m%s\033[1;0m",   # Green background, white bold text
        logging.WARNING: "\033[1;48;5;214;38;5;0m%s\033[1;0m",  # Orange background, black bold text
        logging.ERROR: "\033[1;48;5;202;38;5;15m%s\033[1;0m",  # Orange-red background, white bold text
        logging.CRITICAL: "\033[1;48;5;196;38;5;15m%s\033[1;0m",  # Pure red background, white bold text
    }

    def format(self, record):
        # Apply color to the level name
        levelname = self.COLORS.get(record.levelno, "%s") % record.levelname.ljust(8)
        record.levelname = levelname

        # Format the message
        formatted_message = super().format(record)

        return formatted_message

# Custom formatter with fixed width for level name
formatter = CustomFormatter('\033[2;37m%(asctime)s\033[1;0m - %(levelname)s - %(message)s')

logging.basicConfig(level=logging.DEBUG)

# Set the custom formatter for the root logger
logging.getLogger().handlers[0].setFormatter(formatter)

SOCKET_PATH = "/tmp/airpods_daemon.sock"

# Initialize battery_status at the module level
battery_status = {
    "LEFT": {"status": "Unknown", "level": 0},
    "RIGHT": {"status": "Unknown", "level": 0},
    "CASE": {"status": "Unknown", "level": 0}
}
anc_mode = 0
# Define a lock
battery_status_lock = threading.Lock()

class MediaController:
    def __init__(self):
        self.earStatus = "Both out"
        self.wasMusicPlayingInSingle = False
        self.wasMusicPlayingInBoth = False
        self.firstEarOutTime = 0
        self.stop_thread_event = threading.Event()

    def playMusic(self):
        logging.info("Playing music")
        subprocess.call(("playerctl", "play"))

    def pauseMusic(self):
        logging.info("Pausing music")
        subprocess.call(("playerctl", "--all-players", "pause"))

    def isPlaying(self):
        return "Playing" in subprocess.getoutput("playerctl --all-players status").strip() 

    def handlePlayPause(self, data):
        primary_status = data[0]
        secondary_status = data[1]

        logging.debug(f"Handling play/pause with data: {data}, previousStatus: {self.earStatus}, wasMusicPlaying: {self.wasMusicPlayingInSingle or self.wasMusicPlayingInBoth}")

        def delayed_action(s):
            if not self.stop_thread_event.is_set():
                if self.wasMusicPlayingInSingle:
                    self.playMusic()
                    self.wasMusicPlayingInBoth = False
                elif self.wasMusicPlayingInBoth or s:
                    self.wasMusicPlayingInBoth = True
                self.wasMusicPlayingInSingle = False

        if primary_status and secondary_status:
            if self.earStatus != "Both out":
                s = self.isPlaying()
                if s:
                    self.pauseMusic()
                os.system("pactl set-card-profile bluez_card.28_2D_7F_C2_05_5B off")
                logging.info("Setting profile to off")
                if self.earStatus == "Only one in":
                    if self.firstEarOutTime != 0 and time.time() - self.firstEarOutTime < 0.3:
                        self.wasMusicPlayingInSingle = True
                        self.wasMusicPlayingInBoth = True
                        self.stop_thread_event.set()
                    else:
                        if s:
                            self.wasMusicPlayingInSingle = True
                        else:
                            self.wasMusicPlayingInSingle = False
                elif self.earStatus == "Both in":
                    s = self.isPlaying()
                    if s:
                        self.wasMusicPlayingInBoth = True
                        self.wasMusicPlayingInSingle = False
                    else:
                        self.wasMusicPlayingInSingle = False
                self.earStatus = "Both out"
            return "Both out"
        elif not primary_status and not secondary_status:
            if self.earStatus != "Both in":
                if self.earStatus == "Both out":
                    os.system("pactl set-card-profile bluez_card.28_2D_7F_C2_05_5B a2dp-sink")
                    logging.info("Setting profile to a2dp-sink")
                elif self.earStatus == "Only one in":
                    self.stop_thread_event.set()
                    s = self.isPlaying()
                    if s:
                        self.wasMusicPlayingInBoth = True
                if self.wasMusicPlayingInSingle or self.wasMusicPlayingInBoth:
                    self.playMusic()
                    self.wasMusicPlayingInBoth = True
                self.wasMusicPlayingInSingle = False
                self.earStatus = "Both in"
            return "Both in"
        elif (primary_status and not secondary_status) or (not primary_status and secondary_status):
            if self.earStatus != "Only one in":
                self.stop_thread_event.clear()
                s = self.isPlaying()
                if s:
                    self.pauseMusic()
                delayed_thread = threading.Timer(0.3, delayed_action, args=[s])
                delayed_thread.start()
                self.firstEarOutTime = time.time()
                if self.earStatus == "Both out":
                    os.system("pactl set-card-profile bluez_card.28_2D_7F_C2_05_5B a2dp-sink")
                    logging.info("Setting profile to a2dp-sink")
                self.earStatus = "Only one in"
            return "Only one in"

# Function to get current sink volume
def get_current_volume():
    result = subprocess.run(["pactl", "get-sink-volume", "@DEFAULT_SINK@"], capture_output=True, text=True)
    volume_line = result.stdout.splitlines()[0]
    volume_percent = int(volume_line.split()[4].strip('%'))
    return volume_percent

# Function to set sink volume
def set_volume(percent):
    subprocess.run(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{percent}%"])

initial_volume = get_current_volume()

# Handle conversational awareness
def handle_conversational_awareness(status):
    if status < 1 or status > 9:
        logging.error(f"Invalid status: {status}")
        pass
    
    global initial_volume
    
    # Volume adjustment logic
    if status == 1 or status == 2:
        globals()["initial_volume"] = get_current_volume()
        new_volume = max(0, min(int(initial_volume * 0.1), 100))  # Reduce to 10% for initial speaking
    elif status == 3:
        new_volume = max(0, min(int(initial_volume * 0.4), 100))  # Slightly increase to 40%
    elif status == 6:
        new_volume = max(0, min(int(initial_volume * 0.5), 100))  # Set volume to 50%
    elif status >= 8:
        new_volume = initial_volume  # Fully restore volume

    set_volume(new_volume)
    logging.getLogger("Conversational Awareness").info(f"Volume set to {new_volume}% based on conversational awareness status: {status}")

    # If status is 9, print conversation end message
    if status == 9:
        logging.getLogger("Conversational Awareness").info("Conversation ended. Restored volume to original level.")

class BatteryStatusUpdater(QObject):
    battery_status_updated = pyqtSignal()
    anc_mode_updated = pyqtSignal()
    
    def __init__(self):
        super().__init__()
        self.media_controller = MediaController()

    def listen_to_socket(self):
        global battery_status
        global anc_mode
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
            client.connect(SOCKET_PATH)
            while True:
                data = client.recv(1024)
                if data:
                    try:
                        response = json.loads(data.decode('utf-8'))
                        if response["type"] == "battery":
                            with battery_status_lock:
                                battery_status = response
                            logging.debug(f"Received battery status: {response}")
                            self.battery_status_updated.emit()
                        elif response["type"] == "ear_detection":
                            self.media_controller.handlePlayPause([response['primary'], response['secondary']])
                            logging.debug(f"Received ear detection status: {response}")
                        elif response["type"] == "anc":
                            anc_mode = response["mode"]
                            self.anc_mode_updated.emit()
                            logging.debug(f"Received ANC status: {anc_mode}")
                        elif response["type"] == "ca":
                            ca_status = response["status"]
                            handle_conversational_awareness(ca_status)
                            logging.debug(f"Received CA status: {ca_status}")
                    except json.JSONDecodeError as e:
                        logging.warning(f"Error deserializing data: {e}")
                    except KeyError as e:
                        logging.error(f"KeyError: {e} in data: {response}")

def get_battery_status():
    global battery_status
    with battery_status_lock:
        logging.info(f"Getting battery status: {battery_status}")
        left = battery_status["LEFT"]
        right = battery_status["RIGHT"]
        case = battery_status["CASE"]
        left_status = (left['status'] or 'Unknown').title().replace('_', ' ')
        right_status = (right['status'] or 'Unknown').title().replace('_', ' ')
        case_status = (case['status'] or 'Unknown').title().replace('_', ' ')
        return f"Left: {left['level']}% - {left_status} | Right: {right['level']}% - {right_status} | Case: {case['level']}% - {case_status}"
    
from aln import enums
def set_anc_mode(mode):
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
        client.connect(SOCKET_PATH)
        command = enums.NOISE_CANCELLATION_OFF
        if mode == "on":
            command = enums.NOISE_CANCELLATION_ON
        elif mode == "off":
            command = enums.NOISE_CANCELLATION_OFF
        elif mode == "transparency":
            command = enums.NOISE_CANCELLATION_TRANSPARENCY
        elif mode == "adaptive":
            command = enums.NOISE_CANCELLATION_ADAPTIVE
        client.sendall(command)
        response = client.recv(1024)
        return json.loads(response.decode())

def control_anc(action):
    response = set_anc_mode(action)
    logging.info(f"ANC action: {action}, Response: {response}")

def signal_handler(sig, frame):
    logging.info("Exiting...")
    QApplication.quit()
    sys.exit(0)

# Register the signal handler for SIGINT
signal.signal(signal.SIGINT, signal_handler)

app = QApplication(sys.argv)

# Create the system tray icon
tray_icon = QSystemTrayIcon(QIcon("icon.png"), app)
tray_icon.setToolTip(get_battery_status())

# Create the menu
menu = QMenu()

# Add ANC control actions
anc_on_action = QAction("ANC On")
anc_on_action.triggered.connect(lambda: control_anc("on"))
menu.addAction(anc_on_action)

anc_off_action = QAction("ANC Off")
anc_off_action.triggered.connect(lambda: control_anc("off"))
menu.addAction(anc_off_action)

anc_transparency_action = QAction("Transparency Mode")
anc_transparency_action.triggered.connect(lambda: control_anc("transparency"))
menu.addAction(anc_transparency_action)

anc_adaptive_action = QAction("Adaptive Mode")
anc_adaptive_action.triggered.connect(lambda: control_anc("adaptive"))
menu.addAction(anc_adaptive_action)

quit = QAction("Quit")
quit.triggered.connect(app.quit)
menu.addAction(quit)

# Add the menu to the tray icon
tray_icon.setContextMenu(menu)

# Show the tray icon
tray_icon.show()

# Create an instance of BatteryStatusUpdater
battery_status_updater = BatteryStatusUpdater()

# Connect the signal to the slot
battery_status_updater.battery_status_updated.connect(lambda: tray_icon.setToolTip(get_battery_status()))

# Start the battery status listener thread
listener_thread = threading.Thread(target=battery_status_updater.listen_to_socket, daemon=True)
listener_thread.start()

# Run the application
sys.exit(app.exec_())
