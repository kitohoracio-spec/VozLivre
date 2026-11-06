package com.vozlivre.core.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import com.vozlivre.core.R
import com.vozlivre.core.audio.AudioRoute
import com.vozlivre.core.call.CallEngine
import com.vozlivre.core.net.Role

/** Um separador da barra inferior. */
class NavTab(val icon: Int, val label: String, val screen: Screen)

/**
 * Estrutura comum das duas apps: barra inferior, subpáginas, ecrã de chamada,
 * permissões e acesso ao hardware (Bluetooth, volume, bateria).
 *
 * Cada app (Servidor e Cliente) só decide quais são os separadores e como mostra
 * a chamada ativa:
 *  - [callTabIndex] >= 0: a chamada ativa aparece nesse separador, com uma barra
 *    verde de regresso quando o utilizador está noutro ecrã (Servidor).
 *  - [callTabIndex] < 0: a chamada ativa ocupa o ecrã inteiro (Cliente).
 * Chamadas a tocar (a receber ou a fazer) ocupam sempre o ecrã inteiro.
 */
abstract class BaseActivity : Activity(), CallEngine.Ui, Host {

    private class NavItem(val item: LinearLayout, val holder: FrameLayout, val icon: ImageView, val label: TextView)

    private companion object { const val REQ_ENABLE_BT = 21 }

    protected abstract val engine: CallEngine

    /** Separadores da barra inferior. Chamado uma vez, depois de [k] estar pronto. */
    protected abstract fun buildTabs(): List<NavTab>

    protected open val callTabIndex: Int get() = -1

    /** Texto pequeno ao lado do nome na barra superior ("Servidor" ou "Cliente"). */
    protected open val roleLabel: String get() = ""

    protected lateinit var k: Kit

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private var mainView: View? = null

    private var screens: List<Screen> = emptyList()
    private val navItems = ArrayList<NavItem>()
    private var body: FrameLayout? = null
    private var nav: View? = null
    private var navShown = true
    private var current = 0
    private var subScreen: Screen? = null
    private val mainLogs = ArrayList<LogSection>()
    private val subLogs = ArrayList<LogSection>()
    private var buildingShell = false
    private var logPending = false
    private var callBar: TextView? = null
    private var lastState = CallEngine.State.IDLE

    private val pending = HashMap<Int, () -> Unit>()
    private var reqCode = 100
    private var qrCallback: ((String) -> Unit)? = null

    // ecrã de chamada
    private lateinit var callLayer: LinearLayout
    private lateinit var ring: View
    private lateinit var callTop: TextView
    private lateinit var callTimer: TextView
    private lateinit var callName: TextView
    private lateinit var callIp: TextView
    private lateinit var toolsRow: LinearLayout
    private lateinit var incomingRow: LinearLayout
    private lateinit var hangCol: LinearLayout
    private lateinit var muteBtn: FrameLayout
    private lateinit var speakerBtn: FrameLayout
    private lateinit var speakerLabel: TextView
    private lateinit var speakerCol: LinearLayout
    private lateinit var muteCol: LinearLayout
    private lateinit var answerLabel: TextView
    private var pulse: ObjectAnimator? = null

    // aviso de segunda chamada
    private lateinit var waitingBar: LinearLayout
    private lateinit var waitingText: TextView

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        k = Kit(this)
        root = FrameLayout(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF04160D.toInt(), Colors.BG))
        buildOverlay()
        buildWaitingBar()
        setContentView(root)
        engine.start()
        val base = ArrayList<String>(micPerms())
        base.addAll(notifPerms())
        // Sem esta permissão o Android não deixa usar o auricular Bluetooth na chamada.
        if (Build.VERSION.SDK_INT >= 31) base.add(Manifest.permission.BLUETOOTH_CONNECT)
        withPerms(base) { }
        if (allGranted(btServerPerms())) engine.startBluetooth()
        showMain()
    }

    override fun onStart() {
        super.onStart()
        // Retoma a escuta Bluetooth se o Bluetooth foi ligado depois de a app abrir.
        if (allGranted(btServerPerms())) engine.startBluetooth()
        engine.ui = this
        onStateChanged()
        currentScreen()?.refresh()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000)
    }

    override fun onStop() {
        if (engine.ui === this) engine.ui = null
        handler.removeCallbacks(ticker)
        setPulse(false)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing && engine.state == CallEngine.State.IDLE && !engine.listenInBackground) engine.stopAll()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Host

    override fun toast(m: String) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show() }

    override fun allGranted(perms: List<String>): Boolean = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun micPerms(): List<String> = listOf(Manifest.permission.RECORD_AUDIO)

    override fun notifPerms(): List<String> =
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    override fun p2pPerms(): List<String> =
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Para ficar à escuta de ligações Bluetooth basta a permissão de ligar (Android 12 ou superior). A de procurar não é preciso. */
    private fun btServerPerms(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()

    override fun btPerms(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun withPerms(perms: List<String>, whenOk: () -> Unit) {
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { whenOk(); return }
        val code = reqCode++
        pending[code] = whenOk
        requestPermissions(missing.toTypedArray(), code)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val action = pending.remove(requestCode)
        if (action != null) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                action()
            } else {
                val denied = permissions.filterIndexed { i, _ -> grantResults.getOrNull(i) != PackageManager.PERMISSION_GRANTED }
                // Sem justificação a mostrar, o Android deixou de pedir: só as definições da app resolvem.
                if (denied.any { !shouldShowRequestPermissionRationale(it) }) offerSettings(denied)
                else toast("Permissão necessária não concedida")
            }
        }
        if (allGranted(btServerPerms())) engine.startBluetooth()
        currentScreen()?.refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val qr = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (qr != null) {
            val text = qr.contents
            val cb = qrCallback
            qrCallback = null
            if (text != null && cb != null) cb(text)
        }
        if (requestCode == REQ_ENABLE_BT && resultCode == RESULT_OK) {
            engine.startBluetooth()
            engine.bt.startScan()
        }
        currentScreen()?.refresh()
    }

    override fun scanQr(onResult: (String) -> Unit) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            toast("Este telemóvel não tem câmara. Usa Colar código.")
            return
        }
        withPerms(listOf(Manifest.permission.CAMERA)) {
            qrCallback = onResult
            try {
                IntentIntegrator(this)
                    .setCaptureActivity(PortraitCaptureActivity::class.java)
                    .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    .setOrientationLocked(false)
                    .setBeepEnabled(false)
                    .setPrompt("Aponta para o QR do servidor")
                    .initiateScan()
            } catch (e: Exception) {
                qrCallback = null
                engine.log("Leitor de QR não abriu: ${e.message}")
                toast("Não foi possível abrir a câmara. Usa Colar código.")
            }
        }
    }

    /** O Android bloqueou o pedido de permissão: explica e abre as definições da app. */
    private fun offerSettings(denied: List<String>) {
        val what = denied.joinToString(", ") {
            when (it) {
                Manifest.permission.CAMERA -> "Câmara"
                Manifest.permission.RECORD_AUDIO -> "Microfone"
                else -> "as permissões pedidas"
            }
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Permissão bloqueada")
            .setMessage("O Android deixou de mostrar o pedido. Abre as definições da app, toca em Permissões e ativa: $what.")
            .setPositiveButton("Abrir definições") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                } catch (_: Exception) { toast("Abre as definições do Android, Apps, Grfee, Permissões") }
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    override fun scanBluetooth() {
        withPerms(btPerms()) {
            if (!engine.bt.available) { toast("Sem Bluetooth neste telemóvel"); return@withPerms }
            if (!engine.bt.isEnabled()) {
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT)
            } else {
                engine.startBluetooth()
                engine.bt.startScan()
            }
        }
    }

    override fun makeDiscoverable() {
        withPerms(btPerms()) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
        }
    }

    override fun scanAll() {
        engine.lan.wake()
        val all = (p2pPerms() + btPerms()).distinct()
        withPerms(all) {
            if (engine.bt.available && engine.bt.isEnabled()) { engine.startBluetooth(); engine.bt.startScan() }
            engine.p2p.discover()
        }
    }

    override fun openBatterySettings() {
        try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        catch (_: Exception) { toast("Abre as definições de bateria do Android") }
    }

    override fun goTo(index: Int) { select(index) }

    override fun registerLog(s: LogSection) { if (buildingShell) mainLogs.add(s) else subLogs.add(s) }

    override fun openSub(title: String, icon: Int, sections: List<Section>) {
        val b = body ?: return
        val header = StaticSection(k.titleHeader(icon, title) { closeSub() })
        val sc = Screen(k, listOf<Section>(header) + sections)
        attachScroll(sc)
        subScreen = sc
        setNavVisible(true)
        b.removeAllViews()
        b.addView(sc.view, FrameLayout.LayoutParams(-1, -1))
        sc.refresh()
    }

    private fun closeSub() {
        subScreen = null
        subLogs.clear()
        setNavVisible(true)
        val b = body ?: return
        b.removeAllViews()
        screens.getOrNull(current)?.let {
            b.addView(it.view, FrameLayout.LayoutParams(-1, -1))
            it.refresh()
        }
    }

    private fun currentScreen(): Screen? = subScreen ?: screens.getOrNull(current)

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (subScreen != null) closeSub() else super.onBackPressed()
    }

    override fun getVolume(): Int {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_VOICE_CALL) * 100 / max
    }

    override fun setVolume(pct: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        try { am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, pct * max / 100, 0) } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- estrutura

    /** Cabeçalho simples de um separador. */
    protected fun header(icon: Int, title: String): Section = StaticSection(k.titleHeader(icon, title))

    private fun showMain() {
        mainView?.let { root.removeView(it) }
        mainLogs.clear(); subLogs.clear()
        navItems.clear()
        screens = emptyList()
        subScreen = null
        callBar = null
        buildingShell = true
        val v = buildShell()
        buildingShell = false
        mainView = v
        root.addView(v, 0, FrameLayout.LayoutParams(-1, -1))
        onStateChanged()
    }

    private fun buildShell(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(buildTopBar())
        if (callTabIndex >= 0) {
            val bar = k.text("", 13f, Color.BLACK, true)
            bar.gravity = Gravity.CENTER
            bar.setPadding(k.dp(12), k.dp(8), k.dp(12), k.dp(8))
            bar.background = k.shape(Colors.GREEN, 0)
            bar.visibility = View.GONE
            bar.setOnClickListener { select(callTabIndex) }
            callBar = bar
            col.addView(bar)
        }
        // O conteúdo ocupa tudo e a barra inferior flutua por cima, no fundo.
        val holder = FrameLayout(this)
        val b = FrameLayout(this)
        body = b
        holder.addView(b, FrameLayout.LayoutParams(-1, -1))
        col.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
        val tabs = buildTabs()
        screens = tabs.map { it.screen }
        for (s in screens) attachScroll(s)
        val navCard = buildNav(tabs)
        nav = navCard
        navShown = true
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        lp.setMargins(k.dp(16), 0, k.dp(16), k.dp(12))
        holder.addView(navCard, lp)
        current = 0
        select(0)
        return col
    }

    /** Barra superior fixa: ícone e nome da app à esquerda, papel à direita. */
    private fun buildTopBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(k.dp(16), k.dp(10), k.dp(16), k.dp(6))
        bar.addView(k.icon(R.drawable.ic_logo, Colors.GREEN, 28))
        bar.addView(k.text("Grfee", 22f, Colors.TEXT, true), k.lp(0, -2, left = 8, weight = 1f))
        if (roleLabel.isNotEmpty()) bar.addView(k.pill(roleLabel))
        return bar
    }

    private fun attachScroll(s: Screen) {
        s.onScroll = { dy, y ->
            if (y <= k.dp(4) || dy < -k.dp(2)) setNavVisible(true)
            else if (dy > k.dp(2)) setNavVisible(false)
        }
    }

    /** Mostra a barra inferior ou desliza-a para fora do ecrã. */
    private fun setNavVisible(show: Boolean) {
        val n = nav ?: return
        if (navShown == show) return
        navShown = show
        val away = (n.height + k.dp(24)).toFloat()
        n.animate().translationY(if (show) 0f else away).setDuration(200).start()
    }

    private fun buildNav(tabs: List<NavTab>): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(k.dp(8), k.dp(8), k.dp(8), k.dp(8))
        bar.background = k.shape(Colors.CARD, 28, Colors.alpha(Colors.GREEN, 0x33), 1)
        k.glow(bar, Colors.GREEN, 8)
        for ((i, t) in tabs.withIndex()) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(0, k.dp(6), 0, k.dp(6))
            val holder = FrameLayout(this)
            val ic = k.icon(t.icon, Colors.MUTED, 20)
            holder.addView(ic, FrameLayout.LayoutParams(k.dp(20), k.dp(20), Gravity.CENTER))
            item.addView(holder, k.lp(k.dp(34), k.dp(34)))
            val tx = k.text(t.label, 10.5f, Colors.MUTED)
            item.addView(tx, k.lp(-2, -2, top = 2))
            item.setOnClickListener { select(i) }
            navItems.add(NavItem(item, holder, ic, tx))
            bar.addView(item, k.lp(0, -2, left = if (i > 0) 4 else 0, weight = 1f))
        }
        return bar
    }

    private fun select(i: Int) {
        val b = body ?: return
        if (i < 0 || i >= screens.size) return
        current = i
        subScreen = null
        subLogs.clear()
        setNavVisible(true)
        b.removeAllViews()
        b.addView(screens[i].view, FrameLayout.LayoutParams(-1, -1))
        for ((j, n) in navItems.withIndex()) {
            val sel = j == i
            n.icon.setColorFilter(if (sel) Color.BLACK else Colors.MUTED)
            n.holder.background = if (sel) circle(Colors.GREEN) else null
            n.label.setTextColor(if (sel) Colors.GREEN else Colors.MUTED)
            n.item.background = if (sel) k.shape(Colors.alpha(Colors.GREEN, 0x12), 20, Colors.alpha(Colors.GREEN, 0x55), 1) else null
        }
        screens[i].refresh()
        updateCallBar()
    }

    // ---------------------------------------------------------------- CallEngine.Ui

    override fun onPeersChanged() { currentScreen()?.refresh() }

    override fun onLog() {
        if (logPending) return
        logPending = true
        handler.postDelayed({
            logPending = false
            for (l in mainLogs) l.refresh()
            for (l in subLogs) l.refresh()
        }, 400)
    }

    override fun onToast(msg: String) { toast(msg) }

    override fun onStateChanged() {
        val st = engine.state
        val inTab = callTabIndex >= 0
        val showOverlay = st == CallEngine.State.INCOMING || st == CallEngine.State.OUTGOING || (st == CallEngine.State.ACTIVE && !inTab)
        callLayer.visibility = if (showOverlay) View.VISIBLE else View.GONE
        val p = engine.peer
        callName.text = p?.name ?: ""
        callIp.text = if (p == null) "" else if (p.detail.isNotEmpty()) p.detail else viaLabel(p)
        incomingRow.visibility = if (st == CallEngine.State.INCOMING) View.VISIBLE else View.GONE
        hangCol.visibility = if (st == CallEngine.State.OUTGOING || st == CallEngine.State.ACTIVE) View.VISIBLE else View.GONE
        toolsRow.visibility = if (st == CallEngine.State.ACTIVE) View.VISIBLE else View.GONE
        muteBtn.background = circle(if (engine.muted) Colors.RED else Colors.CARD_HI)
        (muteBtn.tag as ImageView).setColorFilter(if (engine.muted) Color.WHITE else Colors.TEXT)
        val routed = engine.route != AudioRoute.EARPIECE
        speakerBtn.background = circle(if (routed) Colors.GREEN else Colors.CARD_HI)
        val speakerIcon = speakerBtn.tag as ImageView
        speakerIcon.setImageResource(if (engine.route == AudioRoute.BLUETOOTH) R.drawable.ic_bluetooth else R.drawable.ic_speaker)
        speakerIcon.setColorFilter(if (routed) Color.BLACK else Colors.TEXT)
        speakerLabel.text = when (engine.route) {
            AudioRoute.EARPIECE -> "Auricular"
            AudioRoute.SPEAKER -> "Altifalante"
            else -> "Bluetooth"
        }
        // Escuta: o servidor só ouve (sem microfone) e o cliente só envia (sem altifalante).
        val listening = engine.listenMode
        answerLabel.text = if (listening) "Permitir" else "Atender"
        muteCol.visibility = if (listening && engine.role == Role.SERVER) View.GONE else View.VISIBLE
        speakerCol.visibility = if (listening && engine.role == Role.CLIENT) View.GONE else View.VISIBLE
        updateCallText()
        val w = engine.waiting
        waitingBar.visibility = if (w == null) View.GONE else View.VISIBLE
        if (w != null) waitingText.text = "${w.name} está a ligar"
        setPulse(st == CallEngine.State.OUTGOING || st == CallEngine.State.INCOMING)
        if (inTab && st == CallEngine.State.ACTIVE && lastState != CallEngine.State.ACTIVE) select(callTabIndex)
        lastState = st
        updateCallBar()
        currentScreen()?.refresh()
    }

    private fun updateCallBar() {
        val bar = callBar ?: return
        val p = engine.peer
        val away = current != callTabIndex || subScreen != null
        if (engine.state == CallEngine.State.ACTIVE && p != null && away) {
            val s = ((SystemClock.elapsedRealtime() - engine.activeSince) / 1000).toInt()
            bar.text = "Em chamada com ${p.name} • ${formatSeconds(s)}  (toca para abrir)"
            bar.visibility = View.VISIBLE
        } else {
            bar.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------- ecrã de chamada

    private fun circle(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }

    private fun round(icon: Int, fill: Int, rotate: Float = 0f, tint: Int = Color.WHITE, glow: Boolean = false, onClick: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.background = circle(fill)
        f.setOnClickListener { onClick() }
        val img = ImageView(this)
        img.setImageResource(icon)
        img.setColorFilter(tint)
        img.rotation = rotate
        f.addView(img, FrameLayout.LayoutParams(k.dp(30), k.dp(30), Gravity.CENTER))
        f.layoutParams = LinearLayout.LayoutParams(k.dp(72), k.dp(72))
        f.tag = img
        if (glow) k.glow(f, fill, 8)
        return f
    }

    private fun labeled(b: View, label: String, weight: Float = 0f): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(b)
        val t = k.muted(label, 13f)
        t.gravity = Gravity.CENTER
        c.addView(t, k.lp(top = 6))
        if (weight > 0f) c.layoutParams = LinearLayout.LayoutParams(0, -2, weight)
        return c
    }

    private fun buildOverlay() {
        callLayer = LinearLayout(this)
        callLayer.orientation = LinearLayout.VERTICAL
        callLayer.gravity = Gravity.CENTER_HORIZONTAL
        callLayer.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF04160D.toInt(), Colors.BG))
        callLayer.setPadding(k.dp(24), k.dp(48), k.dp(24), k.dp(48))
        callLayer.visibility = View.GONE
        callLayer.isClickable = true

        callTop = k.text("", 17f, Colors.TEXT, true)
        callTop.gravity = Gravity.CENTER
        callTimer = k.text("00:00:00", 30f, Colors.TEXT, true)
        callTimer.gravity = Gravity.CENTER
        callLayer.addView(callTop)
        callLayer.addView(callTimer, k.lp(top = 4))

        val ringBox = FrameLayout(this)
        ring = View(this)
        ring.background = k.shape(Colors.alpha(Colors.GREEN, 0x10), 200, Colors.alpha(Colors.GREEN, 0x55), 2)
        ringBox.addView(ring, FrameLayout.LayoutParams(k.dp(190), k.dp(190), Gravity.CENTER))
        val av = k.iconCircle(R.drawable.ic_person, 120)
        k.glow(av, Colors.GREEN, 10)
        ringBox.addView(av, FrameLayout.LayoutParams(k.dp(120), k.dp(120), Gravity.CENTER))
        callLayer.addView(ringBox, k.lp(k.dp(190), k.dp(190), top = 28))

        callName = k.text("", 24f, Colors.TEXT, true)
        callName.gravity = Gravity.CENTER
        callIp = k.muted("", 15f)
        callIp.gravity = Gravity.CENTER
        callLayer.addView(callName, k.lp(top = 16))
        callLayer.addView(callIp, k.lp(top = 4))
        callLayer.addView(View(this), k.lp(-1, 0, weight = 1f))

        muteBtn = round(R.drawable.ic_mic, Colors.CARD_HI, tint = Colors.TEXT) { engine.toggleMute() }
        speakerBtn = round(R.drawable.ic_speaker, Colors.CARD_HI, tint = Colors.TEXT) { engine.cycleRoute() }
        val moreBtn = round(R.drawable.ic_more, Colors.CARD_HI, tint = Colors.TEXT) { showMore() }
        toolsRow = LinearLayout(this)
        toolsRow.orientation = LinearLayout.HORIZONTAL
        muteCol = labeled(muteBtn, "Microfone", 1f)
        toolsRow.addView(muteCol)
        speakerCol = labeled(speakerBtn, "Auricular", 1f)
        speakerLabel = speakerCol.getChildAt(1) as TextView
        toolsRow.addView(speakerCol)
        toolsRow.addView(labeled(moreBtn, "Mais", 1f))
        callLayer.addView(toolsRow, k.lp(bottom = 28))

        val reject = round(R.drawable.ic_call, Colors.RED, 135f, glow = true) { engine.reject() }
        val answer = round(R.drawable.ic_call, Colors.GREEN, 0f, Color.BLACK, true) { engine.answer() }
        incomingRow = LinearLayout(this)
        incomingRow.orientation = LinearLayout.HORIZONTAL
        incomingRow.addView(labeled(reject, "Recusar", 1f))
        val answerCol = labeled(answer, "Atender", 1f)
        answerLabel = answerCol.getChildAt(1) as TextView
        incomingRow.addView(answerCol)
        callLayer.addView(incomingRow)

        val hang = round(R.drawable.ic_call, Colors.RED, 135f, glow = true) { engine.hangup() }
        hangCol = labeled(hang, "Terminar")
        callLayer.addView(hangCol)

        root.addView(callLayer, FrameLayout.LayoutParams(-1, -1))
    }

    /** Aviso sobre tudo quando alguém liga durante uma chamada: o utilizador decide. */
    private fun buildWaitingBar() {
        waitingBar = k.card(true)
        waitingBar.visibility = View.GONE
        waitingText = k.text("", 16f, Colors.TEXT, true)
        waitingBar.addView(waitingText)
        waitingBar.addView(k.muted("Atender termina a chamada atual.", 12f), k.lp(top = 2))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(k.button("Recusar", Kit.Style.DANGER, true) { engine.rejectWaiting() }, k.lp(0, -2, weight = 1f))
        row.addView(k.button("Atender", Kit.Style.PRIMARY, true) { engine.answerWaiting() }, k.lp(0, -2, left = 8, weight = 1f))
        waitingBar.addView(row, k.lp(top = 10))
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        lp.setMargins(k.dp(16), k.dp(48), k.dp(16), 0)
        root.addView(waitingBar, lp)
    }

    private fun showMore() {
        val items = arrayOf("Aumentar volume", "Diminuir volume", "Detalhes da ligação")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Mais opções")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> setVolume((getVolume() + 10).coerceAtMost(100))
                    1 -> setVolume((getVolume() - 10).coerceAtLeast(0))
                    else -> {
                        val p = engine.peer
                        toast(if (p == null) "Sem chamada" else "${p.name} · ${viaLabel(p)} · ${p.detail}")
                    }
                }
            }
            .show()
    }

    private fun setPulse(on: Boolean) {
        if (on) {
            if (pulse == null) {
                val a = ObjectAnimator.ofPropertyValuesHolder(
                    ring,
                    PropertyValuesHolder.ofFloat("scaleX", 1f, 1.12f),
                    PropertyValuesHolder.ofFloat("scaleY", 1f, 1.12f),
                    PropertyValuesHolder.ofFloat("alpha", 1f, 0.35f)
                )
                a.duration = 1100
                a.repeatCount = ValueAnimator.INFINITE
                a.repeatMode = ValueAnimator.REVERSE
                a.start()
                pulse = a
            }
        } else {
            pulse?.cancel()
            pulse = null
            ring.scaleX = 1f; ring.scaleY = 1f; ring.alpha = 1f
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateCallText()
            updateCallBar()
            currentScreen()?.refreshLive()
            handler.postDelayed(this, 1000)
        }
    }

    private fun updateCallText() {
        if (engine.state == CallEngine.State.ACTIVE) {
            val s = ((SystemClock.elapsedRealtime() - engine.activeSince) / 1000).toInt()
            callTop.text = when {
                !engine.listenMode -> "Chamada em curso"
                engine.role == Role.SERVER -> "A escutar"
                else -> "O servidor está a escutar o teu microfone"
            }
            callTimer.text = formatHms(s)
            callTimer.visibility = View.VISIBLE
        } else {
            callTop.text = engine.statusText
            callTimer.visibility = View.GONE
        }
    }
}
