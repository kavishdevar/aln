# ALN - AirPodsLikeNormal
*Bringing AirPods' Apple-exclusive features on linux and android!*

## [XDAForums Thread](https://xdaforums.com/t/app-root-for-now-airpodslikenormal-unlock-apple-exclusive-airpods-features-on-android.4707585/)

## Tested device(s)
- AirPods Pro 2

Other devices might work too. Features like ear detection and battery should be available for any AirPods! Although the app will show unsupported features/settings. I will not be able test any other devices than the ones I already have (i.e. the AirPods Pro 2).

## Features

Check the [pinned issue](https://github.com/kavishdevar/aln/issues/20) for a list. 


## Linux — Deprecated, awaiting a rewrite!
ANY ISSUES ABOUT THE LINUX VERSION WILL BE CLOSED.
Check out the README file in [linux](/linux) folder for more info.

This tray app communicates with a daemon with the help of a UNIX socket. The daemon is responsible for the actual communication with the AirPods. The tray app is just a frontend for the daemon, that does ear-detection, conversational awareness, setting the noise-cancellation mode, and more.

![Tray Battery App](/linux/imgs/tray-icon-hover.png)
![Tray Noise Control Mode Menu](/linux/imgs/tray-icon-menu.png)

## Android

### Screenshots

| | | |
|-------------------|-------------------|-------------------|
| ![Settings 1](/android/imgs/settings-1.png) | ![Settings 2](/android/imgs/settings-2.png) | ![Debug Screen](/android/imgs/debug.png) |
| ![Battery Notification](/android/imgs/notification.png) | ![Popup](/android/imgs/popup.png) | ![QuickSetting Tile](/android/imgs/qstile.png) |
| ![Long Press Configuration](/android/imgs/long-press.png) | ![Widget](/android/imgs/widget.png) | ![Customizations](/android/imgs/customizations.png) |

### Installation

Currently, there's a [bug in the Android Bluetooth stack](https://issuetracker.google.com/issues/371713238) that prevents the app from working (upvote the issue - click the '+1' icon on the top right corner of IssueTracker). This project provides a root module which patches the bug.

> [!CAUTION]
> **This workaround requires root access.** Try at your own risk. It may break your bluetooth, and I am not responsible for any damage caused by this module.

To install it, just download the `btl2capfix.zip` from [a release](https://github.com/kavishdevar/aln/releases) and install it from your root manager app.

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

## Check out the packet definitions at [AAP Definitions](/AAP%20Definitions.md)

# License

AirPodsLikeNormal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
Copyright (C) 2024 Kavish Devar

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program over [here](/LICENSE). If not, see <https://www.gnu.org/licenses/>.
