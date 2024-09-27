import socket
import threading
import signal
import sys
import logging
from aln import Connection, enums
from aln.Notifications import Notifications
import os

AIRPODS_MAC = '28:2D:7F:C2:05:5B'
SOCKET_PATH = '/tmp/airpods_daemon.sock'
LOG_FOLDER = '.'
LOG_FILE = os.path.join(LOG_FOLDER, 'airpods_daemon.log')

# Global flag to control the server loop
running = True

# Configure logging to write to a file
logging.basicConfig(filename=LOG_FILE, level=logging.DEBUG, format='%(asctime)s %(levelname)s : %(message)s')

def handle_client(connection, client_socket):
    """Handle client requests by forwarding all received data to aln.Connection."""
    while running:
        try:
            data = client_socket.recv(1024)  # Receive data in bytes
            if not data:
                break  # Client disconnected

            # Forward the raw data to aln.Connection
            connection.send(data)
            logging.info(f'Forwarded data: {data}')

        except Exception as e:
            logging.error(f"Error handling client: {e}")
            break

    client_socket.close()

def start_socket_server(connection):
    """Start a UNIX domain socket server."""
    global running

    # Set up the socket
    server_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)

    # Bind the socket to the path
    try:
        server_socket.bind(SOCKET_PATH)
    except OSError:
        logging.error(f"Socket already in use or unavailable: {SOCKET_PATH}")
        sys.exit(1)

    server_socket.listen(1)
    logging.info(f"Socket server listening on {SOCKET_PATH}")

    while running:
        try:
            client_socket, _ = server_socket.accept()
            logging.info("Client connected")

            # Handle the client connection in a separate thread
            client_thread = threading.Thread(target=handle_client, args=(connection, client_socket))
            client_thread.start()
        except Exception as e:
            logging.error(f"Error accepting connection: {e}")

    # Close the server socket when stopped
    server_socket.close()
    logging.info("Socket server stopped")

def stop_daemon(signum, frame):
    """Signal handler to stop the daemon."""
    global running
    logging.info("Received termination signal. Stopping daemon...")
    running = False  # Set running flag to False to stop the loop

    # Close the socket gracefully by removing the file path
    try:
        socket.socket(socket.AF_UNIX, socket.SOCK_STREAM).connect(SOCKET_PATH)
    except socket.error:
        pass
    finally:
        # Remove the socket file
        if os.path.exists(SOCKET_PATH):
            os.remove(SOCKET_PATH)

    sys.exit(0)

def notification_handler(notification_type: int):
    global connection
    logging.debug(f"Received notification: {notification_type}")
    if notification_type == Notifications.BATTERY_UPDATED:
        logger = logging.getLogger("Battery Status")
        for i in connection.notificationListener.BatteryNotification.getBattery():
            logger.debug(f'{i.get_component()} - {i.get_status()}: {i.get_level()}')
    elif notification_type == Notifications.EAR_DETECTION_UPDATED:
        logger = logging.getLogger("In-Ear Status")
        logger.debug(f'{connection.notificationListener.EarDetectionNotification.getEarDetection()}')

def main():
    global running

    # Set up signal handlers to handle termination signals
    signal.signal(signal.SIGINT, stop_daemon)  # Handle Ctrl+C
    signal.signal(signal.SIGTERM, stop_daemon)  # Handle kill signal

    logging.info("Starting AirPods daemon")

    # Initialize the connection to the AirPods
    global connection
    connection = Connection(AIRPODS_MAC)
    connection.connect()
    connection.send(enums.HANDSHAKE)
    logging.info("Handshake sent")
    connection.initialize_notifications(notification_handler)

    # Start the socket server to listen for client connections
    start_socket_server(connection)

if __name__ == "__main__":
    # Daemonize the process
    if os.fork():
        sys.exit()

    os.setsid()

    if os.fork():
        sys.exit()

    sys.stdout.flush()
    sys.stderr.flush()

    with open('/dev/null', 'r') as devnull:
        os.dup2(devnull.fileno(), sys.stdin.fileno())

    with open(LOG_FILE, 'a+') as logfile:
        os.dup2(logfile.fileno(), sys.stdout.fileno())
        os.dup2(logfile.fileno(), sys.stderr.fileno())

    main()