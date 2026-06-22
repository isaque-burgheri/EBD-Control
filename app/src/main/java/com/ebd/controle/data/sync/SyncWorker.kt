package com.ebd.controle.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ebd.controle.EBDApp

/**
 * Executa UM ciclo de sincronização de mão dupla em segundo plano.
 * É usado por três gatilhos (todos via [SyncScheduler]):
 *  - periódico (a cada 15 min),
 *  - ao abrir o app,
 *  - logo após uma edição local (envio rápido).
 *
 * Lê a URL e a preferência de auto-sync das mesmas SharedPreferences que a
 * tela de Configurações usa. Reaproveita exatamente o caminho do botão manual
 * ([com.ebd.controle.data.Repository.executarSync]).
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, "").orEmpty()

        // Nada configurado ou auto-sync desligado: não é erro, apenas não faz nada.
        if (url.isBlank()) return Result.success()
        if (!prefs.getBoolean(KEY_AUTO, true)) return Result.success()

        val repo = (applicationContext as EBDApp).repository
        return try {
            repo.executarSync(url)
            prefs.edit()
                .putLong(KEY_LAST, System.currentTimeMillis())
                .putBoolean(KEY_LAST_OK, true)
                .putString(KEY_LAST_MSG, "")
                .apply()
            Result.success()
        } catch (e: Exception) {
            prefs.edit()
                .putBoolean(KEY_LAST_OK, false)
                .putString(KEY_LAST_MSG, e.message ?: "falha")
                .apply()
            // tenta de novo algumas vezes (rede instável); depois desiste até o próximo ciclo
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        const val PREFS = "settings"
        const val KEY_URL = "sync_url"
        const val KEY_AUTO = "auto_sync"
        const val KEY_LAST = "last_sync"
        const val KEY_LAST_OK = "last_sync_ok"
        const val KEY_LAST_MSG = "last_sync_msg"
    }
}
