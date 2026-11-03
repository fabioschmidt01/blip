# Blip

[Português](README.md)

A chat and a walkie-talkie for Android. No account and no phone number. The name and the identity stay on this phone.

The channel is where you write. The radio is where you talk: hold the button, speak, let go. Nearby phones pass the voice along, even with no internet. If nobody is close enough to pass it on, the voice tries the internet.

<p>
  <img src="docs/screenshots/canal.png" width="240" alt="Sample conversation on the channel">
  <img src="docs/screenshots/pessoas.png" width="240" alt="A person in range">
  <img src="docs/screenshots/mapa.png" width="240" alt="Map with the pin and the room's coverage">
</p>

The shots are a sample of the channel, the people list, and the map. The app opens in English. The language can be changed in Settings.

## Screens

- **Channel.** Text, images, and voice notes.
- **Radio.** The walkie-talkie. Hold to talk, release to send. The voice goes to everyone on the channel. When someone else talks, the phone plays it.
- **Map.** The pin stays in the center. Search takes a country, city, or neighborhood. Country, State, City, and Neighborhood change how large the area is, painted in pale green.
- **People.** Who is nearby, for a direct chat.
- **Menu.** Opens Settings only.

## Settings

- **Internet and local** looks for nearby phones and also tries the internet. **Internet only** does not look for nearby phones. Talk goes over the internet.
- Turn the link on or off, the battery note, and the diary.
- Language. English is the default. The other languages in the list can be chosen.
- Name, the identity fingerprint, and erasing everything on this phone by typing `LIMPAR`.

A place room on the map does not cross the internet. Two phones on the same point only meet when they are in range.

## What is sealed

Every packet is signed with Ed25519. A bad signature is thrown away.

A direct chat, and the audio or image in it, is sealed for one phone: a fresh X25519 key, HKDF-SHA256, and ChaCha20-Poly1305. Only that phone can open it.

A room with a password seals its text and media with that password: PBKDF2-HMAC-SHA256, 50,000 rounds, then ChaCha20-Poly1305.

The public channel, place rooms, and rooms without a password are signed, but anyone who receives them can read them. The name can be seen too. What is saved on this phone is not locked.

## Relay

Blip does not ship a public relay. To carry a conversation across the internet, run `relay/relay.py` on your own server. It is a Python process, standard library only, that stays up and keeps the queue in memory. It is not a Worker.

The app asks that relay every 2 seconds. Three calls:

- `POST /v1/hello` with `{"id","name"}`. Marks the phone as present. A phone that stays quiet for 25 seconds drops off the list.
- `POST /v1/push` with `{"id","to","packet"}`. `packet` is the Blip packet in base64. Each destination queue keeps at most 200 packets. A packet larger than 300,000 base64 characters is refused.
- `GET /v1/pull?id=` returns who is present and takes that phone's queued packets.

The relay does not open packets. Direct chats stay sealed. The public channel can be read by anyone on that path, including whoever runs the server. Place rooms are not sent. There is no password: anyone who can reach the address can send. The queue is lost when the process restarts. If the relay is down, messages wait on the phone.

A normal phone talks to `http://127.0.0.1:8791`. Waydroid talks to `http://192.168.240.1:8791`. Cleartext HTTP is allowed only for those addresses and for `localhost`.

On this computer:

```bash
python3 relay/relay.py
```

It listens on `0.0.0.0:8791`. With the phone on USB:

```bash
adb reverse tcp:8791 tcp:8791
```

For a server on the internet, copy `relay/relay.py` to the machine and keep the process running. Python 3 is enough. Nothing to install with pip.

```bash
python3 relay/relay.py
```

A systemd service keeps it up after a reboot. Change the script path.

```ini
[Unit]
Description=Blip relay
After=network.target

[Service]
ExecStart=/usr/bin/python3 /opt/blip/relay.py
Restart=on-failure
User=blip

[Install]
WantedBy=multi-user.target
```

Open port 8791 only to the proxy, not to the whole internet. In front, Caddy or nginx with HTTPS forwards `/v1/hello`, `/v1/push`, and `/v1/pull` to `127.0.0.1:8791`. The phone needs `https://`, because a public address over plain HTTP is blocked.

The address is `relayBase()` in `app/src/main/java/dev/vereda/app/mesh/InternetMeshTransport.kt`. Point it at `https://your.domain` and build the APK again. Until that change, the app keeps talking only to the local relay.

## Build

Android 12 or newer (minSdk 31), with Google Play Services. This project's Gradle needs JDK 17.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :mesh-core:test :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Each push to `main` also builds that APK on GitHub, in the **APK** action, under Artifacts, named `blip-debug`.
