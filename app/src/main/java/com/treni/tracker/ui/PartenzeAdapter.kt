package com.treni.tracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.treni.tracker.R
import com.treni.tracker.network.PartenzaTreno

class PartenzeAdapter(
    private var partenze: List<PartenzaTreno>,
    private val onAggiungi: (PartenzaTreno) -> Unit
) : RecyclerView.Adapter<PartenzeAdapter.PartenzaViewHolder>() {

    fun aggiorna(nuove: List<PartenzaTreno>) {
        partenze = nuove
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): PartenzaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_partenza, parent, false)
        return PartenzaViewHolder(view)
    }

    override fun onBindViewHolder(holder: PartenzaViewHolder, position: Int) {
        holder.bind(partenze[position])
    }

    override fun getItemCount(): Int = partenze.size

    inner class PartenzaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val numero: android.widget.TextView = itemView.findViewById(R.id.textNumeroPartenza)
        private val destinazione: android.widget.TextView = itemView.findViewById(R.id.textDestinazione)
        private val orario: android.widget.TextView = itemView.findViewById(R.id.textOrario)
        private val btnAggiungi: android.widget.ImageButton = itemView.findViewById(R.id.btnAggiungiPartenza)

        fun bind(partenza: PartenzaTreno) {
            numero.text = "Treno ${partenza.numeroTreno}"
            destinazione.text = "→ ${partenza.destinazione}"
            orario.text = partenza.orarioPartenza
            btnAggiungi.setOnClickListener { onAggiungi(partenza) }
            itemView.setOnClickListener { onAggiungi(partenza) }
        }
    }
}
