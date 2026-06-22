package com.ebd.controle.data

import android.app.Application
import android.content.Context
import androidx.room.InvalidationTracker
import com.ebd.controle.data.network.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncStatus { OCIOSO, SINCRONIZANDO, OK, ERRO }

/**
 * Orquestra a sincronização AUTOMÁTICA de mão dupla com o Google Sheets.
 *
 * Dispara sozinho:
 *   • ao abrir / voltar para o app (primeiro plano);
 *   • periodicamente, enquanto o app está aberto (rede de segurança);
 *   • alguns segundos após qualquer alteração local (debounce).
 *
 * Como toda sincronização ENVIA o que mudou e APLICA o que voltou (merge por uid,
 * "última alteração vence"), qualquer outro celular recebe as novidades sozinho —
 * sem precisar mais do antigo "Baixar da nuvem".
 *
 * Tudo é controlado pela preferência "auto_sync" (ligada por padrão). O botão
 * "Sincronizar agora" força uma rodada mesmo com a automática desligada.
 */
class SyncManager(
    app: Application,
    private val repo: Repository,
    private val db: AppDatabase
) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()

    private val _status = MutableStateFlow(SyncStatus.OCIOSO)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _ultimaSync = MutableStateFlow(prefs.getLong(PREF_ULTIMA, 0L))
    val ultimaSync: StateFlow<Long> = _ultimaSync.asStateFlow()

    private var jobPeriodico: Job? = null
    private var jobDebounce: Job? = null

    /**
     * Janela em que ignoramos as notificações de mudança do Room — elas são apenas
     * o reflexo do que ACABAMOS de aplicar vindo da nuvem. Sem isso, aplicar dados
     * remotos dispararia um novo sync (eco), e por aí vai.
     */
    @Volatile
    private var fimJanelaRemota = 0L

    /** Registra o observador de mudanças do banco. Chamar uma única vez, no boot. */
    fun iniciar() {
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(TABELAS) {
                override fun onInvalidated(tables: Set<String>) {
                    if (System.currentTimeMillis() < fimJanelaRemota) return
                    if (!autoAtivo()) return
                    agendarPorMudanca()
                }
            }
        )
    }

    /* ----------------------- gatilhos automáticos ----------------------- */

    /** App entrou em primeiro plano: sincroniza agora e liga o backstop periódico. */
    fun aoAbrir() {
        if (!autoAtivo()) return
        dispararInterno()
        iniciarPeriodico()
    }

    /** App saiu de primeiro plano: encerra o backstop periódico. */
    fun aoFechar() {
        jobPeriodico?.cancel()
    }

    private fun iniciarPeriodico() {
        if (jobPeriodico?.isActive == true) return
        jobPeriodico = escopo.launch {
            while (isActive) {
                delay(INTERVALO_MS)
                if (autoAtivo()) sincronizar(manual = false)
            }
        }
    }

    @Synchronized
    private fun agendarPorMudanca() {
        jobDebounce?.cancel()
        jobDebounce = escopo.launch {
            delay(DEBOUNCE_MS)
            sincronizar(manual = false)
        }
    }

    private fun dispararInterno() {
        escopo.launch { sincronizar(manual = false) }
    }

    /* --------------------- gatilho manual (botão) ----------------------- */

    /** "Sincronizar agora": força uma rodada imediata, mesmo com a automática desligada. */
    fun disparar(manual: Boolean = true, onResult: ((Boolean, String) -> Unit)? = null) {
        escopo.launch {
            val (ok, msg) = sincronizar(manual)
            onResult?.invoke(ok, msg)
        }
    }

    /* ----------------------------- núcleo ------------------------------- */

    suspend fun sincronizar(manual: Boolean): Pair<Boolean, String> = trava.withLock {
        val url = prefs.getString(PREF_URL, "")?.takeIf { it.isNotBlank() }
            ?: return@withLock false to "Configure a URL da planilha primeiro"
        if (!manual && !autoAtivo()) return@withLock false to "Sincronização automática desligada"

        _status.value = SyncStatus.SINCRONIZANDO
        return@withLock try {
            val payload = repo.montarPayloadSync()
            val dados = SyncEngine.enviarEReceber(url, payload)

            // Marca a janela de "eco" em volta da aplicação dos dados remotos.
            fimJanelaRemota = System.currentTimeMillis() + JANELA_REMOTA_MS
            repo.aplicarSync(dados)
            fimJanelaRemota = System.currentTimeMillis() + JANELA_REMOTA_MS

            val agora = System.currentTimeMillis()
            prefs.edit().putLong(PREF_ULTIMA, agora).apply()
            _ultimaSync.value = agora
            _status.value = SyncStatus.OK
            true to "Sincronizado com sucesso!"
        } catch (e: Exception) {
            _status.value = SyncStatus.ERRO
            false to "Falha: ${e.message ?: "erro desconhecido"}"
        }
    }

    private fun autoAtivo() = prefs.getBoolean(PREF_AUTO, true)

    companion object {
        private const val PREF_URL = "sync_url"
        private const val PREF_AUTO = "auto_sync"
        private const val PREF_ULTIMA = "ultima_sync"

        private const val INTERVALO_MS = 15 * 60_000L  // backstop a cada 15 min (app aberto)
        private const val DEBOUNCE_MS = 2_500L         // após uma alteração local
        private const val JANELA_REMOTA_MS = 2_000L    // ignora o eco do que acabou de aplicar

        private val TABELAS = arrayOf(
            "classes", "alunos", "chamadas", "presencas", "financeiro", "visitantes"
        )
    }
}
