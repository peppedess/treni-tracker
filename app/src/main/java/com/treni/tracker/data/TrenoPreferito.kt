package com.treni.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treni_preferiti")
data class TrenoPreferito(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val numeroTreno: String,
    val stazionePartenzaCod: String,
    val stazionePartenzaNome: String,
    val stazioneDestinazioneNome: String?,
    val creatoIl: Long = System.currentTimeMillis()
)
