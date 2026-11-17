# PPV.st TV

Android TV app and Roku channel for PPV events and 24/7 streams, with sports
categories, event status, and source selection.

- **Android TV:** standalone app with native video and embedded web playback.
- **Roku TV:** embedded streams use a Docker playback bridge on your PC or home
  server. **Roku playback has not yet been verified end to end.**

Run the commands below from the repository root.

## Android TV

### Install

In GitHub, open **Actions → Build Android TV APK**, select a successful run,
and download **ppvst-tv-debug-apk**. Extract the download and sideload the APK
onto your Android TV.

### Build locally

Requires JDK 21, Gradle 9.3.1, and the project's Android SDK installed/configured.

```sh
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Unit tests: `gradle :app:testDebugUnitTest`

## Roku TV

Requires **PowerShell 7**, **Docker Desktop running with Linux containers**, and
a Roku on the same home network as the Docker host. **The host must stay on and
awake while you watch.** No public internet exposure or router port forwarding
is needed.

### 1. Start the playback bridge

```sh
docker compose -f ./roku/bridge/compose.yml up -d --build
```

The first run downloads and builds the external
[PPV resolver](https://github.com/Lunatic16/ppv-hls-proxy). It resolves embedded
sources and relays video through port **3003**.

### 2. Build and install the Roku app

```powershell
pwsh -File ./roku/build.ps1
```

ZIP: `build/roku/ppvst-roku.zip`

Alternatively, download **ppvst-roku-channel** from a successful
**Actions → Build Roku TV Channel** run and extract the download to get this ZIP.

[Enable Roku developer mode](https://developer.roku.com/dev/docs/developer-setup),
open the Roku's displayed address in your computer's browser, log in as
**rokudev** with the password you created, and upload the ZIP.

### 3. Point Roku at the bridge

Find the Docker host's LAN IP address (`ipconfig` on Windows). In the Roku app,
press **\* → Playback bridge address** and enter, for example:

```text
http://192.168.1.50:3003
```

Save, select an event, and choose a source. Use your host's actual IP address;
`localhost` on Roku refers to the TV itself.

## Configuration

| Setting | Where to change it | Rebuild required? |
| --- | --- | --- |
| Roku playback bridge address | Roku app: **\* → Playback bridge address** | No; saved on the Roku |
| Roku default bridge address | `bridgeUrl` in [roku/config.json](roku/config.json) | Yes; a saved address takes precedence |
| Roku catalog API | `apiUrl` in [roku/config.json](roku/config.json) | Yes |

Event listings and individual stream URLs are fetched at runtime.

For bridge errors, check its logs:

```sh
docker compose -f ./roku/bridge/compose.yml logs --tail 50
```

See [Roku setup and troubleshooting](roku/README.md) and
[API investigation and verification status](API_VERIFICATION.md).
