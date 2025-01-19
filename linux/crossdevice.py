import bluetooth
import time
import threading

# Bluetooth MAC address of the target device
TARGET_MAC = "22:22:F5:BB:1C:A0"  # Replace with the actual MAC address
UUID = "1abbb9a4-10e4-4000-a75c-8953c5471342"

# Define packets
PACKETS = {
    "AIRPODS_CONNECTED": b"\x00\x01\x00\x01",
    "AIRPODS_DISCONNECTED": b"\x00\x01\x00\x00",
    "REQUEST_BATTERY_BYTES": b"\x00\x02\x00\x01",
    "REQUEST_ANC_BYTES": b"\x00\x02\x00\x02",
    "REQUEST_DISCONNECT": b"\x00\x02\x00\x00"
}

def send_packet(sock, packet_name):
    if packet_name in PACKETS:
        packet = PACKETS[packet_name]
        sock.send(packet)
        print(f"Sent packet: {packet_name}")
    else:
        print(f"Packet {packet_name} not defined.")

def listen_for_packets(sock):
    try:
        while True:
            data = sock.recv(1024)
            if data:
                print(f"Received packet: {data}")
    except Exception as e:
        print(f"Error receiving data: {e}")

def main():
    # Discover services to find the channel using the UUID
    services = bluetooth.find_service(address=TARGET_MAC, uuid=UUID)
    if len(services) == 0:
        print(f"Could not find services for UUID {UUID}")
        return

    # Use the first service found
    service = services[0]
    port = service["port"]
    name = service["name"]
    host = service["host"]

    print(f"Connecting to \"{name}\" on {host}, port {port}")

    # Create a Bluetooth socket
    sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    sock.connect((host, port))
    print(f"Connected to {TARGET_MAC} on port {port}")

    # Start listening for packets in a background thread
    listener_thread = threading.Thread(target=listen_for_packets, args=(sock,))
    listener_thread.daemon = True
    listener_thread.start()

    try:
        while True:
            packet_name = input("Enter packet name to send (or 'exit' to quit): ")
            if packet_name.lower() == "exit":
                break
            send_packet(sock, packet_name)
            time.sleep(1)
    except Exception as e:
        print(f"Error: {e}")
    finally:
        sock.close()
        print("Connection closed.")

if __name__ == "__main__":
    main()