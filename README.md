# Blip

[English](README.en.md)

Chat e walkie-talkie para Android. Sem conta e sem número de telefone. O nome e a identidade ficam neste aparelho.

No canal se escreve. No rádio se fala: segura o botão, fala e solta. Os aparelhos perto passam a voz adiante, mesmo sem internet. Se não tem ninguém perto para passar, a voz tenta a internet.

<p>
  <img src="docs/screenshots/canal.png" width="240" alt="Conversa de exemplo no canal">
  <img src="docs/screenshots/pessoas.png" width="240" alt="Pessoa no alcance">
  <img src="docs/screenshots/mapa.png" width="240" alt="Mapa com o pin e a área da sala">
</p>

As capturas são um exemplo do canal, das pessoas e do mapa. O app abre em inglês. O idioma muda nos Ajustes.

## Telas

- **Canal.** Texto, imagem e nota de voz.
- **Rádio.** O walkie-talkie. Segura para falar, solta para enviar. A voz vai para todo mundo no canal. Quando outra pessoa fala, o aparelho toca.
- **Mapa.** O pin fica no centro. A busca aceita país, cidade ou bairro. País, Estado, Cidade e Bairro mudam o tamanho da área, pintada em verde claro.
- **Pessoas.** Quem está por perto, para uma conversa direta.
- **Menu.** Abre só os Ajustes.

## Ajustes

- **Internet e local** procura aparelhos perto e também tenta a internet. **Só internet** não procura aparelhos perto. A fala sai pela internet.
- Ligar e desligar a ligação, o aviso da bateria e o diário.
- Idioma. O padrão é inglês. Dá para escolher os outros idiomas da lista.
- Nome, impressão digital da identidade, e apagar tudo neste aparelho digitando `LIMPAR`.

A sala de um lugar no mapa não atravessa a internet. Dois aparelhos no mesmo ponto só se encontram se estiverem no alcance.

## O que fica cifrado

Cada pacote é assinado com Ed25519. Assinatura ruim é descartada.

A conversa direta, e o áudio ou a imagem nela, são cifrados para um aparelho só: uma chave X25519 nova, HKDF-SHA256 e ChaCha20-Poly1305. Só aquele aparelho abre.

Sala com senha cifra o texto e a mídia com essa senha: PBKDF2-HMAC-SHA256, 50 mil voltas, depois ChaCha20-Poly1305.

O canal público, as salas de lugar e as salas sem senha são assinados, mas quem recebe consegue ler. O nome também aparece. O que fica salvo neste aparelho não está trancado.

## Internet

Não há relay público. O script `relay/relay.py` sobe um relay local na porta 8791. O app fala com `http://127.0.0.1:8791`. No Waydroid, com `http://192.168.240.1:8791`.

```bash
python3 relay/relay.py
```

No celular ligado por USB, o relay deste computador só aparece depois de `adb reverse tcp:8791 tcp:8791`. Se o relay não está no ar, as mensagens esperam. A direta continua cifrada nesse caminho. O canal público pode ser lido por quem estiver nele, inclusive pelo relay.

## Build

Android 12 ou mais novo (minSdk 31), com Google Play Services. O Gradle deste projeto precisa de JDK 17.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :mesh-core:test :app:assembleDebug
```

O APK de debug sai em `app/build/outputs/apk/debug/app-debug.apk`.

Cada push na `main` também gera esse APK no GitHub, na ação **APK**, em Artifacts, com o nome `blip-debug`.
