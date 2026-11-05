package com.vozlivre.core.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.vozlivre.core.audio.AudioEngine
import com.vozlivre.core.audio.AudioRoute
import com.vozlivre.core.audio.AudioRoutes
import com.vozlivre.core.audio.Codec
import com.vozlivre.core.net.BluetoothTransport
import com.vozlivre.core.net.LanTransport
import com.vozlivre.core.net.Peer
import com.vozlivre.core.net.PeerKind
import com.vozlivre.core.net.Role
import com.vozlivre.core.net.Transport
import com.vozlivre.core.net.TransportListener
import com.vozlivre.core.net.WifiDirect
import com.vozlivre.core.net.safe
import com.vozlivre.core.security.Secure
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID

/** O que fazer com uma chamada recebida antes de tocar. */
enum class Screening {
    /** Segue para o toque. */
    ALLOW,

    /** Descarta em silêncio. Quem liga continua a tentar até ao tempo limite. */
    IGNORE,

    /** Responde DENY: quem liga vê logo que não foi autorizado. */
    DENY
}

/**
 * Máquina de estados da chamada, independente do transporte e do papel.
 * As regras de quem pode ligar a quem ficam nas subclasses (ServerEngine e
 * ClientEngine), através de [screenIncoming].
 *
 * Mensagens de controlo (texto, separadas por "|"):
 *   CALL|callId|nome    quem liga repete até haver resposta
 *   RINGING|callId      o outro lado está a tocar
 *   ACCEPT|callId       atendeu (repetido sempre que chega um CALL duplicado)
 *   REJECT|callId  BUSY|callId  DENY|callId  HANGUP|callId
 *   PING|callId         batimento de 2 em 2 s durante a chamada
 *
 * Todas as transições correm na thread principal.
 */
abstract class CallEngine(protected val app: Context, val role: Role) : TransportListener {

    enum class State { IDLE, OUTGOING, INCOMING, ACTIVE }

    interface Ui {
        fun onStateChanged()
        fun onPeersChanged()
        fun onLog()
        fun onToast(msg: String)
    }

    class CallRecord(val peerId: String, val peerName: String, val incoming: Boolean, val at: Long, val seconds: Int, val result: String)

    protected val main = Handler(Looper.getMainLooper())
    protected val prefs = app.getSharedPreferences("vozlivre", Context.MODE_PRIVATE)
    val myId: String = prefs.getString("id", null) ?: UUID.randomUUID().toString().take(8).also { prefs.edit().putString("id", it).apply() }

    @Volatile var ui: Ui? = null
    var displayName: String = prefs.getString("name", "Utilizador") ?: "Utilizador"
        private set

    val lan = LanTransport(app, myId, { displayName }, { role.code }, { groupId() })
    val bt = BluetoothTransport(app)
    val p2p = WifiDirect(app, lan, { log(it) }, { ui?.onPeersChanged() })

    @Volatile var state = State.IDLE; private set
    @Volatile var peer: Peer? = null; private set
    var statusText = "Nenhuma chamada ativa"; private set
    var muted = false; private set

    /** A sessão atual é uma escuta: o cliente envia o microfone e o servidor só ouve. */
    @Volatile var listenMode = false; private set
    var route: AudioRoute = AudioRoute.EARPIECE; private set
    var activeSince = 0L; private set

    private var callId = ""
    private var ringing = false
    private var outgoingStart = 0L
    private var lastCallSeen = 0L
    @Volatile private var lastRemote = 0L
    private var audio: AudioEngine? = null
    private var seq = 0
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var started = false
    private var sessionStart = SystemClock.elapsedRealtime()
    private var incomingCall = false
    private var lastDecryptLog = 0L

    private val logLines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val historyList = ArrayList<CallRecord>()

    var listenInBackground: Boolean = prefs.getBoolean("listen", false)
        private set

    /** Saída preferida ao começar uma chamada. AUTO usa o auricular Bluetooth se houver um ligado. */
    var defaultRoute: AudioRoute = loadDefaultRoute()
        private set

    private fun loadDefaultRoute(): AudioRoute {
        val saved = prefs.getString("default_route", null)
        if (saved != null) return try { AudioRoute.valueOf(saved) } catch (_: Exception) { AudioRoute.AUTO }
        return if (prefs.getBoolean("default_speaker", false)) AudioRoute.SPEAKER else AudioRoute.AUTO
    }

    var micPercent: Int = prefs.getInt("mic_pct", 60)
        private set

    /** Se alguém liga durante uma chamada: true pergunta ao utilizador, false responde "ocupado". */
    var askOnSecondCall: Boolean = prefs.getBoolean("ask_second", true)
        private set

    /** Mostra também dispositivos Bluetooth e Wi-Fi Direct que não têm a app. */
    var showAllDevices: Boolean = prefs.getBoolean("show_all", false)
        private set

    /** Segunda chamada à espera de resposta do utilizador. */
    @Volatile var waiting: Peer? = null; private set
    private var waitingId = ""
    private var waitingSeen = 0L

    val usingDefaultPassword: Boolean get() = prefs.getString("pw", "").isNullOrBlank()

    val history: List<CallRecord> get() = historyList.toList()

    init {
        lan.listener = this
        bt.listener = this
        bt.showAll = showAllDevices
        try { Secure.setPassword(prefs.getString("pw", "") ?: "") } catch (e: Exception) { log("Chave não iniciada: ${e.message}") }
    }

    // ---------------------------------------------------------------- regras do papel

    /**
     * Decide se uma chamada recebida segue para o toque. [peer] é o par já
     * descoberto na rede, ou null se ainda não o conhecemos. A implementação
     * regista no log o motivo de qualquer recusa.
     */
    protected abstract fun screenIncoming(peerId: String, peer: Peer?, listen: Boolean): Screening

    /** Servidor a que este par se juntou, anunciado aos outros no HELLO. Só o Cliente usa. */
    protected open fun groupId(): String = ""

    // ---------------------------------------------------------------- ciclo de vida

    fun start() {
        if (started) return
        started = true
        lan.start()
        p2p.start()
        sessionStart = SystemClock.elapsedRealtime()
        log("Motor iniciado (${if (role == Role.SERVER) "servidor" else "cliente"}). O meu id: $myId")
        if (listenInBackground) startListenService()
    }

    fun startBluetooth() { bt.startServer() }

    fun stopAll() {
        if (!started) return
        started = false
        p2p.stop(); lan.stop(); bt.stop()
        try { ListenService.stop(app) } catch (_: Exception) {}
        log("Motor parado")
    }

    fun setListen(on: Boolean) {
        listenInBackground = on
        prefs.edit().putBoolean("listen", on).apply()
        if (on) startListenService() else try { ListenService.stop(app) } catch (_: Exception) {}
    }

    private fun startListenService() {
        try { ListenService.start(app) } catch (e: Exception) { log("Serviço de escuta não arrancou: ${e.message}") }
    }

    // ---------------------------------------------------------------- identidade e definições locais

    fun setPassword(pw: String) {
        prefs.edit().putString("pw", pw.trim()).apply()
        try { Secure.setPassword(pw.trim()) } catch (e: Exception) { log("Chave não iniciada: ${e.message}") }
        log(if (pw.isBlank()) "Senha padrão ativa" else "Senha das chamadas atualizada")
    }

    fun setName(n: String) {
        displayName = n.trim().ifBlank { "Utilizador" }
        prefs.edit().putString("name", displayName).apply()
        lan.wake()
    }

    fun setDefaultRoute(r: AudioRoute) {
        defaultRoute = r
        prefs.edit().putString("default_route", r.name).apply()
        if (state == State.ACTIVE) setRoute(AudioRoutes.resolve(r, app))
    }

    fun setAskOnSecondCall(on: Boolean) {
        askOnSecondCall = on
        prefs.edit().putBoolean("ask_second", on).apply()
    }

    fun setShowAllDevices(on: Boolean) {
        showAllDevices = on
        prefs.edit().putBoolean("show_all", on).apply()
        bt.showAll = on
        notifyPeersChanged()
    }

    fun setMicPercent(p: Int) {
        micPercent = p
        prefs.edit().putInt("mic_pct", p).apply()
        audio?.gain = micGain(p)
    }

    // ---------------------------------------------------------------- estado da rede

    fun sessionSeconds(): Int = ((SystemClock.elapsedRealtime() - sessionStart) / 1000).toInt()

    fun wifiSignal(): String {
        return try {
            val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val rssi = wm.connectionInfo.rssi
            when {
                rssi <= -127 || rssi == 0 -> "Indisponível"
                rssi >= -55 -> "Excelente"
                rssi >= -67 -> "Boa"
                rssi >= -80 -> "Razoável"
                else -> "Fraca"
            }
        } catch (e: Exception) { "Indisponível" }
    }

    fun networkLabel(): String = when {
        lan.hasP2pLink() -> "Wi-Fi Direct"
        lan.hasLinks() -> "Wi-Fi"
        bt.isEnabled() -> "Bluetooth"
        else -> "Sem rede"
    }

    fun transportFor(peerId: String): Transport = if (peerId.startsWith("bt:")) bt else lan

    /** Todos os pares visíveis. As subclasses podem enriquecer cada par (por exemplo, com o estado de bloqueio). */
    open fun allPeers(): List<Peer> = lan.peers() + bt.peers()

    protected fun notifyPeersChanged() { ui?.onPeersChanged() }

    // ---------------------------------------------------------------- diagnóstico

    fun log(msg: String) {
        val line = fmt.format(Date()) + " " + msg
        synchronized(logLines) {
            logLines.addLast(line)
            while (logLines.size > 200) logLines.removeFirst()
        }
        main.post { ui?.onLog() }
    }

    fun logText(): String = synchronized(logLines) { logLines.joinToString("\n") }

    fun clearLog() { synchronized(logLines) { logLines.clear() }; ui?.onLog() }

    // ---------------------------------------------------------------- ações do utilizador

    fun startCall(p: Peer) { beginOutgoing(p, false) }

    /** Pede ao par para partilhar o microfone. Só o servidor expõe isto. O par tem de permitir. */
    protected fun beginListen(p: Peer) { beginOutgoing(p, true) }

    private fun beginOutgoing(p: Peer, listen: Boolean) {
        if (state != State.IDLE) { ui?.onToast("Já estás numa chamada"); return }
        val id = newCallId()
        state = State.OUTGOING; peer = p; callId = id; ringing = false; incomingCall = false; listenMode = listen
        statusText = if (listen) "A pedir para escutar ${p.name}..." else "A ligar a ${p.name}..."
        outgoingStart = SystemClock.elapsedRealtime()
        log((if (listen) "Pedido de escuta $id a " else "Chamada $id para ") + "${p.name} (${p.id}) via ${p.via.ifBlank { "bluetooth" }}")
        ui?.onStateChanged()
        transportFor(p.id).connect(p.id) { ok, err ->
            main.post {
                if (state != State.OUTGOING || callId != id) return@post
                if (!ok) finish("Não foi possível ligar: ${err ?: "sem resposta"}", false)
                else callTick.run()
            }
        }
    }

    fun answer() {
        val p = peer ?: return
        if (state != State.INCOMING) return
        send(p, "ACCEPT|$callId")
        becomeActive()
    }

    fun reject() {
        val p = peer ?: return
        if (state != State.INCOMING) return
        send(p, "REJECT|$callId")
        finish("Chamada recusada", false)
    }

    fun hangup() {
        if (state == State.IDLE) return
        finish("Chamada terminada", true)
    }

    fun toggleMute() {
        muted = !muted
        audio?.muted = muted
        ui?.onStateChanged()
    }

    /** Muda a saída do som da chamada atual. */
    fun setRoute(r: AudioRoute) {
        route = r
        audio?.setRoute(r)
        ui?.onStateChanged()
    }

    /** Auricular, altifalante e, havendo um ligado, auricular Bluetooth. */
    fun cycleRoute() { setRoute(AudioRoutes.next(route, app)) }

    // ---------------------------------------------------------------- TransportListener (threads de rede)

    override fun onPeersChanged() { main.post { ui?.onPeersChanged() } }

    override fun onLog(msg: String) { log(msg) }

    override fun onControl(peerId: String, text: String) {
        val plain = Secure.decryptText(text)
        if (plain == null) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastDecryptLog > 10_000) {
                lastDecryptLog = now
                log("Mensagem recebida mas não decifrada. A senha é igual nos dois telemóveis?")
            }
            return
        }
        main.post { handleControl(peerId, plain) }
    }

    override fun onAudio(peerId: String, seq: Int, pcm: ByteArray) {
        if (state == State.ACTIVE && peerId == peer?.id) {
            val plain = Secure.decrypt(pcm) ?: return
            lastRemote = SystemClock.elapsedRealtime()
            audio?.onRemoteFrame(seq, Codec.decode(plain))
        }
    }

    override fun onPeerDisconnected(peerId: String) {
        main.post {
            if (state != State.IDLE && peer?.id == peerId) finish("Ligação perdida", false)
        }
    }

    // ---------------------------------------------------------------- protocolo

    private fun handleControl(peerId: String, text: String) {
        val parts = text.split("|")
        if (parts.size < 2) return
        val cmd = parts[0]
        val cid = parts[1]
        val cur = peer
        val sameCall = cid == callId && cur != null && cur.id == peerId
        when (cmd) {
            "CALL" -> {
                val name = parts.getOrNull(2)?.replace('_', ' ') ?: "Desconhecido"
                val listen = parts.getOrNull(3) == "L"
                val known = allPeers().firstOrNull { it.id == peerId }
                val p = known
                    ?: Peer(peerId, name, if (peerId.startsWith("bt:")) PeerKind.BLUETOOTH else PeerKind.LAN)
                when (screenIncoming(peerId, known, listen)) {
                    Screening.IGNORE -> return
                    Screening.DENY -> { send(p, "DENY|$cid"); return }
                    Screening.ALLOW -> {}
                }
                if (state == State.IDLE) {
                    state = State.INCOMING; peer = p; callId = cid; incomingCall = true; listenMode = listen
                    statusText = if (listen) "${p.name} pede para escutar o microfone" else "Chamada de ${p.name}"
                    lastCallSeen = SystemClock.elapsedRealtime()
                    log(if (listen) "Pedido de escuta recebido de ${p.name}" else "Chamada recebida de ${p.name}")
                    send(p, "RINGING|$cid")
                    startRinging()
                    if (ui == null) showIncomingNotification(p)
                    main.removeCallbacks(incomingWatch)
                    main.postDelayed(incomingWatch, 1000)
                    ui?.onStateChanged()
                } else if (sameCall) {
                    lastCallSeen = SystemClock.elapsedRealtime()
                    if (state == State.INCOMING) send(p, "RINGING|$cid")
                    else if (state == State.ACTIVE) send(p, "ACCEPT|$cid")
                } else if (listen) {
                    send(p, "BUSY|$cid")
                } else if (waiting?.id == peerId && cid == waitingId) {
                    waitingSeen = SystemClock.elapsedRealtime()
                    send(p, "RINGING|$cid")
                } else if (askOnSecondCall && waiting == null) {
                    startWaiting(p, cid)
                } else {
                    send(p, "BUSY|$cid")
                }
            }
            "RINGING" -> if (state == State.OUTGOING && sameCall && !ringing) {
                ringing = true
                statusText = "A tocar em ${cur?.name}..."
                ui?.onStateChanged()
            }
            "ACCEPT" -> if (state == State.OUTGOING && sameCall) becomeActive()
            "REJECT" -> if (state == State.OUTGOING && sameCall) finish("Chamada recusada", false)
            "BUSY" -> if (state == State.OUTGOING && sameCall) finish("O outro telemóvel está ocupado", false)
            "DENY" -> if (state == State.OUTGOING && sameCall) finish("Chamada não autorizada: o servidor ainda não aprovou este cliente", false)
            "HANGUP" -> {
                if (state != State.IDLE && sameCall) finish("A outra pessoa terminou a chamada", false)
                else if (waiting?.id == peerId && cid == waitingId) dropWaiting("Chamada perdida")
            }
            "PING" -> if (state == State.ACTIVE && sameCall) lastRemote = SystemClock.elapsedRealtime()
        }
    }

    private fun send(p: Peer, text: String) {
        try { transportFor(p.id).sendControl(p.id, Secure.encryptText(text)) } catch (e: Exception) { log("Envio cifrado falhou: ${e.message}") }
    }

    private val callTick = object : Runnable {
        override fun run() {
            if (state != State.OUTGOING) return
            val p = peer ?: return
            if (SystemClock.elapsedRealtime() - outgoingStart > 40_000) { finish("Sem resposta", true); return }
            send(p, "CALL|$callId|${safe(displayName)}" + if (listenMode) "|L" else "")
            main.postDelayed(this, if (ringing) 2000 else 1000)
        }
    }

    private val incomingWatch = object : Runnable {
        override fun run() {
            if (state != State.INCOMING) return
            if (SystemClock.elapsedRealtime() - lastCallSeen > 6000) { finish("Chamada perdida", false); return }
            main.postDelayed(this, 1000)
        }
    }

    private val keepAlive = object : Runnable {
        override fun run() {
            if (state != State.ACTIVE) return
            val p = peer ?: return
            send(p, "PING|$callId")
            if (SystemClock.elapsedRealtime() - lastRemote > 9000) { finish("Ligação perdida", true); return }
            main.postDelayed(this, 2000)
        }
    }

    private fun becomeActive() {
        val p = peer ?: return
        main.removeCallbacks(callTick)
        main.removeCallbacks(incomingWatch)
        stopRinging()
        hideIncomingNotification()
        state = State.ACTIVE
        activeSince = SystemClock.elapsedRealtime()
        lastRemote = activeSince
        // Na escuta, o servidor só ouve (sem microfone) e o cliente só envia (sem altifalante).
        val captures = !(listenMode && role == Role.SERVER)
        val plays = !(listenMode && role == Role.CLIENT)
        val notice = when {
            !listenMode -> "Em chamada com ${p.name}"
            captures -> "A partilhar o microfone com ${p.name}"
            else -> "A escutar ${p.name}"
        }
        statusText = notice
        route = when {
            listenMode && role == Role.SERVER -> if (AudioRoutes.bluetoothAvailable(app)) AudioRoute.BLUETOOTH else AudioRoute.SPEAKER
            else -> AudioRoutes.resolve(defaultRoute, app)
        }
        log(if (listenMode) "Escuta ativa: $notice" else "Chamada ativa com ${p.name}")
        try { CallService.start(app, notice) } catch (e: Exception) { log("Serviço de chamada não arrancou: ${e.message}") }
        acquireLocks()
        val t = transportFor(p.id)
        seq = 0
        val a = AudioEngine(app, t.audioRate, captures, plays) { buf, n ->
            seq = (seq + 1) and 0xFFFF
            val packed = Codec.encode(buf, n)
            val sealed = Secure.encrypt(packed, packed.size)
            t.sendAudio(p.id, seq, sealed, sealed.size)
        }
        a.muted = muted
        a.gain = micGain(micPercent)
        val err = a.start()
        if (err != null) { log(err); ui?.onToast(err); finish(err, true); return }
        a.setRoute(route)
        audio = a
        main.removeCallbacks(keepAlive)
        main.postDelayed(keepAlive, 2000)
        ui?.onStateChanged()
    }

    private fun finish(message: String, notify: Boolean) {
        val p = peer
        val cid = callId
        if (notify && p != null && state != State.IDLE) {
            send(p, "HANGUP|$cid")
            main.postDelayed({ send(p, "HANGUP|$cid") }, 200)
        }
        main.removeCallbacks(callTick)
        main.removeCallbacks(incomingWatch)
        main.removeCallbacks(keepAlive)
        stopRinging()
        hideIncomingNotification()
        audio?.stop(); audio = null
        releaseLocks()
        try { CallService.stop(app) } catch (_: Exception) {}
        if (p != null) {
            val secs = if (state == State.ACTIVE) ((SystemClock.elapsedRealtime() - activeSince) / 1000).toInt() else 0
            historyList.add(0, CallRecord(p.id, p.name, incomingCall, System.currentTimeMillis(), secs, message))
            while (historyList.size > 50) historyList.removeAt(historyList.size - 1)
        }
        log("Chamada terminada: $message")
        state = State.IDLE; peer = null; callId = ""; ringing = false
        muted = false; route = AudioRoute.EARPIECE; listenMode = false
        statusText = message
        ui?.onToast(message)
        ui?.onStateChanged()
        promoteWaiting()
    }

    // ---------------------------------------------------------------- segunda chamada

    private fun startWaiting(p: Peer, cid: String) {
        waiting = p; waitingId = cid; waitingSeen = SystemClock.elapsedRealtime()
        log("Segunda chamada de ${p.name}: à espera da decisão do utilizador")
        send(p, "RINGING|$cid")
        try {
            @Suppress("DEPRECATION")
            (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
        main.removeCallbacks(waitingWatch)
        main.postDelayed(waitingWatch, 1000)
        ui?.onStateChanged()
    }

    private val waitingWatch = object : Runnable {
        override fun run() {
            if (waiting == null) return
            if (SystemClock.elapsedRealtime() - waitingSeen > 6000) { dropWaiting("Chamada perdida"); return }
            main.postDelayed(this, 1000)
        }
    }

    private fun dropWaiting(result: String) {
        val w = waiting ?: return
        waiting = null
        main.removeCallbacks(waitingWatch)
        historyList.add(0, CallRecord(w.id, w.name, true, System.currentTimeMillis(), 0, result))
        while (historyList.size > 50) historyList.removeAt(historyList.size - 1)
        log("Segunda chamada de ${w.name}: $result")
        ui?.onStateChanged()
    }

    /** Recusa a segunda chamada e continua na atual. */
    fun rejectWaiting() {
        val w = waiting ?: return
        send(w, "REJECT|$waitingId")
        dropWaiting("Chamada recusada")
    }

    /** Termina a chamada atual e atende a segunda. */
    fun answerWaiting() {
        val w = waiting ?: return
        val cid = waitingId
        waiting = null
        main.removeCallbacks(waitingWatch)
        if (state != State.IDLE) finish("Chamada terminada para atender ${w.name}", true)
        state = State.INCOMING; peer = w; callId = cid; incomingCall = true
        send(w, "ACCEPT|$cid")
        becomeActive()
    }

    /** Quando a chamada atual acaba sozinha, a segunda passa a tocar normalmente. */
    private fun promoteWaiting() {
        val w = waiting ?: return
        waiting = null
        main.removeCallbacks(waitingWatch)
        state = State.INCOMING; peer = w; callId = waitingId; incomingCall = true
        statusText = "Chamada de ${w.name}"
        lastCallSeen = waitingSeen
        startRinging()
        if (ui == null) showIncomingNotification(w)
        main.removeCallbacks(incomingWatch)
        main.postDelayed(incomingWatch, 1000)
        ui?.onStateChanged()
    }

    // ---------------------------------------------------------------- toque e bloqueios do sistema

    private fun startRinging() {
        try {
            val r = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            r?.play(); ringtone = r
        } catch (_: Exception) {}
        try {
            @Suppress("DEPRECATION")
            val v = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 0))
        } catch (_: Exception) {}
    }

    private fun showIncomingNotification(p: Peer) {
        try {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel("vozlivre_incoming", "Chamadas recebidas", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false)
                }
            )
            val launch = launchIntent(app)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: Intent()
            val pi = PendingIntent.getActivity(
                app, 1, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(app, "vozlivre_incoming")
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(if (listenMode) "${p.name} pede para escutar o microfone" else "Chamada de ${p.name}")
                .setContentText(if (listenMode) "Toca para decidir" else "Toca para atender")
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
            nm.notify(2, n)
        } catch (e: Exception) { log("Notificação de chamada falhou: ${e.message}") }
    }

    private fun hideIncomingNotification() {
        try { (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(2) } catch (_: Exception) {}
    }

    private fun stopRinging() {
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try {
            @Suppress("DEPRECATION")
            (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
        } catch (_: Exception) {}
    }

    private fun acquireLocks() {
        try {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vozlivre:call").apply { setReferenceCounted(false); acquire(2 * 60 * 60 * 1000L) }
        } catch (_: Exception) {}
        try {
            val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val mode = if (android.os.Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "vozlivre:call").apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
        try { wifiLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wifiLock = null
    }

    /** 50% é o ganho normal (1x). De 50% a 100% sobe até 4x. */
    private fun micGain(percent: Int): Float =
        if (percent <= 50) percent / 50f else 1f + (percent - 50) / 50f * 3f

    private fun newCallId() = UUID.randomUUID().toString().take(6)
}
