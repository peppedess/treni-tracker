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

    companion object {
        const val ACTION_REFRESH = "com.treni.tracker.widget.ACTION_REFRESH"

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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            // Feedback immediato: mostra "Aggiornamento…" subito, prima che il worker finisca
            mostraFeedbackAggiornamento(context)

            // Lancia subito un controllo dei treni monitorati, poi aggiorna il widget
            val richiesta = androidx.work.OneTimeWorkRequestBuilder<com.treni.tracker.worker.TrainCheckWorker>().build()
            androidx.work.WorkManager.getInstance(context).enqueue(richiesta)
        }
    }

    private fun mostraFeedbackAggiornamento(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TrenoWidgetProvider::class.java))
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_treno)
            // Aggiorniamo solo il testo di stato per dare un feedback immediato;
            // il resto dei dati verrà sovrascritto subito dopo da aggiornaWidget
            manager.partiallyUpdateAppWidget(id, views.apply {
                setTextViewText(R.id.widgetStato, "Aggiornamento…")
            })
        }
    }

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

            // Tap sull'icona refresh: controllo immediato senza aprire l'app
            val refreshIntent = Intent(context, TrenoWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
