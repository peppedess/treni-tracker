package com.treni.tracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.treni.tracker.R
import com.treni.tracker.data.TrenoMonitorato

class TreniAdapter(
    private var treni: List<TrenoMonitorato>,
    private val onRimuovi: (TrenoMonitorato) -> Unit
) : RecyclerView.Adapter<TreniAdapter.TrenoViewHolder>() {

    fun aggiorna(nuoviTreni: List<TrenoMonitorato>) {
        treni = nuoviTreni
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): TrenoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_treno, parent, false)
        return TrenoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrenoViewHolder, position: Int) {
        holder.bind(treni[position])
    }

    override fun getItemCount(): Int = treni.size

    inner class TrenoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val numero: android.widget.TextView = itemView.findViewById(R.id.textNumeroTreno)
        private val tratta: android.widget.TextView = itemView.findViewById(R.id.textTratta)
        private val stato: android.widget.TextView = itemView.findViewById(R.id.textStato)
        private val btnRimuovi: android.widget.ImageButton = itemView.findViewById(R.id.btnRimuovi)

        fun bind(treno: TrenoMonitorato) {
            numero.text = treno.numeroTreno
            tratta.text = "${treno.stazionePartenzaNome} → ${treno.stazioneDestinazioneNome ?: "?"}"

            val ritardo = treno.ultimoRitardo
            stato.text = when {
                ritardo == null -> "In attesa di aggiornamento…"
                ritardo > 0 -> "In ritardo di $ritardo min" + (treno.ultimaStazioneNotificata?.let { " · $it" } ?: "")
                ritardo < 0 -> "In anticipo di ${kotlin.math.abs(ritardo)} min" + (treno.ultimaStazioneNotificata?.let { " · $it" } ?: "")
                else -> "In orario" + (treno.ultimaStazioneNotificata?.let { " · $it" } ?: "")
            }

            btnRimuovi.setOnClickListener { onRimuovi(treno) }
        }
    }
}
