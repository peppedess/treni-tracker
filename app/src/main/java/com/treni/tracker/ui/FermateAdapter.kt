package com.treni.tracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.treni.tracker.R
import com.treni.tracker.network.StopInfo

class FermateAdapter(
    private var fermate: List<StopInfo>
) : RecyclerView.Adapter<FermateAdapter.FermataViewHolder>() {

    fun aggiorna(nuoveFermate: List<StopInfo>) {
        fermate = nuoveFermate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): FermataViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fermata, parent, false)
        return FermataViewHolder(view)
    }

    override fun onBindViewHolder(holder: FermataViewHolder, position: Int) {
        holder.bind(fermate[position], isUltima = position == fermate.size - 1)
    }

    override fun getItemCount(): Int = fermate.size

    inner class FermataViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nomeStazione: android.widget.TextView = itemView.findViewById(R.id.textNomeStazione)
        private val rigaRitardo: android.widget.TextView = itemView.findViewById(R.id.textRitardoFermata)
        private val dotIndicatore: View = itemView.findViewById(R.id.dotIndicatore)
        private val lineaConnettore: View = itemView.findViewById(R.id.lineaConnettore)

        fun bind(fermata: StopInfo, isUltima: Boolean) {
            val context = itemView.context
            nomeStazione.text = fermata.stazione

            val ritardo = fermata.ritardo
            rigaRitardo.text = when {
                !fermata.passata -> "In attesa"
                ritardo == null -> "—"
                ritardo > 0 -> "+$ritardo min"
                ritardo < 0 -> "$ritardo min"
                else -> "In orario"
            }

            val colore = when {
                !fermata.passata -> ContextCompat.getColor(context, R.color.status_pending_grey)
                ritardo == null -> ContextCompat.getColor(context, R.color.status_pending_grey)
                ritardo > 0 -> ContextCompat.getColor(context, R.color.status_late)
                ritardo < 0 -> ContextCompat.getColor(context, R.color.status_early)
                else -> ContextCompat.getColor(context, R.color.status_ontime)
            }

            dotIndicatore.backgroundTintList = android.content.res.ColorStateList.valueOf(colore)
            lineaConnettore.visibility = if (isUltima) View.INVISIBLE else View.VISIBLE
        }
    }
}
