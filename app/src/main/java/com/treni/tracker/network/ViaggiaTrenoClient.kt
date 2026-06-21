package com.treni.tracker.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Client per le API non ufficiali di ViaggiaTreno (Trenitalia/RFI).
 *
 * Non documentate ufficialmente, possono cambiare o interrompersi
 * senza preavviso. Coprono treni Trenitalia e buona parte del TPL
 * ferroviario su rete RFI. Italo e Trenord su rete non-RFI non sono
 * coperti: nessuna API pubblica nota per questi casi.
 */

private const val BASE_URL = "http://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno"

data class TrainCandidate(
    val numero: String,
    val stazionePartenzaNome: String,
    val stazionePartenzaCod: String,
    val timestampMs: Long
)

data class StopInfo(
    val stazione: String,
    val arrivoEffettivo: Long?,
    val partenzaEffettiva: Long?,
    val ritardo: Int?,
    val passata: Boolean
)

data class TrainStatus(
    val numero: String,
    val stazionePartenza: String?,
    val stazioneDestinazione: String?,
    val ritardoMinuti: Int,
    val soppresso: Boolean,
    val ultimaStazioneRilevata: String?,
    val fermate: List<StopInfo>
)

sealed class TrainResult<out T> {
    data class Success<T>(val data: T) : TrainResult<T>()
    data class NotFound(val message: String) : TrainResult<Nothing>()
    data class NoData(val message: String) : TrainResult<Nothing>()
    data class NetworkError(val message: String) : TrainResult<Nothing>()
}

class ViaggiaTrenoClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun cercaNumeroTreno(numeroTreno: String): TrainResult<List<TrainCandidate>> {
        val url = "$BASE_URL/cercaNumeroTrenoTrenoAutocomplete/$numeroTreno"
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return TrainResult.NotFound("Nessuna corsa trovata per il treno $numeroTreno")
            }

            val body = response.body?.string()?.trim()
            if (body.isNullOrEmpty()) {
                return TrainResult.NotFound("Nessuna corsa trovata per il treno $numeroTreno")
            }

            val candidati = body.lines().mapNotNull { riga ->
                try {
                    val parts = riga.split("|")
                    val label = parts[0]
                    val payload = parts[1]
                    val payloadParts = payload.split("-")
                    val num = payloadParts[0]
                    val codStazione = payloadParts[1]
                    val ts = payloadParts[2].toLong()
                    val nomeStazione = label.substringAfter(" - ")
                    TrainCandidate(num, nomeStazione, codStazione, ts)
                } catch (e: Exception) {
                    null
                }
            }

            if (candidati.isEmpty()) {
                TrainResult.NotFound("Nessuna corsa trovata per il treno $numeroTreno")
            } else {
                TrainResult.Success(candidati)
            }
        } catch (e: Exception) {
            TrainResult.NetworkError(e.message ?: "Errore di rete")
        }
    }

    fun andamentoTreno(codStazionePartenza: String, numeroTreno: String, timestampMs: Long): TrainResult<TrainStatus> {
        val url = "$BASE_URL/andamentoTreno/$codStazionePartenza/$numeroTreno/$timestampMs"
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.code == 204) {
                return TrainResult.NoData("Dati non disponibili per il treno $numeroTreno (probabilmente cancellato o riprogrammato)")
            }
            if (!response.isSuccessful) {
                return TrainResult.NoData("Errore API ViaggiaTreno (status ${response.code}) per il treno $numeroTreno")
            }

            val body = response.body?.string() ?: return TrainResult.NoData("Risposta vuota")
            val obj = json.parseToJsonElement(body) as JsonObject

            val ritardo = obj["ritardo"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val provvedimento = obj["provvedimento"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val soppresso = provvedimento == 1 || provvedimento == 2

            val fermateJson = obj["fermate"]
            val fermate = mutableListOf<StopInfo>()
            if (fermateJson is kotlinx.serialization.json.JsonArray) {
                for (f in fermateJson) {
                    val fObj = f as JsonObject
                    val stazione = fObj["stazione"]?.jsonPrimitive?.contentOrNull ?: ""
                    val arrivoReale = fObj["arrivoReale"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    val partenzaReale = fObj["partenzaReale"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    val ritardoFermata = fObj["ritardo"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    fermate.add(
                        StopInfo(
                            stazione = stazione,
                            arrivoEffettivo = arrivoReale,
                            partenzaEffettiva = partenzaReale,
                            ritardo = ritardoFermata,
                            passata = arrivoReale != null || partenzaReale != null
                        )
                    )
                }
            }

            val stazioneUltimoRilevamento = obj["stazioneUltimoRilevamento"]?.jsonPrimitive?.contentOrNull

            TrainResult.Success(
                TrainStatus(
                    numero = obj["numeroTreno"]?.jsonPrimitive?.contentOrNull ?: numeroTreno,
                    stazionePartenza = obj["origine"]?.jsonPrimitive?.contentOrNull,
                    stazioneDestinazione = obj["destinazione"]?.jsonPrimitive?.contentOrNull,
                    ritardoMinuti = ritardo,
                    soppresso = soppresso,
                    ultimaStazioneRilevata = stazioneUltimoRilevamento,
                    fermate = fermate
                )
            )
        } catch (e: Exception) {
            TrainResult.NetworkError(e.message ?: "Errore di rete")
        }
    }
}
