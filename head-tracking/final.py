import bluetooth
import threading
import time

sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)

bt_addr = "28:2D:7F:C2:05:5B"
psm = 0x1001

sock.connect((bt_addr, psm))
running = threading.Event()

data = []

def hex_to_base10(hex_string):
    hex_values = hex_string.split()
    base10_values = [int(hex_value, 16) for hex_value in hex_values]
    return base10_values

def listen():
    global running, data
    while not running.is_set():
        res = sock.recv(1024)
        if len(res.hex()) == 162:
            hex_string = " ".join(res.hex()[i:i+2].upper() for i in range(0, len(res.hex()), 2))
            base10_values = hex_to_base10(hex_string)
            data.append(base10_values)

t = threading.Thread(target=listen)
t.start()

try:
    byts = bytes(int(b, 16) for b in "00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00".split(" "))
    sock.send(byts)
    byts = bytes(int(b, 16) for b in "04 00 04 00 0F 00 FF FF FE FF".split(" "))
    sock.send(byts)
    byts = bytes(int(b, 16) for b in "04 00 04 00 17 00 00 00 10 00 11 00 08 7C 10 02 42 0B 08 4E 10 02 1A 05 01 40 9C 00 00".split(" "))
    sock.send(byts)
except:
    ...

start_time = time.time()
timeout = 10  # seconds

vertical_count = 0
horizontal_count = 0
horizontal_fluctuation_count = 0
initial_131_sequence = True

while time.time() - start_time < timeout:
    if len(data) > 0:
        column_34_data = [row[34] for row in data]

        for i in range(len(column_34_data)):
            print(column_34_data[i])
            if initial_131_sequence:
                if column_34_data[i] == 131:
                    if i >= 4:
                        continue
                else:
                    initial_131_sequence = False

            if column_34_data[i] == 131 and not initial_131_sequence:
                vertical_count += 1
                horizontal_count = 0
                if vertical_count >= 45:
                    print("vertical")
                    running.set()
                    sock.close()
                    exit()
            elif column_34_data[i] == 3:
                horizontal_count += 1
                vertical_count = 0
                if horizontal_count >= 40:
                    print("horizontal")
                    running.set()
                    sock.close()
                    exit()
            else:
                vertical_count = 0
                horizontal_count = 0

        # Check for horizontal nodding (fluctuation between 3 and 67 or higher)
        fluctuation_window = 40
        if len(column_34_data) >= fluctuation_window:
            fluctuation_count = 0
            for i in range(len(column_34_data) - fluctuation_window + 1):
                segment = column_34_data[i:i+fluctuation_window]
                if any(value == 3 for value in segment) and any(value >= 67 for value in segment):
                    count_67 = segment.count(67)
                    if count_67 <= 10:
                        fluctuation_count += 1
                        if fluctuation_count >= 10:
                            print("horizontal")
                            running.set()
                            sock.close()
                            exit()

print("unknown")
running.set()
sock.close()