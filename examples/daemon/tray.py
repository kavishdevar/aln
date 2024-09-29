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

SOCKET_PATH = "/tmp/airpods_daemon.sock"

# Initialize battery_status at the module level
battery_status = {
    "LEFT": {"status": "Unknown", "level": 0},
    "RIGHT": {"status": "Unknown", "level": 0},
    "CASE": {"status": "Unknown", "level": 0}
}

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
        subprocess.call(("playerctl", "play", "--ignore-player", "OnePlus_7"))

    def pauseMusic(self):
        logging.info("Pausing music")
        subprocess.call(("playerctl", "pause", "--ignore-player", "OnePlus_7"))

    def isPlaying(self):
        return subprocess.check_output(["playerctl", "status", "--player", "spotify"]).decode("utf-8").strip() == "Playing"

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

class BatteryStatusUpdater(QObject):
    battery_status_updated = pyqtSignal()

    def __init__(self):
        super().__init__()
        self.media_controller = MediaController()

    def listen_for_battery_updates(self):
        global battery_status
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
                            self.battery_status_updated.emit()
                        elif response["type"] == "ear_detection":
                            self.media_controller.handlePlayPause([response['primary'], response['secondary']])
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
        return f"Left: {left['level']}% - {left['status'].title().replace('_', ' ')} | Right: {right['level']}% - {right['status'].title().replace('_', ' ')} | Case: {case['level']}% - {case['status'].title().replace('_', ' ')}"

from aln import enums
def set_anc_mode(mode):
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
        client.connect(SOCKET_PATH)
        command = enums.SET_NOISE_CANCELLATION_OFF
        if mode == "on":
            command = enums.SET_NOISE_CANCELLATION_ON
        elif mode == "off":
            command = enums.SET_NOISE_CANCELLATION_OFF
        elif mode == "transparency":
            command = enums.SET_NOISE_CANCELLATION_TRANSPARENCY
        elif mode == "adaptive":
            command = enums.SET_NOISE_CANCELLATION_ADAPTIVE
        client.sendall(command)
        response = client.recv(1024)
        return json.loads(response.decode())

def control_anc(action):
    response = set_anc_mode(action)
    logging.info(f"ANC action: {action}, Response: {response}")

def signal_handler(sig, frame):
    print("Exiting...")
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
listener_thread = threading.Thread(target=battery_status_updater.listen_for_battery_updates, daemon=True)
listener_thread.start()

# Run the application
sys.exit(app.exec_())