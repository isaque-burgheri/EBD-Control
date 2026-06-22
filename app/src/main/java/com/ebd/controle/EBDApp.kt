package com.ebd.controle

import android.app.Application
import com.ebd.controle.data.Aluno
import com.ebd.controle.data.AppDatabase
import com.ebd.controle.data.Classe
import com.ebd.controle.data.Repository
import com.ebd.controle.data.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EBDApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var syncManager: SyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repository = Repository(db)
        syncManager = SyncManager(this, repository, db)
        syncManager.iniciar()
        CoroutineScope(Dispatchers.IO).launch { semearSeVazio() }
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
