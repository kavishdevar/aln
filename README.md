# ALN - AirPods like Normal
*Access AirPods' Apple-exclusive features on linux and android!*
### Check out the packet definitions at [AAP Definitions](/AAP%20Definitions.md)

## Tested device(s)
- AirPods Pro 2

Other devices might work too! Features like ear detection and battery should be available for any AirPods! Although the app will show unsupported features/settings! I will not be able test any other devices than the ones I already have (i.e. the AirPods Pro 2).

## Implemented Features

| Feature | Linux | Android |
| --- | --- | --- |
| Ear Detection | ✅ | ✅ |
| Battery Levels | ✅ | ✅ |
| Conversational Awareness | ✅ | ✅ |
| Changing Noise Control modes | ✅ | ✅ |
| Configure AirPods' Settings | ❌ | ✅ |
| Popup | ❌ | ✅ |

> [!NOTE]
> This just includes features that are already implemented for at least one of the platforms. There is no list for any planned features.

## Linux
Check out the README file in [linux](/linux) folder for more info.

This tray app communicates with a daemon with the help of a UNIX socket. The daemon is responsible for the actual communication with the AirPods. The tray app is just a frontend for the daemon, that does ear-detection, conversational awareness, setting the noise-cancellation mode, and more.

![Tray Battery App](/linux/imgs/tray-icon-hover.png)
![Tray Noise Control Mode Menu](/linux/imgs/tray-icon-menu.png)

## Android

Currently, there's a [bug in the Android Bluetooth stack](https://issuetracker.google.com/issues/371713238) that prevents the app from working (upvote the issue - click the '+1' icon on the top right corner of IssueTracker). This repository provides a workaround for the bug, specifically tested on Android 14 and Android 15 (stock versions). 

> [!CAUTION]
> **This workaround requires root access** to implement and it might not work on other android OEM skins. Try at your own risk!!

### Workaround

#### Step 1: Download the Required Files
- Go to the [Releases](https://github.com/kavishdevar/aln/releases) section.
- Download the required files depending upon your Android version - `bt.sh`, `module.prop`, and `libbluetooth_jni-a14.so` (for android 14) or `libbluetooth_jni-a15.so` (for android 15).

#### Step 2: Set Up the Directory Structure

- Use a file manager with root access (eg. Solid FIle Explorer or MT manager) or a shell (using adb, or a terminal app like Termux) and create a folder structure like this (`upper` and `work` are also folders):

```
/data/local/tmp/overlay:
upper
work
```
- Rename the appropriate file to `libbluetooth_jni.so` and place it in the `upper` folder
```
/data/local/tmp/overlay/upper:
libbluetooth_jni.so
```
- Create a duplicate of the overlay folder inside tmp  and name it overlay2.

#### Step 3: Add the Boot Script

- Place the `bt.sh` script in `/data/adb/post-fs-data.d/`.
- This script ensures the fix is applied during system startup.

#### Step 4: Create a Magisk Module
- Create a `btl2capfix` folder in `/data/adb/modules/` and copy the `module.prop`into the folder.
```
/data/adb/modules/btl2capfix:
module.prop
```
- Create `system/lib64` under `btl2capfix` and copy the `libbluetooth_jni.so` into `lib64`
```
/data/adb/modules/btl2capfix/system/lib64:
libbluetooth_jni.so
```

#### Step 5: Verify the Fix
- Reboot your device.
- The Bluetooth bug should now be resolved.
  
Once the issue is resolved by Google developers or you've addressed it through the above root-based method, download and install the `app-release.apk` from the releases, and you're good to go!

### Features

#### Renaming the Airpods
When you rename the Airpods using the app, you'll need to re-pair it with your phone. Currently, user-level apps cannot directly rename a Bluetooth device. After re-pairing, your phone will display the updated name!

#### Noise Control Modes

- Active Noise Cancellation (ANC): Blocks external sounds using microphones and advanced algorithms for an immersive audio experience; ideal for noisy environments.
- Transparency Mode: Allows external sounds to blend with audio for situational awareness; best for environments where you need to stay alert.
- Off Mode: Disables noise control for a natural listening experience, conserving battery in quiet settings.
- Adaptive Transparency: Dynamically reduces sudden loud noises while maintaining environmental awareness, adjusting seamlessly to fluctuating noise levels.

> [!IMPORTANT]
> Due to recent AirPods' firmware upgrades, you must enable `Off listening mode` to switch to `Off`. This is because in this mode, louds sounds are not reduced!

#### Conversational Awareness

Automatically lowers audio volume and enhances voices when you start speaking, making it easier to engage in conversations without removing your AirPods.

#### Automatic Ear Detection

Recognizes when the AirPods are in your ears to automatically play or pause audio and adjust functionality accordingly.

### Screenshots of the app

| | | |
|-------------------|-------------------|-------------------|
| ![Settings 1](/android/imgs/settings-1.png) | ![Settings 2](/android/imgs/settings-2.png) | ![Debug Screen](/android/imgs/debug.png) |
| ![Battery Notification](/android/imgs/notification.png) | ![Popup](/android/imgs/popup.png) | ![QuickSetting Tile](/android/imgs/qstile.png) |

# License

AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
Copyright (C) 2024 Kavish Devar

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program over [here](/LICENSE). If not, see <https://www.gnu.org/licenses/>.
