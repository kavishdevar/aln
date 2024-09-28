import socket
import json
from aln.Notifications import Battery

SOCKET_PATH = "/tmp/airpods_daemon.sock"

def read():
    """Send a command to the daemon via UNIX domain socket."""
    client_socket = None
    try:
        # Create a socket connection to the daemon
        client_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        print("Connecting to daemon...")
        client_socket.connect(SOCKET_PATH)
        
        # Receive data
        while True:
            d = client_socket.recv(1024)
            if d:
                try:
                    data: dict = json.loads(d.decode('utf-8'))
                    if data["type"] == "battery":
                        for b in data.keys():
                            print(f"Received battery status: {b} - {data[b]}")
                    elif data["type"] == "ear_detection":
                        print(f"Ear detection: {data['primary']} - {data['secondary']}")
                    else:
                        print(f"Received data: {data}")
                except json.JSONDecodeError as e:
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