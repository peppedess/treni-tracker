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
}
