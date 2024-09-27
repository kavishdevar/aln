# ALN - AirPods like Normal (Linux Only)

![Main Demo (Screenshot 2024-09-27 at 3 06 56 AM)](https://github.com/user-attachments/assets/352275c8-e143-42c3-a06a-fc3ac0c937b9)

# Get Started!
## 1. Install the required packages

```bash
sudo apt install python3 python3-pip
pip3 install pybluez
```

If you want to run it as a daemon (Refer to the [Daemonizing](#Daemonizing) section), you will need to install the `python-daemon` package.

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

## 4. Run!
You can either choose the more polished version of the script, which currently only supports fetching the battery percentage, and in-ear status (but not actually controlling the media with that information), or the more experimental standalone version of the script, which supports the following features:
- Controlling the media with the in-ear status
- Remove the device as an audio sink when the AirPods are not in your ears. 
- Try to connect with the AirPods if media is playing and the AirPods are not connected.

### Polished version
```bash
python3 main.py
```

### Experimental version
```bash
python3 standalone.py
```

## Daemonizing
If you want to run this as a deamon, you can use the `airpods_daemon.py` script. This creates a standard UNIX socket at `/tmp/airpods_daemon.sock` and listens for commands (and sends battery/in-ear info, soon!).

You can run it as follows:

```bash
python3 airpods_daemon.py
```

### Sending data to the daemon
You can send data to the daemon using the `send_data.py` script. Since it's a standard UNIX socket, you can send data to it using any programming language that supports UNIX sockets.

This package includes a demo script that sends a command to turn off the ANC. You can run it as follows:

```bash
python3 example_daemon_send.py
```

### Reading data from the daemon
Youhcan listen to the daemon's output by running the `example_daemon_read.py` script. This script listens to the UNIX socket and prints the data it receives. Currenty, it only prints the battery percentage and the in-ear status.

```bash
python3 example_daemon_read.py
```