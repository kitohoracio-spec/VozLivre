package com.vozlivre.core.call

import android.content.Context
import android.content.Intent

/**
 * Intent que abre a atividade principal da app em execução (Servidor ou Cliente).
 * Evita que o núcleo conheça as atividades de cada app.
 */
internal fun launchIntent(ctx: Context): Intent? =
    ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
