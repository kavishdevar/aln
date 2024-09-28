import socket
import argparse
from aln import enums
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
