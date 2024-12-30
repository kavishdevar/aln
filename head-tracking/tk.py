# AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
#
# Copyright (C) 2024 Kavish Devar
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.

import matplotlib.pyplot as plt
import mplcursors
import bluetooth
import threading
import time
from matplotlib.animation import FuncAnimation
import tkinter as tk
from tkinter import ttk

sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)

bt_addr = "28:2D:7F:C2:05:5B"
psm = 0x1001

sock.connect((bt_addr, psm))
running = threading.Event()

data = []
selected_columns = list(range(80))

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
    for i in range(len(data[0])):
        if i in selected_columns:
            plt.plot([row[i] for row in data], label=f'Column {i}', alpha=1, linewidth=1.2)
    plt.xlabel('Time')
    plt.ylabel('Value')
    mplcursors.cursor(hover=True, highlight=True)

def on_checkbox_change():
    global selected_columns
    selected_columns = [i for i, var in enumerate(checkbox_vars) if var.get()]

def create_checkbox_window():
    root = tk.Tk()
    root.title("Select Columns")
    frame = ttk.Frame(root)
    frame.pack(fill=tk.BOTH, expand=True)
    canvas = tk.Canvas(frame)
    scrollbar = ttk.Scrollbar(frame, orient="vertical", command=canvas.yview)
    scrollable_frame = ttk.Frame(canvas)

    scrollable_frame.bind(
        "<Configure>",
        lambda e: canvas.configure(
            scrollregion=canvas.bbox("all")
        )
    )

    canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
    canvas.configure(yscrollcommand=scrollbar.set)

    global checkbox_vars
    checkbox_vars = []
    for i in range(80):
        var = tk.BooleanVar(value=True)
        checkbox = ttk.Checkbutton(scrollable_frame, text=f"Column {i}", variable=var, command=on_checkbox_change)
        checkbox.pack(anchor='w')
        checkbox_vars.append(var)

    canvas.pack(side="left", fill=tk.BOTH, expand=True)
    scrollbar.pack(side="right", fill="y")

    root.mainloop()

checkbox_thread = threading.Thread(target=create_checkbox_window)
checkbox_thread.start()

fig = plt.figure()
fig.tight_layout()
ani = FuncAnimation(fig, update, interval=500, repeat=True, cache_frame_data=False)

plt.show()

running.set()
sock.close()