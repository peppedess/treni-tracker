package com.treni.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.treni.tracker.R
import com.treni.tracker.data.AppDatabase
import com.treni.tracker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrenoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            aggiornaWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun aggiornaWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getInstance(context).trenoDao()
            val treni = dao.getTreniAttivi()
            val treno = treni.firstOrNull()

            val views = RemoteViews(context.packageName, R.layout.widget_treno)

            if (treno == null) {
                views.setTextViewText(R.id.widgetNumero, "—")
                views.setTextViewText(R.id.widgetTratta, "Nessun treno monitorato")
                views.setTextViewText(R.id.widgetStato, "Apri l'app per aggiungerne uno")
            } else {
                views.setTextViewText(R.id.widgetNumero, treno.numeroTreno)
                views.setTextViewText(
                    R.id.widgetTratta,
                    "${treno.stazionePartenzaNome} → ${treno.stazioneDestinazioneNome ?: "?"}"
                )
                val ritardo = treno.ultimoRitardo
                val statoTesto = when {
                    ritardo == null -> "In attesa"
                    ritardo > 0 -> "+$ritardo min"
                    ritardo < 0 -> "$ritardo min"
                    else -> "In orario"
                }
                views.setTextViewText(R.id.widgetStato, statoTesto)
            }

            // Tap sul widget apre l'app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        /** Richiama l'aggiornamento di tutti i widget istanziati (chiamato dal worker). */
        fun aggiornaTutti(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TrenoWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, TrenoWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
