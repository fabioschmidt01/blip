# Blip

[Português](README.md)

A pocket radio for Android. Talk to people nearby, with or without internet.

The phone announces a name, finds other radios around it, and delivers text, audio, and images with no account and no phone number. The identity stays on the device.

<p>
  <img src="docs/screenshots/canal.png" width="240" alt="Sample conversation on the local channel">
  <img src="docs/screenshots/pessoas.png" width="240" alt="A person in radio range">
  <img src="docs/screenshots/mapa.png" width="240" alt="Map with the pin and the room's coverage">
</p>

These shots are a sample from one phone. The channel shows a conversation between the radio name and another set. The map marks a place and paints, in pale green, the area that room covers.

## What it does

- A local channel, encrypted direct messages, and rooms with an optional password.
- Voice notes with a waveform, and images in the chat.
- A map to pick a place. The pin stays in the center. Search takes a country, city, or neighborhood. Country, State, City, and Neighborhood change how large the area is.
- Clear one conversation, or wipe everything on the device by typing LIMPAR under Radio.
- History stays on the phone. The radio keeps running with the screen off, as a foreground service.

Two people in the same place, with Blip open, hear each other over the nearby radio. A place room does not cross the internet yet: two phones that pick the same point only meet when they are in range.

## Build

Android 12 or newer (minSdk 31), with Google Play Services. This project's Gradle needs JDK 17.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :mesh-core:test :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
