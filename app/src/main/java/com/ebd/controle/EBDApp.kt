package com.ebd.controle

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ebd.controle.data.Aluno
import com.ebd.controle.data.AppDatabase
import com.ebd.controle.data.Classe
import com.ebd.controle.data.Repository
import com.ebd.controle.data.sync.SyncScheduler
import com.ebd.controle.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EBDApp : Application(), DefaultLifecycleObserver {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super<android.app.Application>.onCreate()
        repository = Repository(AppDatabase.get(this))

        // Mão dupla automática: toda gravação local agenda um envio rápido
        // (agrupado) para a planilha, sem precisar do botão.
        repository.onLocalChange = { SyncScheduler.sincronizarEmBreve(this) }

        // Sincroniza sempre que o app vai para o primeiro plano (abrir/retomar).
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Mantém em dia em segundo plano (a cada 15 min), se já houver URL.
        val prefs = getSharedPreferences(SyncWorker.PREFS, Context.MODE_PRIVATE)
        val temUrl = !prefs.getString(SyncWorker.KEY_URL, "").isNullOrBlank()
        if (temUrl) SyncScheduler.agendarPeriodico(this)

        CoroutineScope(Dispatchers.IO).launch { semearSeVazio() }
    }

    /** Chamado quando o app entra em primeiro plano: puxa/empurra o mais recente. */
    override fun onStart(owner: LifecycleOwner) {
        SyncScheduler.sincronizarAgora(this)
    }

    /** Na primeira execução, cria as classes e alunos de exemplo (da sua planilha). */
    private suspend fun semearSeVazio() {
        if (repository.totalClasses() > 0) return

        val seeds = listOf(
            Triple("CRIANÇAS", "Crianças 2 a 5 anos" to "Mayara",
                listOf("Mayara", "Allana", "Gael", "Elena", "Noa", "Maete", "Maysa", "Estrela")),
            Triple("SENHORAS", "Senhoras" to "Katia | Eunice",
                listOf("Katia", "Eunice", "Camila", "Edna", "Tatiane", "Jaciara", "Maely",
                    "Claudia", "Favila", "Jussara", "Mirtes", "Luciene", "Ilza")),
            Triple("JOVENS", "Jovens 16 a 18 anos" to "Davi & Wanderson",
                listOf("Cida", "Wanderson", "Davi Lucas", "Yuri", "Nerine", "Kelly", "Raquel",
                    "Moiseis", "Laura", "Keren", "Kerolyn", "Davi Azafe", "Paulo", "Pedro",
                    "Marcos", "Ygride", "Juliano", "Andressa", "Gabriely", "Beatriz")),
            Triple("SENHORES", "Senhores" to "Marcos & Josadaque",
                listOf("Enoque Jose", "Julio", "Marcos da Hora", "Arnaldo", "Almerindo", "Messias",
                    "Walter", "Josadaque", "Antonio", "Emerson", "Cicero", "Alexandre",
                    "Francinaldo", "Isaque"))
        )

        for ((nome, meta, alunos) in seeds) {
            val classeId = repository.salvarClasse(
                Classe(nome = nome, faixaEtaria = meta.first, professores = meta.second)
            )
            alunos.forEach { repository.salvarAluno(Aluno(classeId = classeId, nome = it)) }
        }
    }
}
