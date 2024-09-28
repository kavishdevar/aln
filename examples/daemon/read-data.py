import socket
import pickle
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
                    data = pickle.loads(d)
                    if isinstance(data, str):
                        print(f"Received data: {data}")
                    elif isinstance(data, list) and all(isinstance(b, Battery.Battery) for b in data):
                        for b in data:
                            print(f"Received battery status: {b.get_component()} is {b.get_status()} at {b.get_level()}%")
                    elif isinstance(data, list) and len(data) == 2 and all(isinstance(i, int) for i in data):
                        print(f"Received ear detection status: Is in-ear? Primary: {data[0] == 0}, Secondary: {data[1] == 0}")
                    else:
                        print(f"Received unknown data: {data}")
                        all(isinstance(b, Battery.Battery) for b in data)
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