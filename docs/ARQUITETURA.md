# Arquitetura

## Módulos

```
core/        com.vozlivre.core    biblioteca Android
app-server/  com.vozlivre.server  app, depende de :core
app-client/  com.vozlivre.client  app, depende de :core
```

As duas apps dependem do `core` e não dependem uma da outra. Tudo o que é comum vive no `core`. Cada app contém só o que é específico do seu papel.

## core

Pacote `net`: transporte.
- `Transport`, `Peer`, `TransportListener`: contrato comum entre Wi-Fi e Bluetooth.
- `LanTransport`: UDP em qualquer rede IP local (router, hotspot, Wi-Fi Direct). Abre sockets presos ao IP de cada interface, descobre pares por broadcast, varredura da sub-rede e sondagem por IP.
- `BluetoothTransport`: RFCOMM com enquadramento próprio.
- `WifiDirect`: descoberta e grupo Wi-Fi Direct. Quando o grupo se forma, o Android cria uma interface `p2p-*` e o `LanTransport` passa a usá-la.
- `Role`: papel anunciado na rede (`S` servidor, `C` cliente).

Pacote `call`: chamada.
- `CallEngine`: máquina de estados da chamada, protocolo de controlo, histórico, toque, bloqueios de energia e áudio. É abstrata. As subclasses implementam `screenIncoming`, que decide se uma chamada recebida toca (`ALLOW`), é descartada (`IGNORE`) ou é recusada com aviso (`DENY`).
- `CallService`, `ListenService`: serviços em primeiro plano durante a chamada e para escuta em segundo plano.
- `EngineProvider`: implementado pela `Application` de cada app. Os serviços e atividades do núcleo usam-no para chegar ao motor.

Pacote `audio`: `AudioEngine` (microfone, altifalante, buffer de jitter) e `Codec` (G.711 µ-law).

Pacote `security`: `Secure` (AES-256-GCM, chave derivada da senha com PBKDF2-SHA256).

Pacote `ui`: componentes visuais (`Kit`, `Colors`, `Section`, `Screen`), secções partilhadas (identidade, permissões, segundo plano, áudio, Wi-Fi Direct, Bluetooth, registo) e `BaseActivity`, que traz a barra inferior, as subpáginas, o ecrã de chamada e os pedidos de permissões.

## app-server

- `ServerEngine`: bloqueios, aprovação manual e lista de clientes. Anuncia-se como `S`.
- `ServerActivity`: define os cinco separadores e indica que a chamada ativa fica no separador Chamadas.
- `ui/`: secções do painel (visão geral, clientes, chamadas, diagnóstico, definições avançadas).

## app-client

- `ClientEngine`: servidor escolhido, lista de servidores e favoritos. Anuncia-se como `C`.
- `ClientActivity`: define os quatro separadores. A chamada ativa ocupa o ecrã inteiro.
- `ui/`: secções do cliente (ligação, chamadas, estado).

## Rede

Portas UDP: 39871 para controlo e descoberta, 39872 para áudio.

Controlo (UDP, texto):
- `HELLO|id|nome|papel|grupo`: anúncio de presença, em claro. O grupo é o id do servidor que o cliente escolheu (vazio no servidor). É enviado por broadcast, por varredura e para pares já conhecidos.
- `MSG|id|carga`: a carga é o comando de chamada cifrado e codificado em Base64.

Áudio (UDP): `[seq:2][nonce:12][áudio cifrado][etiqueta:16]`. O áudio é µ-law, frames de 20 ms.

Bluetooth: um socket RFCOMM por par, com frames `[tipo:1][tamanho:2][dados]`. Tipo 1 é controlo e tipo 2 é áudio (`seq:2` mais o mesmo envelope cifrado).

## Ligação por QR

O servidor gera `grfee://join?v=1&id=..&n=..&ip=a,b&pw=..` (`Pairing`, no pacote `net`), com cada valor codificado como URL. `ServerEngine.pairingPayload()` junta o id, o nome, os IPv4 das redes ativas e a senha. O cliente lê o texto com a biblioteca zxing-android-embedded, `Pairing.decode` valida-o (versão, id e só IPv4 numéricos) e `ClientEngine.joinServer` define a senha, escolhe o servidor e sonda os IPs. A leitura corre em `PortraitCaptureActivity`, uma subclasse da captura da biblioteca que permite o retrato.

## Protocolo de chamada

Comandos de controlo, cifrados, separados por `|`:

```
CALL|callId|nome[|L]  quem liga repete de 1 em 1 s (2 s depois de tocar) até haver resposta. L marca um pedido de escuta
RINGING|callId     o outro lado está a tocar
ACCEPT|callId      atendeu. Repetido sempre que chega um CALL duplicado
REJECT|callId      recusou
BUSY|callId        já está noutra chamada
DENY|callId        o servidor não autorizou este cliente
HANGUP|callId      terminou (enviado duas vezes)
PING|callId        batimento de 2 em 2 s durante a chamada
```

Tempos: 40 s sem resposta termina a chamada de quem liga. Quem recebe perde a chamada se não chegar CALL durante 6 s. Uma chamada ativa cai depois de 9 s sem áudio nem PING.

## Grupo e contactos do cliente

Cada cliente anuncia no HELLO o id do servidor escolhido (`ClientEngine.groupId`). `ClientEngine.members()` devolve os clientes com o mesmo grupo e `contacts()` junta-lhes o servidor. `screenIncoming` ignora chamadas de clientes de outro grupo.

## Escuta

`ServerEngine.startListening` envia um CALL com a marca `L`. No cliente, a sessão fica em INCOMING com `listenMode` e o telemóvel toca, por isso começa só quando o utilizador toca em Permitir (`answer`). O `AudioEngine` abre só o que a sessão precisa: o cliente capta e não toca, o servidor toca e não capta. `ClientEngine.screenIncoming` só aceita escuta do servidor escolhido e `ServerEngine` ignora pedidos de escuta. Com uma chamada em curso, um pedido de escuta recebe BUSY e não entra na segunda chamada.

## Segunda chamada

Com `askOnSecondCall` ligado, um CALL de outro par durante uma chamada fica em `waiting` e o chamador recebe RINGING. `answerWaiting` termina a chamada atual e aceita a nova, `rejectWaiting` responde REJECT. Sem decisão, a espera acaba ao fim de 6 s sem novo CALL. Se a chamada atual acabar antes, `promoteWaiting` faz a segunda tocar. Com a opção desligada, o CALL recebe BUSY.

## Saída de áudio

`AudioRoute` (automático, auricular, altifalante, Bluetooth) e `AudioRoutes` decidem a saída. No Android 12 ou superior usa `setCommunicationDevice` com os dispositivos de comunicação disponíveis. Em versões anteriores usa `startBluetoothSco`. `CallEngine.defaultRoute` guarda a preferência e `cycleRoute` alterna durante a chamada.

## Filtro de dispositivos

`BluetoothTransport` só expõe pares que anunciam o `SERVICE_UUID` (lista de UUID do Android, atualizada com `fetchUuidsWithSdp` e `ACTION_UUID`), pares que ligaram a este telemóvel e pares a que este telemóvel já se ligou. Wi-Fi Direct mostra só dispositivos da categoria 10 (telefone) ou sem categoria. `showAllDevices` desativa ambos os filtros.

## Regras por papel

Servidor, por esta ordem:
0. Pedido de escuta: `IGNORE`.
1. Cliente bloqueado: `IGNORE`.
2. Chamada de outro servidor: `IGNORE`.
3. Aprovação manual ligada e cliente não aprovado: `DENY`.
4. Caso contrário: `ALLOW`.

Cliente:
1. Pedido de escuta que não vem do servidor escolhido: `IGNORE`.
2. Chamada de um cliente de outro servidor (ou sem servidor escolhido): `IGNORE`.
3. Caso contrário: `ALLOW`.

Um par sem papel conhecido (dispositivo Bluetooth, ou um par que ainda não enviou HELLO) é tratado como permitido.

## Acrescentar uma regra

Uma regra nova de autorização entra em `screenIncoming` da subclasse correspondente. O protocolo, o áudio e a interface comum não mudam.
