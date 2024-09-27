import socket
import pickle
import subprocess
from aln.Notifications import Battery
import threading
import time
import os
SOCKET_PATH = "/tmp/airpods_daemon.sock"

class MediaController:
    def __init__(self):
        self.wasMusicPlaying = False
        self.earStatus = "Both out"
        self.status = "Stopped"
        self.stop_thread_event = threading.Event()
        self.wasMusicPlayingInSingle = False
        self.wasMusicPlayingInBoth = False
        self.firstEarOutTime = 0

    def playMusic(self):
        print("Playing music")
        subprocess.call(("playerctl", "play", "--ignore-player", "OnePlus_7"))

    def pauseMusic(self):
        print("Pausing music")
        subprocess.call(("playerctl", "pause", "--player", "spotify"))

    def isPlaying(self):
        status = subprocess.check_output(["playerctl", "status", "--player", "spotify"]).decode("utf-8").strip()
        print(f"Music status: {status}")
        return status == "Playing"

    def handlePlayPause(self, data):
        primary_status = data[0]
        secondary_status = data[1]

        print(f"Handle play/pause called with data: {data}, previousStatus: {self.status}, wasMusicPlaying: {self.wasMusicPlaying}")

        def delayed_action(s):
            if not self.stop_thread_event.is_set():
                print("Delayed action")
                if self.wasMusicPlayingInSingle:
                    self.playMusic()
                    self.wasMusicPlayingInBoth = False
                elif self.wasMusicPlayingInBoth or s:
                    self.wasMusicPlayingInBoth = True
                self.wasMusicPlayingInSingle = False
                print(self.wasMusicPlayingInSingle, self.wasMusicPlayingInBoth)

        if primary_status and secondary_status:
            if self.earStatus != "Both out":
                s = self.isPlaying()
                self.pauseMusic()
                os.system("pacmd set-card-profile bluez_card.28_2D_7F_C2_05_5B off")
                if self.earStatus == "Only one in":
                    if self.firstEarOutTime != 0 and time.time() - self.firstEarOutTime < 0.3:
                        print("Only one in called with both out")
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
        print("Connecting to daemon...")
        client_socket.connect(SOCKET_PATH)
        
        media_controller = MediaController()
        
        # Receive data
        while True:
            d = client_socket.recv(1024)
            if d:
                try:
                    data = pickle.loads(d)
                    if isinstance(data, str):
                        print(f"Received data: {data}")
                    elif isinstance(data, list) and all(isinstance(b, Battery.Battery) for b in data):
                        for b in data:
                            print(f"Received battery status: {b.get_component()} is {b.get_status()} at {b.get_level()}%")
                    elif isinstance(data, list) and len(data) == 2 and all(isinstance(i, int) for i in data):
                        print(f"Received ear detection status: Is in-ear? Primary: {data[0] == 0}, Secondary: {data[1] == 0}")
                        media_controller.handlePlayPause(data)
                    else:
                        print(f"Received unknown data: {data}")
                except pickle.UnpicklingError as e:
                    print(f"Error deserializing data: {e}")
            else:
                break
        
    except Exception as e:
        print(f"Error communicating with daemon: {e}")
    finally:
        if client_socket:
            client_socket.close()
            print("Socket closed")

if __name__ == "__main__":
    read()