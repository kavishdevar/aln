from aln import Connection
from aln import enums
import logging
import threading
import time
import sys
import shutil

AIRPODS_MAC = '28:2D:7F:C2:05:5B'

class CustomFormatter(logging.Formatter):
    def format(self, record):
        # Format the log message with spaces around colons without altering the original message
        formatted_message = record.getMessage().replace(':', ': ')
        record.message = formatted_message
        return super().format(record)

class ConsoleHandler(logging.StreamHandler):
    def __init__(self, stream=None):
        super().__init__(stream)
        self.terminator = '\n'
        self.log_lines = []

    def emit(self, record):
        try:
            msg = self.format(record)
            self.log_lines.append(msg)
            self.display_logs()
        except Exception:
            self.handleError(record)

    def display_logs(self):
        sys.stdout.write('\033[H\033[J')  # Clear the screen
        terminal_height, _ = shutil.get_terminal_size()
        log_display_lines = self.log_lines[-(terminal_height - 5):]  # Display the last terminal_height - 5 log lines
        empty_lines = terminal_height - 5 - len(log_display_lines)
        
        for _ in range(empty_lines):
            sys.stdout.write('\n')  # Fill empty space with new lines

        for line in log_display_lines:
            sys.stdout.write(line + self.terminator)
        
        sys.stdout.write('1: ANC Off\n')
        sys.stdout.write('2: Transparency\n')
        sys.stdout.write('3: Adaptive Transparency\n')
        sys.stdout.write('4: ANC On\n')
        sys.stdout.write('Select ANC Mode: ')
        sys.stdout.flush()

def input_thread(connection: Connection):
    while True:
        anc_mode = input()
        if anc_mode == '1':
            connection.send(enums.NOISE_CANCELLATION_OFF)
            logging.info('ANC Off')
        elif anc_mode == '2':
            connection.send(enums.NOISE_CANCELLATION_TRANSPARENCY)
            logging.info('Transparency On')
        elif anc_mode == '3':
            connection.send(enums.NOISE_CANCELLATION_ADAPTIVE)
            logging.info('Adaptive Transparency On')
        elif anc_mode == '4':
            connection.send(enums.NOISE_CANCELLATION_ON)
            logging.info('ANC On')
        else:
            logging.error('Invalid ANC Mode')

def main():
    # Set up logging
    handler = ConsoleHandler()

    logging.addLevelName(logging.DEBUG, "\033[1;34m%s\033[1;0m" % logging.getLevelName(logging.DEBUG))
    logging.addLevelName(logging.INFO, "\033[1;32m%s\033[1;0m" % logging.getLevelName(logging.INFO))
    logging.addLevelName(logging.WARNING, "\033[1;33m%s\033[1;0m" % logging.getLevelName(logging.WARNING))
    logging.addLevelName(logging.ERROR, "\033[1;31m%s\033[1;0m" % logging.getLevelName(logging.ERROR))
    logging.addLevelName(logging.CRITICAL, "\033[1;41m%s\033[1;0m" % logging.getLevelName(logging.CRITICAL))

    formatter = CustomFormatter('%(asctime)s - %(levelname)s - %(name)s - %(message)s')
    handler.setFormatter(formatter)
    logging.basicConfig(level=logging.DEBUG, handlers=[handler])

    connection = Connection(AIRPODS_MAC)
    connection.connect()
    logging.info('Sending Handshake')
    connection.send(enums.HANDSHAKE)
    logging.info('Initializing Notifications')
    connection.initialize_notifications()
    logging.info('Initialized Notifications')

    # Start the input thread
    thread = threading.Thread(target=input_thread, args=(connection,))
    thread.daemon = True
    thread.start()

    try:
        # Keep the main thread alive to handle logging
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logging.info('Program interrupted. Exiting...')
        connection.disconnect()  # Ensure the connection is properly closed

if __name__ == "__main__":
    main()