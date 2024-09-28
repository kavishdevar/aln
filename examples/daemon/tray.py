import sys
import socket
import json
import signal
import threading
from PyQt5.QtWidgets import QApplication, QSystemTrayIcon, QMenu, QAction, QMessageBox
from PyQt5.QtGui import QIcon
from PyQt5.QtCore import QObject, pyqtSignal
import logging

SOCKET_PATH = "/tmp/airpods_daemon.sock"

# Initialize battery_status at the module level
battery_status = {
    "LEFT": {"status": "Unknown", "level": 0},
    "RIGHT": {"status": "Unknown", "level": 0},
    "CASE": {"status": "Unknown", "level": 0}
}

# Define a lock
battery_status_lock = threading.Lock()

class BatteryStatusUpdater(QObject):
    battery_status_updated = pyqtSignal()

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
                            print(response)
                            with battery_status_lock:
                                battery_status = response
                            self.battery_status_updated.emit()
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