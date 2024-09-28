import socket
import json
import subprocess
from aln.Notifications import Battery
import threading
import time
import os
import logging


# Configure logging
logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(message)s')

# Colorful logging
logging.addLevelName(logging.DEBUG, "\033[1;34m%s\033[1;0m" % logging.getLevelName(logging.DEBUG))
logging.addLevelName(logging.INFO, "\033[1;32m%s\033[1;0m" % logging.getLevelName(logging.INFO))
logging.addLevelName(logging.WARNING, "\033[1;33m%s\033[1;0m" % logging.getLevelName(logging.WARNING))
logging.addLevelName(logging.ERROR, "\033[1;31m%s\033[1;0m" % logging.getLevelName(logging.ERROR))
logging.addLevelName(logging.CRITICAL, "\033[1;41m%s\033[1;0m" % logging.getLevelName(logging.CRITICAL))

SOCKET_PATH = "/tmp/airpods_daemon.sock"

class MediaController:
    def __init__(self):
        self.earStatus = "Both out"
        self.wasMusicPlayingInSingle = False
        self.wasMusicPlayingInBoth = False
        self.firstEarOutTime = 0
        self.stop_thread_event = threading.Event()

    def playMusic(self):
        subprocess.call(("playerctl", "play", "--ignore-player", "OnePlus_7"))

    def pauseMusic(self):
        subprocess.call(("playerctl", "pause", "--ignore-player", "OnePlus_7"))

    def isPlaying(self):
        return subprocess.check_output(["playerctl", "status", "--player", "spotify"]).decode("utf-8").strip() == "Playing"

    def handlePlayPause(self, data):
        primary_status = data[0]
        secondary_status = data[1]

        logging.debug(f"Handle play/pause called with data: {data}, previousStatus: {self.earStatus}, wasMusicPlaying: {self.wasMusicPlayingInSingle or self.wasMusicPlayingInBoth}")

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
                self.pauseMusic()
                os.system("pacmd set-card-profile bluez_card.28_2D_7F_C2_05_5B off")
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
                    os.system("pacmd set-card-profile bluez_card.28_2D_7F_C2_05_5B a2dp_sink")
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
                self.pauseMusic()
                delayed_thread = threading.Timer(0.3, delayed_action, args=[s])
                delayed_thread.start()
                self.firstEarOutTime = time.time()
                if self.earStatus == "Both out":
                    os.system("pacmd set-card-profile bluez_card.28_2D_7F_C2_05_5B a2dp_sink")
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
                        logging.debug(f"Ear detection: {data['primary']} - {data['secondary']}")
                        media_controller.handlePlayPause([data['primary'], data['secondary']])
                except json.JSONDecodeError as e:
                    logging.error(f"Error deserializing data: {e}")
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
