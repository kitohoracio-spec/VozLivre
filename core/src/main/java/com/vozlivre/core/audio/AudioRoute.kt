package com.vozlivre.core.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Por onde sai (e entra) o som da chamada. */
enum class AudioRoute(val label: String) {
    /** Só como preferência: auricular Bluetooth se houver um ligado, senão o auricular do telemóvel. */
    AUTO("Automático"),
    EARPIECE("Auricular do telemóvel"),
    SPEAKER("Altifalante do telemóvel"),
    BLUETOOTH("Auricular Bluetooth")
}

object AudioRoutes {

    /** Há um auricular ou kit Bluetooth ligado a este telemóvel que sirva para chamadas? */
    @SuppressLint("MissingPermission")
    fun bluetoothAvailable(ctx: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 31) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } else {
            val adapter = (ctx.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter != null && adapter.isEnabled &&
                adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        }
    } catch (_: Exception) { false }

    /** Transforma a preferência na saída que realmente se usa agora. */
    fun resolve(pref: AudioRoute, ctx: Context): AudioRoute = when (pref) {
        AudioRoute.AUTO, AudioRoute.BLUETOOTH -> if (bluetoothAvailable(ctx)) AudioRoute.BLUETOOTH else AudioRoute.EARPIECE
        else -> pref
    }

    /** Próxima saída ao tocar no botão da chamada: auricular, altifalante e, se existir, Bluetooth. */
    fun next(current: AudioRoute, ctx: Context): AudioRoute = when (current) {
        AudioRoute.EARPIECE -> AudioRoute.SPEAKER
        AudioRoute.SPEAKER -> if (bluetoothAvailable(ctx)) AudioRoute.BLUETOOTH else AudioRoute.EARPIECE
        else -> AudioRoute.EARPIECE
    }
}
