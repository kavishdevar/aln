# ALN - AirPods like Normal
*Access AirPods' Apple-exclusive features on linux and android!*
### Check out the packet definitions at [AAP Definitions](/AAP%20Definitions.md)

## Currently supported device(s)
- AirPods Pro 2

## Implemented Features

| Feature | Linux | Android |
| --- | --- | --- |
| Ear Detection | ✅ | ✅ |
| Conversational Awareness | ✅ | ✅ |
| Setting Noise Control | ✅ | ✅ |
| Battery Level | ✅ | ✅ |
| Rename AirPods | ✅ | ✅ |
| Adjust Adaptive Audio | ❌ | ✅ |


## Linux
Check out the README file in [linux](/linux) folder for more info.

This tray app communicates with a daemon with the help of a UNIX socket. The daemon is responsible for the actual communication with the AirPods. The tray app is just a frontend for the daemon, that does ear-detection, conversational awareness, setting the noise-cancellation mode, and more.

![Tray Battery App](/linux/imgs/tray-icon-hover.png)
![Tray Noise Control Mode Menu](/linux/imgs/tray-icon-menu.png)

## Android

> Currently, there's a [bug on android](https://issuetracker.google.com/issues/371713238) that prevents this from working (psst, go upvote!)

### Workaround

- Create a folder structure like this:

```
/data/local/tmp/overlay:
upper  work
/data/local/tmp/overlay/upper:
libbluetooth_jni.so
```
- Copy the overlay and name it overlay2.
- Place `bt.sh` in `/data/adb/post-fs-data.d/`
- create a folder in /data/adb/modules/
```
/data/adb/modules/btl2capfix:
module.prop
/data/adb/modules/btl2capfix/system/lib64:
libbluetooth_jni.so
```
- Now, you have the bug in android's bluetooth stack fixed.
  
But once that's fixed, or you have fixed the issue using root, download the APK, and you're off!

I don't know how to write READMEs for android apps, because they're just that, apps. So, here are two screenshots of the app:

![AirPods Settings](/android/imgs/settings.png)
![Debugging View](/android/imgs/debug.png)

> Quick Tile to toggle Conversational Awareness and to switch Noise Control modes, and Battery Widget (App and AndroidSystemIntelligence)/Notification coming soon!!

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
