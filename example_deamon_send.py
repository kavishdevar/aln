import socket
from aln import enums

SOCKET_PATH = "/tmp/airpods_daemon.sock"

def send_command(command):
    """Send a command to the daemon via UNIX domain socket."""
    try:
        # Create a socket connection to the daemon
        client_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        client_socket.connect(SOCKET_PATH)
        
        # Send the command
        client_socket.send(command)
        print(f"Sent command: {command}")
        
        # Close the connection
        client_socket.close()
    except Exception as e:
        print(f"Error communicating with daemon: {e}")

if __name__ == "__main__":
    # Convert the command (enum) to bytes and send it to the daemon
    command = enums.SET_NOISE_CANCELLATION_OFF
    send_command(command)
