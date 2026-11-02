# Blip

Nome: **Blip**. Rádio de bolso para Android, para qualquer momento em que as pessoas estão perto. O rádio local vem primeiro. A internet fica para um marco seguinte.

Estado: marco 1 em construção. Texto no canal local, mensagem direta cifrada, descoberta por Nearby Connections, histórico no aparelho.

## Decisões travadas

- Só Android no MVP. `minSdk` 31 (Android 12). `targetSdk` 35.
- Google Play Services é aceito no marco 1. O rádio fica atrás de `MeshTransport`. O núcleo não importa Android.
- Voz do MVP, quando entrar, será nota de voz em hold-to-talk. PTT ao vivo fica restrito a um hop e não entra agora.
- Um canal local público e mensagens diretas. Sem grupos nomeados.
- Identidade Ed25519 local, sem conta. DM cifrada desde o primeiro envelope. A sala pública é assinada e legível por quem está no alcance.
- Texto e anúncio podem inundar a malha com TTL baixo. Mídia, quando existir, vai dirigida e com TTL menor.
- Fallback de internet fica atrás da mesma interface, depois que dois aparelhos conversarem sem sinal.
- Uso que manda no desenho: gente junta sem sinal estável. Trilha, evento, emergência.

## Módulos

- `:mesh-core` é Kotlin puro. Identidade, envelope binário, caixa selada, router, store-and-forward e o motor. Testa com nós simulados, sem rádio.
- `:app` é Jetpack Compose, Room, serviço em primeiro plano e o adaptador Nearby Connections em `P2P_CLUSTER`.

## Este marco entrega

- Documento de requisitos.
- Dois aparelhos no alcance se descobrem, entram no canal e trocam texto.
- O texto atravessa um aparelho no meio, com TTL, deduplicação e assinatura.
- DM só abre no destinatário.
- A DM fica na caixa de saída até o ack, ou até esgotar as tentativas.
- O histórico sobrevive a fechar a tela.
- O serviço em primeiro plano mantém a malha com a tela apagada.

## Fora deste marco

Nota de voz, imagem, relay WebSocket, Nostr, vários canais, número de segurança comparado por QR e rádio sem Play Services.

## Build

O Gradle deste projeto roda no JDK 17. O JDK 25 do sistema não serve para o Gradle 8.11.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :mesh-core:test :app:assembleDebug
```

O `sdk.dir` fica em `local.properties` e aponta para `$HOME/Android/Sdk`.
