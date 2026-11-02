# Blip

[English](README.en.md)

Rádio de bolso para Android. Fala com quem está perto, com ou sem internet.

O aparelho anuncia um nome, acha os outros rádios por perto e entrega texto, áudio e imagem sem conta e sem número de telefone. A identidade fica só no aparelho.

<p>
  <img src="docs/screenshots/canal.png" width="240" alt="Conversa de exemplo no canal">
  <img src="docs/screenshots/pessoas.png" width="240" alt="Pessoa no alcance do rádio">
  <img src="docs/screenshots/mapa.png" width="240" alt="Mapa com o pin e a área da sala">
</p>

As capturas são um exemplo no celular. O canal mostra uma conversa entre o nome do rádio e outro aparelho. O mapa marca um lugar e pinta, em verde claro, a área que a sala cobre.

## O que já faz

- Canal local, mensagem direta cifrada e salas com senha opcional.
- Nota de voz com a forma de onda, e imagem no chat.
- Mapa para escolher um lugar. O pin fica no centro. A busca aceita país, cidade ou bairro. País, Estado, Cidade e Bairro mudam o tamanho da área.
- Limpar uma conversa, e apagar tudo no aparelho digitando LIMPAR em Rádio.
- Histórico no aparelho. O rádio continua com a tela apagada, num serviço em primeiro plano.

Duas pessoas no mesmo lugar, com o Blip aberto, se ouvem pelo rádio de perto. A sala de um ponto do mapa ainda não atravessa a internet: quem escolhe o mesmo lugar só se encontra se estiver no alcance.

## Build

Android 12 ou mais novo (minSdk 31), com Google Play Services. O Gradle deste projeto precisa de JDK 17.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :mesh-core:test :app:assembleDebug
```

O APK de debug sai em `app/build/outputs/apk/debug/app-debug.apk`.

Cada push na `main` também gera esse APK no GitHub, na ação **APK**, em Artifacts, com o nome `blip-debug`.
