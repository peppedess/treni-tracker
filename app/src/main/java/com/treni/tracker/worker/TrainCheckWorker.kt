package com.treni.tracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.treni.tracker.data.AppDatabase
import com.treni.tracker.data.TrenoMonitorato
import com.treni.tracker.network.TrainResult
import com.treni.tracker.network.ViaggiaTrenoClient
import com.treni.tracker.notification.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SOGLIA_VARIAZIONE_RITARDO = 2 // minuti

class TrainCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = ViaggiaTrenoClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.trenoDao()
        val treni = dao.getTreniAttivi()

        for (treno in treni) {
            controllaTreno(treno)
        }

        Result.success()
    }

    private suspend fun controllaTreno(treno: TrenoMonitorato) {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.trenoDao()

        val result = client.andamentoTreno(
            treno.stazionePartenzaCod,
            treno.numeroTreno,
            treno.timestampMs
        )

        when (result) {
            is TrainResult.Success -> {
                val stato = result.data

                if (stato.soppresso) {
                    Notifier.notifica(
                        applicationContext,
                        treno.id.toInt(),
                        "Treno ${treno.numeroTreno} soppresso",
                        "Il treno ${treno.numeroTreno} (${treno.stazionePartenzaNome} → ${treno.stazioneDestinazioneNome}) è stato soppresso."
                    )
                    dao.rimuovi(treno.id)
                    return
                }

                val ritardoAttuale = stato.ritardoMinuti
                val ultimaStazioneRilevata = stato.ultimaStazioneRilevata
                val ritardoPrecedente = treno.ultimoRitardo
                val stazionePrecedente = treno.ultimaStazioneNotificata

                // Registra il rilevamento nello storico (per le statistiche)
                dao.inserisciRilevamento(
                    com.treni.tracker.data.RilevamentoStorico(
                        numeroTreno = treno.numeroTreno,
                        ritardoMinuti = ritardoAttuale,
                        dataCorsa = treno.dataCorsa
                    )
                )

                val cambioStazione = ultimaStazioneRilevata != null && ultimaStazioneRilevata != stazionePrecedente
                val variazioneSignificativa = ritardoPrecedente == null ||
                    kotlin.math.abs(ritardoAttuale - ritardoPrecedente) >= SOGLIA_VARIAZIONE_RITARDO

                if (cambioStazione || variazioneSignificativa) {
                    val corpo = costruisciMessaggio(ultimaStazioneRilevata, ritardoAttuale)
                    Notifier.notifica(
                        applicationContext,
                        treno.id.toInt(),
                        "Treno ${treno.numeroTreno}",
                        corpo
                    )
                    dao.aggiornaStato(treno.id, ritardoAttuale, ultimaStazioneRilevata)
                }

                // Se l'ultima fermata della corsa è stata raggiunta ed è la destinazione, fine monitoraggio
                val ultimaFermata = stato.fermate.lastOrNull()
                if (ultimaFermata != null && ultimaFermata.passata && ultimaFermata.stazione == stato.stazioneDestinazione) {
                    Notifier.notifica(
                        applicationContext,
                        treno.id.toInt(),
                        "Treno ${treno.numeroTreno} arrivato",
                        "Il treno ${treno.numeroTreno} è arrivato a ${stato.stazioneDestinazione}."
                    )
                    dao.rimuovi(treno.id)
                }
            }
            is TrainResult.NoData -> {
                // Dati non disponibili in questo ciclo: non facciamo nulla, riproviamo al prossimo giro
            }
            is TrainResult.NotFound -> {
                // Non dovrebbe succedere qui (il treno è già stato validato all'aggiunta)
            }
            is TrainResult.NetworkError -> {
                // Errore di rete temporaneo: riproviamo al prossimo ciclo
            }
        }
    }

    private fun costruisciMessaggio(stazione: String?, ritardo: Int): String {
        val rigaRitardo = when {
            ritardo > 0 -> "in ritardo di $ritardo min"
            ritardo < 0 -> "in anticipo di ${kotlin.math.abs(ritardo)} min"
            else -> "in orario"
        }
        return if (stazione != null) "A $stazione, $rigaRitardo." else "Aggiornamento: $rigaRitardo."
    }
}
