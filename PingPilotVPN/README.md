# Ping Pilot VPN

A complete Android Studio demo project built with Kotlin, Jetpack Compose, and Android `VpnService`.

## What the app does

- Requests Android's official VPN permission.
- Starts and stops a foreground `VpnService`.
- Creates a real local TUN interface.
- Routes only the reserved benchmark range `198.18.0.0/15`, so ordinary Internet and game traffic remain on the normal network.
- Measures TCP connection latency every two seconds.
- Shows a rolling latency chart and a configurable warning threshold.
- Includes a GitHub Actions workflow that builds a debug APK.

## Important limitation

This is a safe local VPN demonstration, not a relay VPN. It has no remote VPN server and does not forward packets. It therefore does not lower Free Fire ping, lock ping to 100–200 ms, alter packets, or create fake lag. A real production VPN requires a trusted remote server and a complete protocol implementation such as WireGuard or IKEv2.

## Build in Android Studio

1. Install a current Android Studio version with Android SDK 36 and JDK 17.
2. Extract the ZIP and open the `PingPilotVPN` folder.
3. Let Android Studio sync dependencies.
4. Select the `app` configuration and press **Run**, or use **Build > Build APK(s)**.

The included `gradlew` and `gradlew.bat` are lightweight bootstrap scripts. They download the official Gradle 8.13 distribution on first use.

### Command line

Linux/macOS:

```bash
./gradlew :app:assembleDebug
```

Windows:

```bat
gradlew.bat :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build with GitHub Actions

1. Upload all project files to a GitHub repository.
2. Open the **Actions** tab.
3. Run **Build Android APK**, or push to `main`/`master`.
4. Download the `PingPilotVPN-debug-apk` artifact from the completed workflow.

## Project structure

```text
app/src/main/java/com/khanhan/pingpilot/
├── MainActivity.kt
├── network/PingMonitor.kt
├── ui/PingPilotApp.kt
├── ui/theme/Theme.kt
└── vpn/
    ├── LocalVpnService.kt
    └── VpnStatusStore.kt
```

## Notes for a real VPN product

To turn this into a real VPN client, replace the demo route/tunnel logic with an audited VPN protocol library and your own secure server infrastructure. Do not attempt to forward raw TCP packets with a simple Kotlin loop; TCP requires a proper userspace stack or a mature tun-to-socket implementation.
