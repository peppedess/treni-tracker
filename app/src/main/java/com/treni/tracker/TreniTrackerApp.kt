package com.treni.tracker

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.treni.tracker.util.ThemeManager

class TreniTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Applica automaticamente i colori estratti dal wallpaper (Android 12+).
        // Su versioni precedenti non ha effetto: resta il fallback statico del tema.
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Applica il tema chiaro/scuro/sistema scelto dall'utente
        ThemeManager.applicaTemaSalvato(this)
    }
}
