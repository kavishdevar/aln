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
start_column = 0
columns_per_view = 5
paused = False

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
    if not paused:
        plt.clf()
        plt.title('Bluetooth Data')
        end_column = start_column + columns_per_view
        for i in range(start_column, min(end_column, len(data[0]))):
            plt.plot([row[i] for row in data], label=f'Column {i}', alpha=0.6, linewidth=1.2)
        plt.xlabel('Time')
        plt.ylabel('Value')
        mplcursors.cursor(hover=True, highlight=True)

def on_key(event):
    global start_column, paused
    if event.key == ' ':
        start_column += columns_per_view
        if start_column >= len(data[0]):
            start_column = 0
    elif event.key == 'p':
        paused = not paused

fig = plt.figure()
fig.tight_layout()
fig.canvas.mpl_connect('key_press_event', on_key)
ani = FuncAnimation(fig, update, interval=0.5, repeat=True, cache_frame_data=False)

plt.show()

running.set()
sock.close()