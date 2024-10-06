import socket
import json
import subprocess
from aln.Notifications import Battery
import threading
import time
import os
import logging

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

def read():
    """Send a command to the daemon via UNIX domain socket."""
    client_socket = None
    try:
        # Create a socket connection to the daemon
        client_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        logging.info("Connecting to daemon...")
        client_socket.connect(SOCKET_PATH)
        
        media_controller = MediaController()
        
        # Receive data
        while True:
            d = client_socket.recv(1024)
            if d:
                try:
                    data: dict = json.loads(d.decode('utf-8'))
                    if data["type"] == "ear_detection":
                        media_controller.handlePlayPause([data['primary'], data['secondary']])
                except json.JSONDecodeError as e:
                    # logging.error(f"Error deserializing data: {e}")
                    pass
            else:
                break
        
    except Exception as e:
        logging.error(f"Error communicating with daemon: {e}")
    finally:
        if client_socket:
            client_socket.close()
            logging.warning("Socket closed")

if __name__ == "__main__":
    read()
