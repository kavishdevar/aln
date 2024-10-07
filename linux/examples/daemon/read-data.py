import socket
import json
import logging
from aln.Notifications.ANC import ANCNotification
SOCKET_PATH = "/tmp/airpods_daemon.sock"

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

def read():
    """Send a command to the daemon via UNIX domain socket."""
    client_socket = None
    try:
        # Create a socket connection to the daemon
        client_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        logging.info("Connecting to daemon...")
        client_socket.connect(SOCKET_PATH)
        
        # Receive data
        while True:
            d = client_socket.recv(1024)
            if d:
                try:
                    data = json.loads(d.decode('utf-8'))
                    if isinstance(data, dict):
                        if data["type"] == "battery":
                            for b, battery_data in data.items():
                                if b != "type":  # Skip the "type" key
                                    logging.info(f"\033[1;33mReceived battery status: {b} - {battery_data['status']} - {battery_data['level']}\033[1;0m")
                        elif data["type"] == "ear_detection":
                            logging.info(f"\033[1;33mReceived ear detection status: {data['primary']} - {data['secondary']}\033[1;0m")
                        elif data["type"] == "anc":
                            logging.info(f"\033[1;33mReceived ANC status: {data['mode']}\033[1;0m")
                        elif data["type"] == "ca":
                            logging.info(f"\033[1;33mReceived Conversational Awareness status: {data['status']}\033[1;0m")
                        elif data["type"] == "unknown":
                            logging.info(f"Received data: {data['data']}")
                        else:
                            logging.info(f"Received data: {data}")
                    else:
                        logging.error("Received data is not a dictionary")
                except json.JSONDecodeError as e:
                    # logging.warning(f"Error deserializing data: {e}")
                    # logging.warning(f"raw data: {d}")
                    pass
                except KeyError as e:
                    logging.error(f"KeyError: {e} in data: {data}")
                except TypeError as e:
                    logging.error(f"TypeError: {e} in data: {data}")
            else:
                break
        
    except Exception as e:
        logging.critical(f"Error communicating with daemon: {e}")
    finally:
        if client_socket:
            client_socket.close()
            logging.warning("Socket closed")

if __name__ == "__main__":
    read()