package com.treni.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treni_monitorati")
data class TrenoMonitorato(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val numeroTreno: String,
    val stazionePartenzaCod: String,
    val stazionePartenzaNome: String,
    val stazioneDestinazioneNome: String?,
    val timestampMs: Long,
    val dataCorsa: String,
    val attivo: Boolean = true,
    val ultimoRitardo: Int? = null,
    val ultimaStazioneNotificata: String? = null,
    val creatoIl: Long = System.currentTimeMillis()
)
