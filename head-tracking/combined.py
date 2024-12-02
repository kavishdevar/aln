import matplotlib.pyplot as plt
import mplcursors
import bluetooth
import threading
import time
from matplotlib.animation import FuncAnimation

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
    
def update(frame):
    plt.clf()
    plt.title('Bluetooth Data')
    # columns = [33, 69, 44, 46, 48, 67, 69, 71, 78]
    columns = [34]
    # columns_to_remove = [0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 13, 14, 15, 16, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 29, 32, 35, 36, 37, 38, 43, 45, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 59, 46, 61, 62, 63, 64, 65, 66, 67, 68, 70,71, 73, 74, 75, 77, 78]
    # columns_to_remove = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 35, 36, 37, 38, 39, 40, 41, 42, 43, 45, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 46, 60, 61, 62, 63, 64, 65, 66, 67, 68, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80]
    for i in range(len(data[0])):
        if i in columns:
            plt.plot([row[i] for row in data], label=f'Column {i}', alpha=1, linewidth=1.2)
    # plt.legend()
    plt.xlabel('Time')
    plt.ylabel('Value')
    mplcursors.cursor(hover=True, highlight=True)

fig = plt.figure()
ani = FuncAnimation(fig, update, interval=0.5, repeat=True, cache_frame_data=False)

plt.show()

running.set()
sock.close()