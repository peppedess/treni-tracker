package com.treni.tracker.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.treni.tracker.R
import com.treni.tracker.data.AppDatabase
import com.treni.tracker.data.TrenoPreferito
import com.treni.tracker.databinding.ActivityTrenoDetailBinding
import com.treni.tracker.network.TrainResult
import com.treni.tracker.network.ViaggiaTrenoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrenoDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRENO_ID = "extra_treno_id"
        const val EXTRA_NUMERO_TRENO = "extra_numero_treno"
        const val EXTRA_STAZIONE_PARTENZA_COD = "extra_stazione_partenza_cod"
        const val EXTRA_STAZIONE_PARTENZA_NOME = "extra_stazione_partenza_nome"
        const val EXTRA_STAZIONE_DESTINAZIONE_NOME = "extra_stazione_destinazione_nome"
        const val EXTRA_TIMESTAMP_MS = "extra_timestamp_ms"
    }

    private lateinit var binding: ActivityTrenoDetailBinding
    private lateinit var fermateAdapter: FermateAdapter
    private val client = ViaggiaTrenoClient()

    private var numeroTrenoCorrente: String = ""
    private var trattaCorrente: String = ""
    private var statoCorrente: String = "In attesa di aggiornamento"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrenoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val numeroTreno = intent.getStringExtra(EXTRA_NUMERO_TRENO) ?: ""
        val stazionePartenzaCod = intent.getStringExtra(EXTRA_STAZIONE_PARTENZA_COD) ?: ""
        val stazionePartenzaNome = intent.getStringExtra(EXTRA_STAZIONE_PARTENZA_NOME) ?: ""
        val stazioneDestinazioneNome = intent.getStringExtra(EXTRA_STAZIONE_DESTINAZIONE_NOME)
        val timestampMs = intent.getLongExtra(EXTRA_TIMESTAMP_MS, 0L)

        binding.toolbar.title = "Treno $numeroTreno"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.textTratta.text = "$stazionePartenzaNome → ${stazioneDestinazioneNome ?: "?"}"

        numeroTrenoCorrente = numeroTreno
        trattaCorrente = "$stazionePartenzaNome → ${stazioneDestinazioneNome ?: "?"}"

        binding.btnCondividi.setOnClickListener { condividiStato() }

        configuraPreferito(numeroTreno, stazionePartenzaCod, stazionePartenzaNome, stazioneDestinazioneNome)
        caricaStatistiche(numeroTreno)

        fermateAdapter = FermateAdapter(emptyList())
        binding.recyclerFermate.layoutManager = LinearLayoutManager(this)
        binding.recyclerFermate.adapter = fermateAdapter

        binding.swipeRefreshDetail.setOnRefreshListener {
            caricaDettagli(stazionePartenzaCod, numeroTreno, timestampMs)
        }

        caricaDettagli(stazionePartenzaCod, numeroTreno, timestampMs)
    }

    private fun configuraPreferito(
        numeroTreno: String,
        stazionePartenzaCod: String,
        stazionePartenzaNome: String,
        stazioneDestinazioneNome: String?
    ) {
        val dao = AppDatabase.getInstance(this).trenoDao()

        lifecycleScope.launch {
            var giaPreferito = withContext(Dispatchers.IO) {
                dao.contaPreferito(numeroTreno, stazionePartenzaCod) > 0
            }
            aggiornaIconaPreferito(giaPreferito)

            binding.btnPreferito.setOnClickListener {
                lifecycleScope.launch {
                    if (giaPreferito) {
                        rimuoviDaiPreferiti(numeroTreno, stazionePartenzaCod)
                        giaPreferito = false
                        aggiornaIconaPreferito(false)
                        Toast.makeText(this@TrenoDetailActivity, "Rimosso dai preferiti", Toast.LENGTH_SHORT).show()
                    } else {
                        withContext(Dispatchers.IO) {
                            dao.inserisciPreferito(
                                TrenoPreferito(
                                    numeroTreno = numeroTreno,
                                    stazionePartenzaCod = stazionePartenzaCod,
                                    stazionePartenzaNome = stazionePartenzaNome,
                                    stazioneDestinazioneNome = stazioneDestinazioneNome
                                )
                            )
                        }
                        giaPreferito = true
                        aggiornaIconaPreferito(true)
                        Toast.makeText(this@TrenoDetailActivity, "Aggiunto ai preferiti", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun rimuoviDaiPreferiti(numeroTreno: String, stazionePartenzaCod: String) {
        val dao = AppDatabase.getInstance(this).trenoDao()
        withContext(Dispatchers.IO) {
            dao.rimuoviPreferitoPerChiave(numeroTreno, stazionePartenzaCod)
        }
    }

    private fun aggiornaIconaPreferito(attivo: Boolean) {
        binding.btnPreferito.setImageResource(
            if (attivo) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    private fun caricaStatistiche(numeroTreno: String) {
        val dao = AppDatabase.getInstance(this).trenoDao()
        lifecycleScope.launch {
            val ritardi = withContext(Dispatchers.IO) { dao.getRitardiPerCorsa(numeroTreno) }
            if (ritardi.isEmpty()) {
                binding.cardStatistiche.visibility = android.view.View.GONE
                return@launch
            }

            val numCorse = ritardi.size
            val ritardoMedio = ritardi.map { it.ritardoMax }.average()
            val corseInRitardo = ritardi.count { it.ritardoMax >= 5 }
            val percentualeRitardo = (corseInRitardo * 100) / numCorse

            binding.cardStatistiche.visibility = android.view.View.VISIBLE
            binding.textStatistiche.text =
                "Su $numCorse corse monitorate:\n" +
                "• Ritardo medio: ${"%.1f".format(ritardoMedio)} min\n" +
                "• In ritardo (≥5 min): $percentualeRitardo% delle volte"
        }
    }

    private fun caricaDettagli(stazionePartenzaCod: String, numeroTreno: String, timestampMs: Long) {
        binding.progressBarDetail.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                client.andamentoTreno(stazionePartenzaCod, numeroTreno, timestampMs)
            }

            binding.progressBarDetail.visibility = android.view.View.GONE
            binding.swipeRefreshDetail.isRefreshing = false

            when (result) {
                is TrainResult.Success -> {
                    val stato = result.data
                    val ritardo = stato.ritardoMinuti
                    val testoStato = when {
                        ritardo > 0 -> "In ritardo di $ritardo min"
                        ritardo < 0 -> "In anticipo di ${kotlin.math.abs(ritardo)} min"
                        else -> "In orario"
                    }
                    binding.textRitardoGlobale.text = testoStato
                    statoCorrente = testoStato
                    if (stato.ultimaStazioneRilevata != null) {
                        statoCorrente += " (ultima fermata: ${stato.ultimaStazioneRilevata})"
                    }
                    fermateAdapter.aggiorna(stato.fermate)
                }
                is TrainResult.NoData -> {
                    Toast.makeText(this@TrenoDetailActivity, result.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this@TrenoDetailActivity, "Errore di rete. Riprova.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun condividiStato() {
        val testo = "🚆 Treno $numeroTrenoCorrente\n$trattaCorrente\n$statoCorrente\n\n(via Treni Tracker)"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, testo)
        }
        startActivity(android.content.Intent.createChooser(intent, "Condividi stato treno"))
    }
}
