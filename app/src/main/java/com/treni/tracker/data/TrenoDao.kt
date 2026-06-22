package com.treni.tracker.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TrenoDao {

    @Insert
    suspend fun inserisci(treno: TrenoMonitorato): Long

    @Update
    suspend fun aggiorna(treno: TrenoMonitorato)

    @Query("SELECT * FROM treni_monitorati WHERE attivo = 1 ORDER BY creatoIl DESC")
    fun osservaTreniAttivi(): LiveData<List<TrenoMonitorato>>

    @Query("SELECT * FROM treni_monitorati WHERE attivo = 1 ORDER BY creatoIl DESC")
    suspend fun getTreniAttivi(): List<TrenoMonitorato>

    @Query("UPDATE treni_monitorati SET attivo = 0 WHERE id = :id")
    suspend fun rimuovi(id: Long)

    @Query("UPDATE treni_monitorati SET ultimoRitardo = :ritardo, ultimaStazioneNotificata = :stazione WHERE id = :id")
    suspend fun aggiornaStato(id: Long, ritardo: Int, stazione: String?)

    // --- Treni preferiti ---

    @Insert
    suspend fun inserisciPreferito(preferito: TrenoPreferito): Long

    @Query("SELECT * FROM treni_preferiti ORDER BY creatoIl DESC")
    fun osservaPreferiti(): LiveData<List<TrenoPreferito>>

    @Query("DELETE FROM treni_preferiti WHERE id = :id")
    suspend fun rimuoviPreferito(id: Long)

    @Query("DELETE FROM treni_preferiti WHERE numeroTreno = :numero AND stazionePartenzaCod = :codPartenza")
    suspend fun rimuoviPreferitoPerChiave(numero: String, codPartenza: String)

    @Query("SELECT COUNT(*) FROM treni_preferiti WHERE numeroTreno = :numero AND stazionePartenzaCod = :codPartenza")
    suspend fun contaPreferito(numero: String, codPartenza: String): Int

    // --- Storico rilevamenti ---

    @Insert
    suspend fun inserisciRilevamento(rilevamento: RilevamentoStorico)

    @Query("SELECT * FROM rilevamenti_storici WHERE numeroTreno = :numero ORDER BY timestampRilevamento DESC")
    suspend fun getStoricoTreno(numero: String): List<RilevamentoStorico>

    // Ritardo massimo registrato per ogni corsa (raggruppato per data), utile per le statistiche
    @Query("""
        SELECT dataCorsa, MAX(ritardoMinuti) as ritardoMax
        FROM rilevamenti_storici
        WHERE numeroTreno = :numero
        GROUP BY dataCorsa
        ORDER BY dataCorsa DESC
        LIMIT 30
    """)
    suspend fun getRitardiPerCorsa(numero: String): List<RitardoPerCorsa>
}

data class RitardoPerCorsa(
    val dataCorsa: String,
    val ritardoMax: Int
)
