package com.treni.tracker.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

        fermateAdapter = FermateAdapter(emptyList())
        binding.recyclerFermate.layoutManager = LinearLayoutManager(this)
        binding.recyclerFermate.adapter = fermateAdapter

        binding.swipeRefreshDetail.setOnRefreshListener {
            caricaDettagli(stazionePartenzaCod, numeroTreno, timestampMs)
        }

        caricaDettagli(stazionePartenzaCod, numeroTreno, timestampMs)
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
                    binding.textRitardoGlobale.text = when {
                        ritardo > 0 -> "In ritardo di $ritardo min"
                        ritardo < 0 -> "In anticipo di ${kotlin.math.abs(ritardo)} min"
                        else -> "In orario"
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
}
