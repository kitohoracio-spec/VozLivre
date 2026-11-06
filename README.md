# Grfee

Grfee — Desenvolvido por Guido Horácio

Chamadas de voz entre telemóveis Android sem Internet, por Wi-Fi (router ou hotspot), Wi-Fi Direct e Bluetooth. O áudio é comprimido com G.711 e cifrado com AES-256-GCM.

O projeto tem duas apps separadas, uma para o servidor e outra para os clientes, que partilham um módulo comum com a rede, o protocolo, o áudio e a cifra.

## Estrutura

```
Grfee/
  core/         biblioteca partilhada
  app-server/   app do Servidor  (com.vozlivre.server)
  app-client/   app do Cliente   (com.vozlivre.client)
  docs/         arquitetura e protocolo
```

O servidor decide quem pode ligar (bloqueios e aprovação manual). O cliente escolhe um servidor e liga-lhe. Cada app anuncia o seu papel na rede, por isso o servidor só lista clientes e o cliente só lista servidores. A explicação completa está em [docs/ARQUITETURA.md](docs/ARQUITETURA.md).

## App Servidor

Barra superior com o ícone e o nome Grfee. Barra inferior flutuante, em cartão arredondado, com cinco separadores. A barra desaparece quando rolas para baixo e volta quando rolas para cima.

- Ligações: logótipo, cartão de sessão ativa (online e tempo), mosaicos com clientes ligados, chamadas ativas, pedidos pendentes e tipo de rede, e o código QR para ligar clientes.
- Clientes: Ligados, Pendentes e Histórico. Em Ligados, o botão Ligar chama o cliente e o botão Escutar pede-lhe o microfone (ver Escuta). Em Pendentes, Aceitar ou Recusar.
- Chamadas: Ativas, Recentes e Registos. A chamada ativa tem silenciar, altifalante e terminar. Fora deste separador, uma barra verde leva de volta à chamada.
- Diagnóstico: estado geral, verificações de rede, descoberta, permissões, sinalização e áudio, tabela de pares e registo com Copiar e Limpar.
- Definições avançadas: gestão da rede (Wi-Fi, IP manual, Wi-Fi Direct com criação de grupo, Bluetooth, portas, reinício do motor), gestão de clientes (aprovação manual, bloqueios), gestão de chamadas (regras e estatísticas), áudio, segurança (nome, senha, permissões), segundo plano, registos e sobre.

## App Cliente

Barra superior com o ícone e o nome Grfee. Barra inferior flutuante, em cartão arredondado, com quatro separadores. A barra desaparece quando rolas para baixo e volta quando rolas para cima.

- Ligação: estado da ligação ao servidor, botão Ler QR do servidor, Procurar servidores, lista de servidores encontrados, nome e senha.
- Chamadas: Contactos (o servidor escolhido e os outros clientes desse servidor), Recentes e Favoritos. Cada contacto tem um botão de chamada.
- Áudio: volume e saída de áudio.
- Estado: servidor, tipo de ligação, IP local, força do sinal, tempo de ligação, permissões, segundo plano e créditos.

Em chamada, o cliente vê o ecrã de chamada com temporizador, microfone, altifalante e terminar. O cliente é básico de propósito: rede avançada (IP manual, Wi-Fi Direct, Bluetooth), bloqueios, escuta, diagnóstico, filtros de dispositivos e ganho do microfone existem só na app do Servidor.

## Ligar por QR

No Servidor, no separador Ligações, toca em Mostrar código QR. No Cliente, toca em Ler QR do servidor e aponta a câmara. O cliente fica com o servidor escolhido, os IPs dele sondados e a senha igual, sem escrever nada. A câmara só é pedida quando tocas no botão.

Se a câmara não abrir, confirma a permissão da câmara. Se o Android a bloqueou, a app mostra um aviso com o botão Abrir definições. Sem câmara, ou com o QR difícil de ler, o Servidor tem os botões Copiar código e Partilhar, e o Cliente tem Colar código. O código é o mesmo texto do QR, e pode seguir por mensagem ou Bluetooth.

O QR contém a senha do servidor. Mostra-o só a quem deve ter acesso e não o fotografes. Se mudares a senha ou a rede do servidor, o código muda sozinho.

## Auricular Bluetooth

O som da chamada pode sair por um auricular ou kit Bluetooth que já esteja ligado ao telemóvel, e o microfone do auricular entra junto. Por defeito a saída é automática: usa o auricular Bluetooth se houver um ligado e, se não houver, o auricular do telemóvel. Durante a chamada, o botão da saída alterna entre auricular, altifalante e Bluetooth. A saída por defeito escolhe-se no separador Áudio (Cliente) ou em Definições (Servidor). A app pede a permissão de dispositivos próximos ao abrir, porque o Android a exige para usar o Bluetooth na chamada.

Um auricular serve só como saída e entrada de som. Não aparece na lista de dispositivos nem recebe chamadas, porque não corre a app.

## Segunda chamada

Se alguém liga durante uma chamada, aparece um aviso no topo do ecrã com Recusar e Atender. Atender termina a chamada atual e passa para a nova. Recusar mantém a atual e a pessoa ouve a recusa. Se a chamada atual terminar antes de decidires, a segunda passa a tocar. No Servidor, a opção Perguntar-me numa segunda chamada pode ser desligada em Definições, Gestão de chamadas: nesse caso a pessoa recebe ocupado. No Cliente está sempre ligada. A app não mantém duas chamadas com áudio ao mesmo tempo.

## Dispositivos visíveis

A lista mostra só aparelhos com o Grfee. Na rede local só aparecem os que enviam o anúncio da app. No Bluetooth só aparecem os que anunciam o serviço da app, por isso auriculares, colunas e outros emparelhados ficam de fora. Em Wi-Fi Direct ficam só os telemóveis, e TV e impressoras são escondidas. Um telemóvel com o Grfee fechado ou com Bluetooth desligado não aparece. Em Gestão da rede (Servidor), a opção Mostrar também aparelhos sem a app mostra tudo, para diagnóstico.

## Quem pode ligar a quem

- Cliente para servidor e servidor para cliente: a chamada toca.
- Cliente para cliente: toca se os dois escolheram o mesmo servidor. Cada cliente anuncia na rede o servidor que escolheu, e é assim que os contactos aparecem. Clientes de servidores diferentes ignoram-se.
- O servidor ignora chamadas de clientes bloqueados. Com a aprovação manual ligada, recusa clientes ainda não aceites e o cliente vê "Chamada não autorizada". Estas regras valem para chamadas ao servidor. Entre clientes a chamada é direta e o servidor não a controla.
- Servidor para servidor: o recetor ignora a chamada.

## Escuta

Só o Servidor pede escuta. No separador Clientes, o botão Escutar envia um pedido ao cliente. O telemóvel do cliente toca, mostra que o servidor quer escutar o microfone e só começa se o utilizador tocar em Permitir. Não há atendimento automático nem permissão permanente. Durante a escuta, o cliente vê o ecrã "O servidor está a escutar o teu microfone" com o botão para parar, tem a notificação da app ativa e o indicador de microfone do Android visível. O cliente só aceita pedidos de escuta do servidor que escolheu. O servidor não abre o microfone dele e ouve pelo altifalante, ou pelo auricular Bluetooth se houver um ligado.

Usa a escuta apenas com o consentimento de quem está no local. Escutar um ambiente sem o conhecimento das pessoas é ilegal em muitos países.

## Compilar

Requisitos: JDK 17 e Android SDK 35. O Gradle 8.10.2 é o que o plugin Android 8.7.3 pede.

Android Studio: abre a pasta do projeto, espera pela sincronização do Gradle e escolhe a configuração `app-server` ou `app-client`.

Linha de comandos, a partir da raiz:

```
gradle :core:testDebugUnitTest
gradle :app-server:assembleDebug :app-client:assembleDebug
```

Os APK ficam em `app-server/build/outputs/apk/debug/` e `app-client/build/outputs/apk/debug/`.

O repositório não inclui o Gradle Wrapper. Para criá-lo: `gradle wrapper --gradle-version 8.10.2`.

GitHub Actions: o fluxo `Build Grfee APKs` corre os testes e compila os dois APK em cada push para `main`, em pull requests e a pedido. Os ficheiros ficam nos artefactos `Grfee-Servidor-debug-apk` e `Grfee-Cliente-debug-apk`.

## Como testar

1. Instala a app Servidor num telemóvel e a app Cliente noutro.
2. Define a mesma senha nos dois (Servidor: Definições, Segurança. Cliente: separador Ligação), ou salta este passo e usa o QR do passo 4.
3. Liga os dois à mesma rede, ou usa o hotspot de um deles. Com Wi-Fi Direct, no Servidor abre Gestão da rede e toca em Criar grupo. Depois, ainda no Servidor, toca no telemóvel cliente na lista de Wi-Fi Direct e aceita o convite no cliente.
4. No Servidor toca em Mostrar código QR e no Cliente em Ler QR do servidor. Sem QR, toca em Procurar servidores e escolhe o servidor. Depois, no Cliente, abre Chamadas e toca no botão de chamada de um contacto. No Servidor, liga a um cliente no separador Clientes. Com um segundo Cliente a usar o mesmo servidor, os dois aparecem nos Contactos um do outro.

As duas apps usam as mesmas portas UDP (39871 e 39872). Para testar, instala cada uma num telemóvel diferente.

## Se a chamada não acontecer

No Servidor abre Diagnóstico, toca em Executar diagnóstico e depois em Copiar no registo. Causas frequentes:

- Router com isolamento de clientes: usa hotspot ou Wi-Fi Direct.
- Senhas diferentes: o registo mostra "mensagem não decifrada".
- Poupança de bateria a fechar a app: ativa Segundo plano e retira as restrições de bateria.
- Wi-Fi Direct até ao Android 12 exige a localização ligada.

## Segundo plano

O botão Segundo plano mantém a app à escuta com o ecrã apagado e mostra uma notificação de chamada em ecrã inteiro quando alguém liga. No Android 14 ou superior pode ser preciso permitir notificações em ecrã inteiro nas definições do Android. Gasta mais bateria.

## Bluetooth

Emparelha primeiro os dois telemóveis nas definições Bluetooth do Android e abre as duas apps. Só aparecem na lista os telemóveis emparelhados que têm o Grfee aberto. Quem liga é o Servidor: em Definições, Gestão da rede, Bluetooth. O Cliente fica à escuta e não precisa de procurar. O Bluetooth tem mais atraso e menos qualidade de áudio do que o Wi-Fi.

## Segurança

Os comandos de chamada e o áudio vão cifrados com AES-256-GCM, com chave derivada da senha (PBKDF2-SHA256). Com a senha padrão, qualquer Grfee na rede consegue decifrar. O anúncio de presença (nome, id e papel) vai em claro para a descoberta funcionar. Bloquear e aprovar clientes controla quem pode ligar ao servidor, mas não substitui a senha.

## Áudio

PCM de 16 bits comprimido com G.711 µ-law, em frames de 20 ms. Wi-Fi a 16 kHz, Bluetooth a 8 kHz.

O microfone usa cancelamento de eco, supressão de ruído e controlo automático de ganho nos telemóveis que os têm. A fila de reprodução guarda no máximo 100 ms: quando a rede entrega uma rajada, os frames mais antigos são descartados para a conversa não ficar atrasada. Na chamada, o botão Altifalante liga a saída de volume alto, e o ganho do microfone (separador Áudio) sobe até 4 vezes.

## Limitações

- Uma só chamada de cada vez. O servidor não faz ponte de áudio entre clientes: as chamadas entre clientes são diretas.
- Um microfone Bluetooth sozinho, sem telemóvel, não serve como fonte de escuta. O Android liga a entrada e a saída do auricular em conjunto, e o aparelho não corre a app. A escuta usa o microfone de um telemóvel com o Cliente.
- Esta versão não fala com as versões 0.5 e anteriores, que eram uma única app. O nome mudou para Grfee, mas o identificador das apps (com.vozlivre.server e com.vozlivre.client) ficou igual, por isso a instalação atualiza a anterior.
- A força do sinal só existe em ligações Wi-Fi a um router ou hotspot. Em Wi-Fi Direct e Bluetooth aparece Indisponível.
- O histórico e as estatísticas existem só durante a sessão. Favoritos, bloqueios e aprovações ficam guardados.
- Sem Opus, que exigiria uma biblioteca nativa.
- Os ícones são vetoriais desenhados à mão e podem precisar de afinação.
