# ALN - AirPods like Normal
*Bringing Apple-only features to Linux and Android for seamless AirPods functionality!*
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
| Rename AirPods | ✅ | ❌ |
| Adjust Adaptive Audio | ❌ | ✅ |


## Linux
Check out the README file in [linux](/linux) folder for more info.

This tray app communicates with a daemon with the help of a UNIX socket. The daemon is responsible for the actual communication with the AirPods. The tray app is just a frontend for the daemon, that does ear-detection, conversational awareness, setting the noise-cancellation mode, and more.
<div style="display: flex; flex-wrap: wrap;">
  <img src="/linux/imgs/tray-icon-hover.png" alt="Linux Tray Icon Hover" style="flex: 1; min-width: 300px; max-width: 50%;">
  <img src="/linux/imgs/tray-icon-menu.png" alt="Linux Tray Icon Menu" style="flex: 1; min-width: 300px; max-width: 50%;">
</div>

## Android

> Currently, there's a [bug on android](https://issuetracker.google.com/issues/371713238) that prevents this from working (psst, go upvote!)

But once that's fixed, or you have fixed the issue using root, download the APK, and you're off!

I don't know how to write READMEs for android apps, because they're just that, apps. So, here's a screenshot of the app:

<div style="display: flex; flex-wrap: wrap;">
  <img src="/android/imgs/settings.png" alt="Settings Screen" style="flex: 1; min-width: 300px; max-width: 50%;">
  <img src="/android/imgs/debug.png" alt="Debug Screen" style="flex: 1; min-width: 300px; max-width: 50%;">
</div>

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
