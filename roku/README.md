# PPV.st on Roku: app and playback bridge

The Roku channel browses PPV's catalog and plays video using Roku's native
player. For embedded sources it sends the selected iframe to a **playback
bridge running on your PC or home server**. The bridge resolves the video
playlist and relays the playlist and segments to Roku. Direct video URLs play
without the bridge.

**The bridge computer must remain on and awake while you watch.** This is a
two-device solution. The playback integration is implemented but has not been
run or verified on a Roku device.

## Setup on Windows

You need PowerShell 7, Docker Desktop running with Linux containers, and the PC
and Roku on the same home network. Docker installation instructions:
[Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/).

### 1. Start the bridge

From PowerShell in this repository:

```powershell
cd "C:\Users\360he\OneDrive\Documents\ppv-tv"
docker compose -f ./roku/bridge/compose.yml up -d --build
```

The first run downloads and builds the external resolver and its dependencies.
It listens on port **3003**. Open `http://localhost:3003` on the PC to confirm
the service responds. This reachability check alone does not verify video.

Run `ipconfig` and find the **IPv4 Address** of your active Wi-Fi or Ethernet
adapter. For example, if it is `192.168.1.50`, the address to enter on Roku is
`http://192.168.1.50:3003`. Do not enter `localhost` on Roku: that refers to the
TV itself. Allow Docker on your private network if Windows Firewall prompts.
Keep the service on your home network; router port forwarding is unnecessary.

### 2. Build the Roku ZIP

```powershell
pwsh -File ./roku/build.ps1
```

Output: `build/roku/ppvst-roku.zip`. The package contains the manifest,
configuration, BrightScript, SceneGraph components, and shared Android artwork.
No Android SDK or Gradle is needed. ZIP packaging does not compile BrightScript.
GitHub Actions also produces this ZIP as the `ppvst-roku-channel` artifact.

### 3. Install it on Roku

On the Roku remote, press **Home three times, Up twice, Right, Left, Right,
Left, Right**. Note the Roku's displayed address, enable the developer installer,
accept the agreement, and set a password. After the Roku restarts, open its
address in a PC browser. Sign in as **rokudev** with that password and upload
`build/roku/ppvst-roku.zip` in the Development Application Installer.

See [Roku's developer setup instructions](https://developer.roku.com/dev/docs/developer-setup).
This is a developer sideload ZIP. Store publication requires separate signing,
store artwork, and certification.

### 4. Connect the app to the bridge

In the Roku app, press **\***, select **Playback bridge address**, and enter the
PC's address from step 1, such as `http://192.168.1.50:3003`. Save it, select an
event, and select a source. The app remembers this address on the Roku.

Selecting an embedded source before configuring the bridge also opens this
setup screen. Saving the address then continues playback automatically.

The app resolves the selected source when you open it. **Retry** resolves it
again, rather than reusing a potentially expired video URL. Back or Cancel
abandons the pending request. Resolution has a 45-second timeout.

## If a source fails

The error dialog includes the bridge's error, or Roku's video error code, and
offers Retry, Change source, and Bridge settings. Inspect the bridge logs with:

```powershell
docker compose -f ./roku/bridge/compose.yml logs --tail 50
```

- **Cannot reach the bridge / timeout:** check the PC is awake, Docker is
  running, the saved IP address is current, and port 3003 is allowed on your
  private network. Guest Wi-Fi isolation can prevent devices communicating.
- **Upstream DNS, connection, or resolution error:** the bridge could not open
  the provider's player or resolve its stream. Try another listed source. These
  errors occur before Roku video decoding; changing the ZIP will not repair a
  provider connection failure.
- **Video error after resolution:** the stream reached the Roku player but
  could not play. Include the Roku error code and bridge logs when diagnosing
  source formatting or codec issues.

During implementation, the live catalog was reachable, but connections to its
embedded-player host could not be completed from the development machine.
Public DNS returned a record for that host. This does not establish a provider
outage, and successful live playback has not been demonstrated here.

To stop the bridge:

```powershell
docker compose -f ./roku/bridge/compose.yml down
```

## Components and configuration

- `config.json`: catalog `apiUrl` and optional default `bridgeUrl`. The address
  saved through the Roku UI takes precedence over the packaged default.
- `components/PlaybackTask.*`: posts the selected iframe to `/api/embed`, uses
  the returned **proxiedUrl**, and passes the HLS stream to Roku's Video node.
  Using the relay keeps playlist, segment, and upstream header handling on the
  same computer that resolved the source.
- `bridge/compose.yml`: runs the external
  [Lunatic16/ppv-hls-proxy](https://github.com/Lunatic16/ppv-hls-proxy) resolver at
  revision `0f91ece96dc33cfda83948264948452bc988bb54`. No third-party source or
  WASM binaries are copied into this repository. See its
  [embed endpoint](https://github.com/Lunatic16/ppv-hls-proxy/blob/0f91ece96dc33cfda83948264948452bc988bb54/src/http/route.js).

The reference [Stitch playback task](https://github.com/jeremy-albinet/Stitch-Revitalized-For-Roku/blob/main/components/Tasks/GetTwitchContent/GetTwitchContent.brs)
resolves Twitch playback tokens into HLS URLs. Its optional fMP4 proxy addresses
Twitch-specific packaging; this integration uses the PPV-specific resolver
instead. Neither service is bundled into the Roku ZIP or hosted for you.

Direct provider feeds can also supply `url` or a direct media URL in `iframe`.
Recognized extensions are `.m3u8`, `.mpd`, `.mp4`, and `.m4v`. For extensionless
URLs, add `stream_format` (`hls`, `dash`, or `mp4`) to the event or substream.
