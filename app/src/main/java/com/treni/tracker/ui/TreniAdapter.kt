package com.treni.tracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.treni.tracker.R
import com.treni.tracker.data.TrenoMonitorato

class TreniAdapter(
    private var treni: List<TrenoMonitorato>,
    private val onRimuovi: (TrenoMonitorato) -> Unit,
    private val onApriDettaglio: (TrenoMonitorato) -> Unit
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
        private val chipStato: Chip = itemView.findViewById(R.id.chipStato)
        private val statusStripe: View = itemView.findViewById(R.id.statusStripe)
        private val btnRimuovi: android.widget.ImageButton = itemView.findViewById(R.id.btnRimuovi)

        fun bind(treno: TrenoMonitorato) {
            val context = itemView.context
            numero.text = treno.numeroTreno
            tratta.text = "${treno.stazionePartenzaNome} → ${treno.stazioneDestinazioneNome ?: "?"}"

            val ritardo = treno.ultimoRitardo
            val coloreStato: Int
            val testoChip: String

            when {
                ritardo == null -> {
                    testoChip = "In attesa"
                    coloreStato = getColorAttr(context, com.google.android.material.R.attr.colorOutline)
                }
                ritardo > 0 -> {
                    testoChip = "+$ritardo min"
                    coloreStato = androidx.core.content.ContextCompat.getColor(context, R.color.status_late)
                }
                ritardo < 0 -> {
                    testoChip = "${ritardo} min"
                    coloreStato = androidx.core.content.ContextCompat.getColor(context, R.color.status_early)
                }
                else -> {
                    testoChip = "In orario"
                    coloreStato = androidx.core.content.ContextCompat.getColor(context, R.color.status_ontime)
                }
            }

            chipStato.text = testoChip
            chipStato.chipBackgroundColor = android.content.res.ColorStateList.valueOf(coloreStato)
            chipStato.setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.white))
            statusStripe.setBackgroundColor(coloreStato)

            stato.text = treno.ultimaStazioneNotificata?.let { "Ultima fermata rilevata: $it" }
                ?: "In attesa del primo aggiornamento…"

            btnRimuovi.setOnClickListener { onRimuovi(treno) }
            itemView.setOnClickListener { onApriDettaglio(treno) }
        }

        private fun getColorAttr(context: android.content.Context, attr: Int): Int {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(attr, typedValue, true)
            return typedValue.data
        }
    }
}
