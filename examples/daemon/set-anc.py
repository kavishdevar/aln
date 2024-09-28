import socket
import argparse
from aln import enums
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

def send_command(command):
    """Send a command to the daemon via UNIX domain socket."""
    try:
        # Create a socket connection to the daemon
        logging.info("Connecting to daemon...")
        client_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        client_socket.connect(SOCKET_PATH)
        logging.info("Connected to daemon")
        
        # Send the command
        client_socket.sendall(command)
        logging.info(f"Sent command: {command}")
        
        # Close the connection
        client_socket.close()
        logging.info("Socket closed")
    except Exception as e:
        logging.error(f"Error communicating with daemon: {e}")

def parse_arguments():
    parser = argparse.ArgumentParser(description="Set AirPods ANC mode.")
    parser.add_argument("mode", choices=["off", "on", "transparency", "adaptive", "1", "2", "3", "4"], help="ANC mode to set")
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_arguments()
    
    if args.mode == "off" or args.mode == "1":
        command = enums.SET_NOISE_CANCELLATION_OFF
    elif args.mode == "on" or args.mode == "2":
        command = enums.SET_NOISE_CANCELLATION_ON
    elif args.mode == "transparency" or args.mode == "3":
        command = enums.SET_NOISE_CANCELLATION_TRANSPARENCY
    elif args.mode == "adaptive" or args.mode == "4":
        command = enums.SET_NOISE_CANCELLATION_ADAPTIVE
    
    send_command(command)
