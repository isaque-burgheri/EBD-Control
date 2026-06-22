package com.ebd.controle.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Centraliza o agendamento da sincronização automática (WorkManager).
 *
 *  - [agendarPeriodico]: roda a cada 15 min (mínimo do Android) quando houver
 *    rede, mantendo a planilha e o app em dia mesmo sem ninguém abrir o app.
 *  - [sincronizarAgora]: dispara assim que possível — usado ao ABRIR o app.
 *  - [sincronizarEmBreve]: espera ~15 s e agrupa rajadas de edições numa única
 *    sincronização (debounce) — usado logo após cada gravação local, para que
 *    a mudança apareça para os outros em segundos.
 */
object SyncScheduler {

    private const val PERIODICO = "ebd_sync_periodico"
    private const val AGORA = "ebd_sync_agora"
    private const val EM_BREVE = "ebd_sync_em_breve"

    private val somenteComRede = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun agendarPeriodico(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(somenteComRede)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun sincronizarAgora(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(somenteComRede)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(AGORA, ExistingWorkPolicy.REPLACE, req)
    }

    fun sincronizarEmBreve(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(somenteComRede)
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(EM_BREVE, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelarPeriodico(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODICO)
    }
}
