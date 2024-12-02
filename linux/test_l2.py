import bluetooth
address="28:2D:7F:C2:05:5B"
try:
    sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)
    sock.connect((address, 0x1001))
    sock.send(b"\x00\x00\x04\x00\x01\x00\x02\x00\x00\x00\x00\x00\x00\x00\x00\x00")
except bluetooth.btcommon.BluetoothError as e:
    print(f"Error: {e}")
