package com.treni.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rilevamenti_storici")
data class RilevamentoStorico(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val numeroTreno: String,
    val ritardoMinuti: Int,
    val dataCorsa: String,
    val timestampRilevamento: Long = System.currentTimeMillis()
)
