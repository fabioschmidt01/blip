# Requisitos do marco 1

## Produto

O Blip liga quem está perto, com ou sem internet. O aparelho anuncia um nome, acha os outros rádios e entrega texto sem conta e sem servidor.

A frase que o app tem de cumprir: duas pessoas abrem o Blip, ficam a alguns metros, e uma frase escrita no canal aparece no outro aparelho com a internet desligada.

## Aceite

1. O primeiro uso pede um nome e as permissões de rádio. Sem isso a malha não liga.
2. Com a malha ligada, o app mostra um serviço em primeiro plano e um log curto do que o rádio fez.
3. Dois aparelhos com Play Services se veem e trocam texto no canal local sem internet.
4. Com três aparelhos em linha, A ligado em B e B ligado em C, o texto de A chega em C. O mesmo texto não aparece duas vezes.
5. Uma mensagem direta de A para B não é legível por C. B vê o texto e A vê que foi entregue.
6. Se B estiver fora de alcance, a direta fica pendente e sai quando o link volta, até 8 tentativas.
7. Fechar a activity não apaga a conversa. O serviço continua.
8. Um aparelho sem Play Services abre o app e explica por que a malha não ligou.

## Identidade

Cada instalação gera duas sementes de 32 bytes e guarda em `filesDir/identity.bin`. Uma é Ed25519 e assina todo pacote. A outra é X25519 e abre as diretas. O arquivo é privado do app. Backup do sistema fica desligado para a chave não ir para a nuvem.

O nome é local e cabe em 32 bytes UTF-8. Ele não autentica ninguém. Quem autentica o pacote é a chave pública. A tela de rádio mostra uma impressão digital curta, o começo do SHA-256 da chave de assinatura, para duas pessoas compararem de boca.

## Envelope

Pacote v1, binário, magic `VR`.

| Campo | Tamanho |
|---|---|
| magic | 2 |
| version | 1 |
| type | 1 |
| ttl | 1 |
| flags | 1 |
| id | 16 |
| timestamp | 8, big-endian, relógio do remetente |
| sender | 32, Ed25519 |
| recipient | 32, zeros se não houver |
| payload length | 4 |
| payload | até 24 KB |
| signature | 64 |

A assinatura cobre tudo menos `ttl` e a própria assinatura. Um relay pode baixar o TTL sem invalidar o pacote. Payload adulterado, remetente trocado ou flag trocada quebra a assinatura.

Flags: bit 0 destinatário presente, bit 1 payload cifrado, bit 2 anúncio só daquele link.

Tipos: anúncio, texto público, direta, ack.

TTL de origem: anúncio 4, texto e direta 5. Pacote com TTL fora de 1..8 é descartado. TTL 1 é entregue e não é reencaminhado.

O anúncio de link (flag 2, TTL 1) prova que aquela conexão Nearby é a pessoa que assinou. O anúncio inundado só espalha o nome e a chave de cifragem.

## Malha

`P2P_CLUSTER`. Os dois lados anunciam e descobrem. Conexão recebida é aceita.

Texto público e anúncio vão a todos os links, menos o de onde vieram. Direta para alguém com link próprio vai só por esse link. Direta para alguém conhecido só de ouvir a malha vai aos 3 primeiros links, em ordem estável. A cópia fica na caixa de saída até o ack.

Deduplicação por id, com memória em disco, por 15 minutos e no máximo 2000 ids. Eco do próprio pacote é ignorado.

Ao nascer um link, o aparelho manda o anúncio e os últimos 20 textos públicos com TTL 1, para o recém-chegado ler o canal sem reencaminhar a história inteira.

## Direta

A caixa selada é X25519 efêmero, HKDF-SHA256 e ChaCha20-Poly1305. O AAD amarra a chave pública de assinatura do remetente, então copiar o ciphertext para outro pacote não autentica a frase. A assinatura externa diz quem enviou. Quem não tem a chave X25519 do destino não escreve direta.

## Permissões

Android 12 e 13 pedem scan, advertise e connect. Até o Android 12 também pedem localização fina, porque o Nearby ainda exige. Do 13 em diante pedem dispositivos Wi-Fi por perto, sem uso de localização. Notificação é pedida para o serviço em primeiro plano e não bloqueia a entrada.

O serviço usa o tipo `connectedDevice` e sobe com o app em primeiro plano.

## Fora do marco

Voz, imagem, canal com nome, relay, Nostr, QR e rádio BLE próprio.

## Prova

Testes de JVM cobrem assinatura, caixa, TTL, dedup e três nós. O rádio real pede dois aparelhos físicos. Emulador não conta.
