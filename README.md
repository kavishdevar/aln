# ALN - AirPods like Normal (Linux Only)

![Main Demo (Screenshot 2024-09-27 at 3 06 56 AM)](https://github.com/user-attachments/assets/352275c8-e143-42c3-a06a-fc3ac0c937b9)

# Get Started!

## 1. Install the required packages

```bash
sudo apt install python3 python3-pip
pip3 install pybluez
```

If you want to run it as a daemon (Refer to the [Daemon Version](#as-a-daemon-using-a-unix-socket) section), you will need to install the `python-daemon` package.

```bash
pip3 install python-daemon
```

## 2. Clone the repository

```bash
git clone https://github.com/kavishdevar/aln.git
cd aln
```

## 3. Preprare
Pair your AirPods with your machine before running this script!
:warning: **Note:** DO NOT FORGET TO EDIT THE `AIRPODS_MAC` VARIABLE IN `main.py`/`standalone.py` WITH YOUR AIRPODS MAC ADDRESS!

# Versions

## Non-Daemon based
This version is the most polished version of the script. It can do the following:
- fetch the battery percentage,
- fetch in-ear status (but not actually controlling the media with that information).
- control ANC modes
```bash
python3 examples/logger-and-anc.py
```

## As a daemon (using a UNIX socket)
If you want to run a deamon for multiple programs to read/write airpods data, you can use the `airpods_daemon.py` script.
- This creates a standard UNIX socket at `/tmp/airpods_daemon.sock` and listens for commands
- and sends battery/in-ear info
You can run it as follows:

```bash
python3 airpods_daemon.py
```

## Interacting with the daemon

- Sending data to the daemon
You can send data to the daemon using the `set-anc.py` script. Since it's a standard UNIX socket, you can send data to it using any programming language that supports UNIX sockets.

This package includes a demo script that sends a command to turn off the ANC. You can run it as follows:

```bash
python3 examples/daemon/set-anc.py
```

- Reading data from the daemon
You can listen to the daemon's output by running the `example_daemon_read.py` script. This script listens to the UNIX socket and prints the data it receives. Currenty, it only prints the battery percentage and the in-ear status.

```bash
python3 examples/daemon/example_daemon_read.py
```

- Controlling the media with the in-ear status (and get battery status)
This script is basically the standalone script, but interacts with the UNIX socket created by the daemon instead. It can control the media with the in-ear status and remove the device as an audio sink when the AirPods are not in your ears.

```bash
python3 examples/daemon/ear-detection.py
```

## Standalone version (without module dependency, mainly for testing, and reverse engineering purposes)
- Controlling the media with the in-ear status.
- Remove the device as an audio sink when the AirPods are not in your ears. 
- Try to connect with the AirPods if media is playing and the AirPods are not connected.
- Control ANC modes.
  
```bash
python3 examples/standalone.py
```