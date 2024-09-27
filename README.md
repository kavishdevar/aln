# ALN - AirPods like Normal (Linux Only)

![Main Demo (Screenshot 2024-09-27 at 3 06 56 AM)](https://github.com/user-attachments/assets/352275c8-e143-42c3-a06a-fc3ac0c937b9)

# Get Started!
## 1. Install the required packages

```bash
sudo apt install python3 python3-pip
pip3 install pybluez
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
