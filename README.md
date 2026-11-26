# ClashPoint Nexus for Android

The phone half of [ClashPoint Nexus](https://github.com/Eoinknd16/ClashPointNexus): a sci-fi home screen (launcher), a full Stremio-addon media player, and a remote for the living-room PC running Nexus.

## What it does

- **Launcher**: optional Android home screen with a command deck of tiles, clock/status HUD, pinned-app dock and a searchable app drawer (swipe up). Long-press an app to pin, hide, view info or uninstall.
- **TV**: the same Stremio addon protocol the PC uses. Cinemeta catalogs and search, your own addons' catalogs, details with seasons and episodes, sources list, continue watching and a library.
- **Player**: Media3/ExoPlayer, so MKV, HEVC and most audio formats play natively with no transcoding. Includes OpenSubtitles and subtitle addons, source switching, automatic fallback to the next source on failure, and resume.
- **Living-room PC link**:
  - pair with Nexus on the PC
  - D-pad remote and screen jumps
  - wake-on-LAN
  - **send to TV**: hand whatever you're watching to the PC, which resumes at the same point
  - import the PC's addon list
- **Torrent streams** (infoHash sources such as Torrentio) play through the PC: Nexus there runs the Stremio torrent engine and proxies it to the phone. Direct-URL streams play on the phone by themselves.

## Pairing with the PC

1. On the PC, update Nexus to 0.3.1+ and open **Settings > Phone Link > Show Pairing Code**. Allow Nexus through Windows Firewall on private networks if asked.
2. On the phone, go to **Settings > Living room PC** and enter the address shown on the PC (port `11480`) and the 6-digit code.
3. Over [Tailscale](https://tailscale.com), use the PC's Tailscale IP. Everything works except wake-on-LAN, which needs the same local network.

Wake-on-LAN also needs it enabled in the PC's BIOS and network adapter settings.

## Building

There's no local Android toolchain needed. GitHub Actions builds a debug APK on every push (grab it from the run's **Artifacts**). Every push to `main` also publishes a release APK on the **Releases** page (`v0.1.<build number>`), so the newest build can be installed straight from a phone browser.

Locally, open the folder in Android Studio, or run:

```
./gradlew assembleDebug
```

### Release signing (optional)

Add these repo secrets to sign tagged releases with your own key. Without them, release APKs are signed with the debug key, which is fine for sideloading:

| Secret | Value |
| --- | --- |
| `NEXUS_KEYSTORE_B64` | `base64 -w0 your.keystore` |
| `NEXUS_KEYSTORE_PASSWORD` | keystore password |
| `NEXUS_KEY_ALIAS` | key alias |
| `NEXUS_KEY_PASSWORD` | key password |

Keep the same key from the first build you install onward. Android won't update an app over a build signed with a different key.

## Layout

```
app/src/main/java/com/eoinknd16/nexus/
  core/       HTTP client, DataStore-backed JSON stores
  media/      Stremio protocol (ported from the PC's src/main/stremio), repository, playback session
  link/       PC remote API client, wake-on-LAN
  launcher/   installed apps via LauncherApps
  ui/         Compose screens: home, apps, tv (browse/detail/player), pc remote, settings
  ui/fx/      the sci-fi layer: AGSL shader backdrop, holo panels, tiles
```
