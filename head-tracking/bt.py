import bluetooth

sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)

bt_addr = "28:2D:7F:C2:05:5B" # sys.argv[1]
psm = 0x1001  # AAP

sock.connect((bt_addr, psm))

init_packets = ["00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00",
                "04 00 04 00 4d 00 ff 00 00 00 00 00 00 00",
                "04 00 04 00 0F 00 FF FF FE FF",
                "04 00 04 00 17 00 00 00 10 00 10 00 08 A0 02 42 0B 08 0E 10 02 1A 05 04 01 00 00 00",
                "04 00 04 00 17 00 00 00 10 00 10 00 08 A1 02 42 0B 08 0E 10 02 1A 05 01 40 9C 00 00"
]           

def listen():
    while True:
        res = sock.recv(1024)
        print(res.hex())
        if res.hex() == "0400040006000000":
            print('hiiiiiiiiii')
        
import threading
threading.Thread(target=listen).start()

for packet in init_packets:
    sock.send(bytes.fromhex(packet))
