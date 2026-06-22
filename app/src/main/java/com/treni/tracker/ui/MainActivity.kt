package com.treni.tracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.treni.tracker.data.AppDatabase
import com.treni.tracker.data.TrenoMonitorato
import com.treni.tracker.databinding.ActivityMainBinding
import com.treni.tracker.network.TrainCandidate
import com.treni.tracker.network.TrainResult
import com.treni.tracker.network.ViaggiaTrenoClient
import com.treni.tracker.notification.Notifier
import com.treni.tracker.worker.TrainCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: TreniAdapter
    private val client = ViaggiaTrenoClient()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Notifier.creaCanale(this)
        chiediPermessoNotifiche()
        pianificaControlloPeriodico()

        adapter = TreniAdapter(
            emptyList(),
            onRimuovi = { treno -> rimuoviTreno(treno) },
            onApriDettaglio = { treno -> apriDettaglio(treno) }
        )
        binding.recyclerTreni.layoutManager = LinearLayoutManager(this)
        binding.recyclerTreni.adapter = adapter

        val dao = AppDatabase.getInstance(this).trenoDao()
        dao.osservaTreniAttivi().observe(this) { treni ->
            adapter.aggiorna(treni)
            binding.emptyState.visibility = if (treni.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.btnCerca.setOnClickListener { cercaTreno() }

        binding.swipeRefresh.setOnRefreshListener {
            val richiesta = OneTimeWorkRequestBuilder<TrainCheckWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(this).enqueue(richiesta)
            // Lo spinner si ferma da solo dopo un tempo breve: il refresh reale avviene
            // in background e la UI si aggiorna automaticamente via LiveData
            binding.swipeRefresh.postDelayed({ binding.swipeRefresh.isRefreshing = false }, 1500)
        }
    }

    private fun chiediPermessoNotifiche() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun pianificaControlloPeriodico() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 15 minuti è il minimo intervallo consentito da WorkManager per i lavori periodici
        val request = PeriodicWorkRequestBuilder<TrainCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "controllo_treni",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun cercaTreno() {
        val numero = binding.inputNumeroTreno.text?.toString()?.trim()
        if (numero.isNullOrEmpty()) return

        binding.btnCerca.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { client.cercaNumeroTreno(numero) }

            binding.btnCerca.isEnabled = true
            binding.progressBar.visibility = android.view.View.GONE

            when (result) {
                is TrainResult.Success -> {
                    val candidati = result.data
                    if (candidati.size == 1) {
                        confermaTreno(candidati[0])
                    } else {
                        mostraSceltaCandidati(candidati)
                    }
                }
                is TrainResult.NotFound -> {
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this@MainActivity, "Errore di rete. Riprova.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun mostraSceltaCandidati(candidati: List<TrainCandidate>) {
        val labels = candidati.map { "Treno ${it.numero} da ${it.stazionePartenzaNome}" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Scegli la corsa giusta")
            .setItems(labels) { _, index -> confermaTreno(candidati[index]) }
            .show()
    }

    private fun confermaTreno(candidato: TrainCandidate) {
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                client.andamentoTreno(candidato.stazionePartenzaCod, candidato.numero, candidato.timestampMs)
            }

            binding.progressBar.visibility = android.view.View.GONE

            when (result) {
                is TrainResult.Success -> {
                    val stato = result.data
                    val dataCorsa = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(java.util.Date(candidato.timestampMs))

                    val treno = TrenoMonitorato(
                        numeroTreno = candidato.numero,
                        stazionePartenzaCod = candidato.stazionePartenzaCod,
                        stazionePartenzaNome = candidato.stazionePartenzaNome,
                        stazioneDestinazioneNome = stato.stazioneDestinazione,
                        timestampMs = candidato.timestampMs,
                        dataCorsa = dataCorsa
                    )

                    withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(this@MainActivity).trenoDao().inserisci(treno)
                    }

                    // Controllo immediato, senza aspettare il primo ciclo periodico (fino a 15 min)
                    val vincoliRete = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    val richiestaImmediata = OneTimeWorkRequestBuilder<TrainCheckWorker>()
                        .setConstraints(vincoliRete)
                        .build()
                    WorkManager.getInstance(this@MainActivity).enqueue(richiestaImmediata)

                    binding.inputNumeroTreno.text?.clear()
                }
                is TrainResult.NoData -> {
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this@MainActivity, "Errore di rete. Riprova.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun rimuoviTreno(treno: TrenoMonitorato) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@MainActivity).trenoDao().rimuovi(treno.id)
            }
        }
    }

    private fun apriDettaglio(treno: TrenoMonitorato) {
        val intent = android.content.Intent(this, TrenoDetailActivity::class.java).apply {
            putExtra(TrenoDetailActivity.EXTRA_TRENO_ID, treno.id)
            putExtra(TrenoDetailActivity.EXTRA_NUMERO_TRENO, treno.numeroTreno)
            putExtra(TrenoDetailActivity.EXTRA_STAZIONE_PARTENZA_COD, treno.stazionePartenzaCod)
            putExtra(TrenoDetailActivity.EXTRA_STAZIONE_PARTENZA_NOME, treno.stazionePartenzaNome)
            putExtra(TrenoDetailActivity.EXTRA_STAZIONE_DESTINAZIONE_NOME, treno.stazioneDestinazioneNome)
            putExtra(TrenoDetailActivity.EXTRA_TIMESTAMP_MS, treno.timestampMs)
        }
        startActivity(intent)
    }
}
