import sys
import threading
from PyQt5.QtWidgets import QApplication, QSystemTrayIcon, QMenu, QAction, QMessageBox
from PyQt5.QtGui import QIcon, QPixmap, QPainter
from PyQt5.QtCore import QObject, pyqtSignal, Qt
from PyQt5.QtGui import QFont, QPalette
import logging
import signal
import subprocess
import time
import os
from aln import Connection, enums
from aln.Notifications import Notifications
import argparse
import dbus
import dbus.mainloop.glib

enums = enums.enums
logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(message)s')
logging.getLogger().addHandler(logging.StreamHandler())

tray_icon = None
anc_actions = None

battery_status = {
    "LEFT": {"status": "Unknown", "level": 0},
    "RIGHT": {"status": "Unknown", "level": 0},
    "CASE": {"status": "Unknown", "level": 0}
}
anc_mode = 0
battery_status_lock = threading.Lock()

CONVERSATIONAL_AWARENESS_FILE = os.path.expanduser("~/.airpods_conversational_awareness")
CONFIG_FILE = os.path.expanduser("~/.config/aln")

def load_conversational_awareness_state():
    if os.path.exists(CONVERSATIONAL_AWARENESS_FILE):
        with open(CONVERSATIONAL_AWARENESS_FILE, "r") as file:
            return file.read().strip() == "enabled"
    return False

def save_conversational_awareness_state(enabled):
    with open(CONVERSATIONAL_AWARENESS_FILE, "w") as file:
        file.write("enabled" if enabled else "disabled")

def toggle_conversational_awareness():
    current_state = load_conversational_awareness_state()
    new_state = not current_state
    save_conversational_awareness_state(new_state)
    connection.send(enums.SET_CONVERSATION_AWARENESS_ON if new_state else enums.SET_CONVERSATION_AWARENESS_OFF)
    logging.info(f"Conversational Awareness {'enabled' if new_state else 'disabled'}")

def load_mac_address():
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, "r") as file:
            return file.read().strip()
    return None

def save_mac_address(mac_address):
    with open(CONFIG_FILE, "w") as file:
        file.write(mac_address)

def parse_arguments():
    parser = argparse.ArgumentParser(description="Standalone tray application for AirPods.")
    parser.add_argument("--mac", help="MAC address of the AirPods")
    return parser.parse_args()

def get_connected_airpods():
    logging.info("Checking for connected AirPods...")
    result = subprocess.run("bluetoothctl devices | cut -f2 -d' ' | while read uuid; do bluetoothctl info $uuid; done | grep -e 'Device\\|Connected\\|Name'", shell=True, capture_output=True, text=True)
    lines = result.stdout.splitlines()
    for i in range(0, len(lines), 3):
        if "Connected: yes" in lines[i + 2]:
            addr = lines[i].split()[1]
            name = lines[i + 1].split(": ")[1]
            logging.debug(f"Checking services for connected device: {name} ({addr})")
            services_result = run_sdptool_browse(addr)
            if services_result and "UUID 128: 74ec2172-0bad-4d01-8f77-997b2be0722a" in services_result:
                logging.info(f"Found connected AirPods: {name} ({addr})")
                return addr
    logging.error("No connected AirPods found.")
    return None

def run_sdptool_browse(addr, retries=5):
    for attempt in range(retries):
        services_result = subprocess.run(f"sdptool browse {addr}", shell=True, capture_output=True, text=True)
        if "Failed to connect to SDP server" not in services_result.stderr:
            return services_result.stdout
        logging.warning(f"Failed to connect to SDP server on {addr}, attempt {attempt + 1} of {retries}")
        time.sleep(1)
    logging.error(f"Failed to connect to SDP server on {addr} after {retries} attempts")
    return None

def set_card_profile(mac_address, profile):
    os.system(f"pactl set-card-profile bluez_card.{mac_address.replace(':', '_')} {profile}")

class MediaController:
    def __init__(self, mac_address):
        self.mac_address = mac_address
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
                set_card_profile(self.mac_address, "off")
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
                    set_card_profile(self.mac_address, "a2dp-sink")
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
                    set_card_profile(self.mac_address, "a2dp-sink")
                    logging.info("Setting profile to a2dp-sink")
                self.earStatus = "Only one in"
            return "Only one in"

def get_current_volume():
    result = subprocess.run(["pactl", "get-sink-volume", "@DEFAULT_SINK@"], capture_output=True, text=True)
    volume_line = result.stdout.splitlines()[0]
    volume_percent = int(volume_line.split()[4].strip('%'))
    return volume_percent

def set_volume(percent):
    subprocess.run(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{percent}%"])

initial_volume = get_current_volume()

def handle_conversational_awareness(status):
    if status < 1 or status > 9:
        logging.error(f"Invalid status: {status}")
        pass
    
    global initial_volume
    
    if status == 1 or status == 2:
        globals()["initial_volume"] = get_current_volume()
        new_volume = max(0, min(int(initial_volume * 0.1), 100))
    elif status == 3:
        new_volume = max(0, min(int(initial_volume * 0.4), 100))
    elif status == 6:
        new_volume = max(0, min(int(initial_volume * 0.5), 100))
    elif status >= 8:
        new_volume = initial_volume
    try:
        set_volume(new_volume)
    except Exception as e:
        logging.error(f"Error setting volume: {e}")
    logging.getLogger("Conversational Awareness").info(f"Volume set to {new_volume}% based on conversational awareness status: {status}")

    if status == 9:
        logging.getLogger("Conversational Awareness").info("Conversation ended. Restored volume to original level.")

class BatteryStatusUpdater(QObject):
    battery_status_updated = pyqtSignal(str)
    anc_mode_updated = pyqtSignal(int)
    
    def __init__(self, connection, mac_address):
        super().__init__()
        self.connection = connection
        self.media_controller = MediaController(mac_address)

    def notification_handler(self, notification_type: int, data: bytes):
        global battery_status, anc_mode
        logging.debug(f"Received data: {' '.join(f'{byte:02X}' for byte in data)}")
        if notification_type == Notifications.BATTERY_UPDATED:
            battery = self.connection.notificationListener.BatteryNotification.getBattery()
            with battery_status_lock:
                battery_status = {
                    "LEFT": {"status": battery[0].get_status(), "level": battery[0].get_level()},
                    "RIGHT": {"status": battery[1].get_status(), "level": battery[1].get_level()},
                    "CASE": {"status": battery[2].get_status(), "level": battery[2].get_level()}
                }
            status_str = get_battery_status()
            self.battery_status_updated.emit(status_str)
            logging.debug(f"Updated battery status: {battery_status}")
        elif notification_type == Notifications.EAR_DETECTION_UPDATED:
            earDetection = self.connection.notificationListener.EarDetectionNotification.getEarDetection()
            self.media_controller.handlePlayPause(earDetection)
            logging.debug(f"Received ear detection status: {earDetection}")
        elif notification_type == Notifications.ANC_UPDATED:
            anc_mode = self.connection.notificationListener.ANCNotification.status
            self.anc_mode_updated.emit(anc_mode)
            logging.debug(f"Received ANC status: {anc_mode}")
        elif notification_type == Notifications.CA_UPDATED:
            ca_status = self.connection.notificationListener.ConversationalAwarenessNotification.status
            handle_conversational_awareness(ca_status)
            logging.debug(f"Received CA status: {ca_status}")

def get_battery_status():
    global battery_status
    with battery_status_lock:
        left = battery_status["LEFT"]
        right = battery_status["RIGHT"]
        case = battery_status["CASE"]
        left_status = (left['status'] or 'Unknown').title().replace('_', ' ')
        right_status = (right['status'] or 'Unknown').title().replace('_', ' ')
        case_status = (case['status'] or 'Unknown').title().replace('_', ' ')
        
        status_emoji = {
            "Not Charging": "",
            "Charging": "⚡",
        }
        
        left_status_emoji = status_emoji.get(left_status, "")
        right_status_emoji = status_emoji.get(right_status, "")
        case_status_emoji = status_emoji.get(case_status, "")
        
        return f"Left: {left['level']}% {left_status_emoji} | Right: {right['level']}% {right_status_emoji} | Case: {case['level']}% {case_status_emoji}"

def create_battery_icon():
    global battery_status
    with battery_status_lock:
        left_level = battery_status["LEFT"]["level"]
        right_level = battery_status["RIGHT"]["level"]
        lowest_level = min(left_level, right_level)
        icon_size = 64

        pixmap = QPixmap(icon_size, icon_size)
        pixmap.fill(Qt.transparent)

        painter = QPainter(pixmap)
        
        is_dark_mode = QApplication.palette().color(QPalette.Window).value() < 128
        text_color = Qt.white if is_dark_mode else Qt.black
        
        painter.setPen(text_color)
        painter.setFont(QFont('Arial', 20, QFont.Bold))
        painter.drawText(pixmap.rect(), Qt.AlignCenter, f"{lowest_level}%")
        painter.end()

        return QIcon(pixmap)

def signal_handler(sig, frame):
    logging.info("Exiting...")
    QApplication.quit()
    sys.exit(0)

connection=None
battery_status_updater = None

def update_anc_menu(anc_mode, actions):
    for action in actions:
        action.setChecked(False)
    if anc_mode == 1:
        actions[0].setChecked(True)
    elif anc_mode == 2:
        actions[1].setChecked(True)
    elif anc_mode == 3:
        actions[2].setChecked(True)
    elif anc_mode == 4:
        actions[3].setChecked(True)

def toggle_conversational_awareness_action(action):
    toggle_conversational_awareness()
    action.setChecked(load_conversational_awareness_state())

def listen_for_device_connections():
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()
    logging.info("Listening for device connections...")

    def device_connected(interface, changed, invalidated, path):
        # /org/bluez/hci0/dev_mac_address/*
        # repl _ with : in mac_address and check         
        
        if 'Connected' in changed and changed['Connected']:
            if path.split("/")[-1] == "":
                return 
            addr = path.split("/")[-1].replace("_", ":").replace("dev:", "")
            name = changed.get('Name', 'Unknown')
            logging.info(f"Device connected: {name} ({addr})")
            logging.debug(f"Running command: sdptool browse {addr}")
            services_result = run_sdptool_browse(addr)
            logging.debug(f"Services result: {services_result}")
            if services_result and "UUID 128: 74ec2172-0bad-4d01-8f77-997b2be0722a" in services_result:
                logging.info(f"Found connected AirPods: {name} ({addr})")
                connect_to_airpods(addr)

    bus.add_signal_receiver(device_connected, dbus_interface="org.freedesktop.DBus.Properties", signal_name="PropertiesChanged", path_keyword="path")

    def interfaces_added(path, interfaces):
        logging.debug(f"Interfaces added: {path}")
        if 'org.bluez.Device1' in interfaces and interfaces['org.bluez.Device1'].get('Connected', False):
            addr = interfaces['org.bluez.Device1']['Address']
            name = interfaces['org.bluez.Device1']['Name']
            logging.info(f"Device connected: {name} ({addr})")
            if path.endswith("/sep1"):
                services_result = run_sdptool_browse(addr)
                if services_result and "UUID 128: 74ec2172-0bad-4d01-8f77-997b2be0722a" in services_result:
                    logging.info(f"Found connected AirPods: {name} ({addr})")
                    connect_to_airpods(addr)

    bus.add_signal_receiver(interfaces_added, dbus_interface="org.freedesktop.DBus.ObjectManager", signal_name="InterfacesAdded")

    bus.add_signal_receiver(audio_device_changed, dbus_interface="org.PulseAudio.Core1.Device", signal_name="NewPlaybackStream")

    from gi.repository import GLib
    loop = GLib.MainLoop()
    loop.run()

def audio_device_changed(*args, **kwargs):
    logging.info("Audio output device changed, checking for connected AirPods...")
    mac_address = get_connected_airpods()
    if mac_address:
        connect_to_airpods(mac_address)

def connect_to_airpods(mac_address):
    logging.info(f"Attempting to connect to AirPods at {mac_address}...")
    globals()["connection"] = Connection(mac_address)
    try:
        connection.connect()
        connection.send(enums.HANDSHAKE)
        globals()["battery_status_updater"] = BatteryStatusUpdater(connection, mac_address)
        connection.initialize_notifications(battery_status_updater.notification_handler)
        
        battery_status_updater.battery_status_updated.connect(lambda status: tray_icon.setToolTip(status))
        battery_status_updater.battery_status_updated.connect(lambda: tray_icon.setIcon(create_battery_icon()))
        battery_status_updater.anc_mode_updated.connect(lambda mode: update_anc_menu(mode, anc_actions))
        
        save_mac_address(mac_address)
        logging.info("Connected to AirPods successfully.")
    except Exception as e:
        logging.error(f"Failed to connect to AirPods: {e}")

def main():
    args = parse_arguments()
    mac_address = args.mac or load_mac_address()

    logging.debug("Starting standalone tray application...")
    app = QApplication(sys.argv)

    globals()["tray_icon"] = QSystemTrayIcon(create_battery_icon(), app)
    tray_icon.setToolTip(get_battery_status())

    menu = QMenu()

    ca_toggle_action = QAction("Toggle Conversational Awareness")
    ca_toggle_action.setCheckable(True)
    ca_toggle_action.setChecked(load_conversational_awareness_state())
    ca_toggle_action.triggered.connect(lambda: toggle_conversational_awareness_action(ca_toggle_action))
    menu.addAction(ca_toggle_action)

    anc_on_action = QAction("Noise Cancellation")
    anc_on_action.setCheckable(True)
    anc_on_action.triggered.connect(lambda: control_anc("on"))
    menu.addAction(anc_on_action)

    anc_off_action = QAction("Off")
    anc_off_action.setCheckable(True)
    anc_off_action.triggered.connect(lambda: control_anc("off"))
    menu.addAction(anc_off_action)

    anc_transparency_action = QAction("Transparency")
    anc_transparency_action.setCheckable(True)
    anc_transparency_action.triggered.connect(lambda: control_anc("transparency"))
    menu.addAction(anc_transparency_action)

    anc_adaptive_action = QAction("Adaptive")
    anc_adaptive_action.setCheckable(True)
    anc_adaptive_action.triggered.connect(lambda: control_anc("adaptive"))
    menu.addAction(anc_adaptive_action)

    globals()["anc_actions"] = [anc_off_action, anc_on_action, anc_transparency_action, anc_adaptive_action]

    quit_action = QAction("Quit")
    quit_action.triggered.connect(lambda: signal_handler(signal.SIGINT, None))
    menu.addAction(quit_action)

    tray_icon.setContextMenu(menu)
    tray_icon.show()
    
    logging.info("Standalone tray application started.")
    
    if mac_address:
        connect_to_airpods(mac_address)
    else:
        mac_address = get_connected_airpods()
        if mac_address:
            connect_to_airpods(mac_address)
        else:
            listen_for_device_connections()
        
    signal.signal(signal.SIGINT, signal_handler)

    try:
        sys.exit(app.exec_())
    except Exception as e:
        logging.error(f"An error occurred: {e}")
        sys.exit(1)

def control_anc(action):
    command = enums.NOISE_CANCELLATION_OFF
    if action == "on":
        command = enums.NOISE_CANCELLATION_ON
    elif action == "off":
        command = enums.NOISE_CANCELLATION_OFF
    elif action == "transparency":
        command = enums.NOISE_CANCELLATION_TRANSPARENCY
    elif action == "adaptive":
        command = enums.NOISE_CANCELLATION_ADAPTIVE
    connection.send(command)
    logging.info(f"ANC action: {action}")

main()