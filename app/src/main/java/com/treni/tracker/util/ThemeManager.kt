package com.treni.tracker.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFS = "treni_tracker_prefs"
    private const val KEY_TEMA = "tema"

    const val TEMA_SISTEMA = 0
    const val TEMA_CHIARO = 1
    const val TEMA_SCURO = 2

    fun applicaTemaSalvato(context: Context) {
        applica(leggiTema(context))
    }

    fun leggiTema(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TEMA, TEMA_SISTEMA)
    }

    fun salvaTema(context: Context, tema: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TEMA, tema)
            .apply()
        applica(tema)
    }

    private fun applica(tema: Int) {
        val mode = when (tema) {
            TEMA_CHIARO -> AppCompatDelegate.MODE_NIGHT_NO
            TEMA_SCURO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
