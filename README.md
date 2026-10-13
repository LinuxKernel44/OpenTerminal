# OpenTerminal

A full terminal emulator for Android that runs its shell with **adb-level (shell, uid 2000) privileges** through [Shizuku](https://shizuku.rikka.app/), wrapped in a **liquid glass** UI built with [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

## Features

- Real PTY shell (`/system/bin/sh`) started by a Shizuku user service: vim, nano, htop, less and other full-screen apps work
- xterm-256color / truecolor emulation, powered by the Termux terminal emulator
- Multiple tabs (sessions)
- Special keys bar: Esc, Tab, sticky Ctrl/Alt, arrows, Home/End, PgUp/PgDn, common symbols, paste
- Command history and autocompletion for common `pm`, `am`, `dumpsys`, `settings`, `input`... commands
- Liquid glass theming: blur, lens refraction and opacity sliders, 4 app themes (Aurora default, Midnight, AMOLED, System) and terminal color schemes
- Guided onboarding when Shizuku is missing, stopped or not authorized
- Pinch to zoom, long-press to select and copy

## Requirements

- Android 8.0+ (API 26). Lens refraction effects need Android 13+; older versions fall back to blur only.
- [Shizuku](https://shizuku.rikka.app/) installed and running (start it with Wireless debugging or adb).

## Download

Signed release APKs are on the [Releases](https://github.com/LinuxKernel44/OpenTerminal/releases) page. Release certificate SHA-256:
`CC:13:86:96:6C:35:6C:14:4A:EA:E2:EC:0F:C0:F1:90:6C:76:57:8A:F5:B1:E7:FB:1C:A0:69:C7:09:DD:A9:26`

## Build

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17, Android SDK platform 37, NDK 28 and CMake.

## How it works

`ShellUserService` runs inside a Shizuku user service process (uid 2000). A small JNI library opens a PTY (`/dev/ptmx`), forks the shell and hands the master file descriptor back to the app over Binder. The app feeds the bytes into a Termux `TerminalEmulator` and renders it in a custom view on top of liquid glass panels.

## License

GPL-3.0. Includes code from [Termux](https://github.com/termux/termux-app) (`terminal-emulator`, GPLv3).

Release builds are signed with a keystore read from `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or the `OPENTERMINAL_KEYSTORE`, `OPENTERMINAL_KEYSTORE_PASSWORD`, `OPENTERMINAL_KEY_ALIAS`, `OPENTERMINAL_KEY_PASSWORD` environment variables. Without them `assembleRelease` produces an unsigned APK.
