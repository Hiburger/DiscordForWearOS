# DiscordWear

[![Build DiscordWear beta](https://github.com/Hiburger/DiscordForWearOS/actions/workflows/build_Discord_Wear.yml/badge.svg)](https://github.com/Hiburger/DiscordForWearOS/actions/workflows/build_Discord_Wear.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![install](https://img.shields.io/badge/only_installable_by_smelly_nerds-yes-red)

Hi ! This is an unofficial, open-source Discord client for your Wear OS watch built and tested for **Pixel Watch** and **Galaxy Watch** (but should run on any modern WearOS).

This is a maintained fork of [Zaffox/Discord-WearOS](https://github.com/Zaffox/Discord-WearOS) on GitHub, kept alive after the original project went inactive. The assets, screenshots, and most of the code are from that repo.

| Home | Servers | Mentions |
|:---:|:---:|:---:|
| ![Home](Images/1.png) | ![Servers](Images/2.png) | ![Mentions](Images/4.png) |

## What you get

- Direct messages and servers
- Channel browsing with categories, pin/hide options
- Chat with emoji, stickers, GIFs and reactions
- Voice message recording
- QR-code login (no token copy-pasting)
- User profile screen and picture upload
- In-app updates from GitHub Releases

All of this from your wrist !

## Easy install

### If you are doing a fresh install

1. Grab the latest `DiscordWear.apk` from the [Releases](https://github.com/Hiburger/DiscordForWearOS/releases) page
2. Transfer it to your watch, either with `adb install DiscordWear.apk` (ADB wireless debugging must be on. [How to turn this on]([URL](https://chk.me/5zu63Fh)) ), or with a tool like *Wear Installer*
3. If installing directly on the watch, allow installs from unknown sources when prompted

### Things you should know !

> [!IMPORTANT]
> If you have the original app installed, you **must uninstall it first** !  
> This fork is signed with a different key, so installing over the original fails (with the following unclear error: "App not installed"). You will need to log in again after installing.  
> This is a 3rd party client, and the discord people don't like that. Using this app goes against their ToS and could lead to a ban (even if the risk is pretty low). I am not responsible for it if your account gets banned.

## Login

The app logs in via Discord's device-linking flow:

1. The watch displays a QR code
2. Open the **Discord app on your phone** and go to **Profile → Scan QR Code**
3. Scan the code on the watch and confirm the login prompt

The app also offers two fallbacks: pasting your token directly on the watch, or letting the watch run a tiny local web server (same Wi-Fi network) where you can submit the token from a phone/PC browser.

Your token is stored locally and exclusively on the watch

## Because our world isn’t all sunshine and rainbows

As you already know, this is **not an official Discord client**. It uses Discord's private user API, which is against Discord's Terms of Service and may result in your account being **suspended or banned**. 
Use at your own risk.

## Building from source

Requirements: JDK 17, Android SDK (API 36).

```bash
git clone https://github.com/Hiburger/DiscordForWearOS.git
cd DiscordForWearOS
./gradlew app:assembleDebug   # makes a debug build; no keystore needed
```

Release builds need a keystore, provided via environment variables (see `app/build.gradle`)

## Credits :)

- Original project: [Zaffox/Discord-WearOS](https://github.com/Zaffox/Discord-WearOS) // huge thanks to [@Zaffox](https://github.com/Zaffox) for creating it
- Licensed under [GPL-3.0](LICENSE) (like the original project)
