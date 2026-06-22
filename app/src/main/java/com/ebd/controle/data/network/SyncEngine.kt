package com.ebd.controle.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP da sincronização com o Google Apps Script (Web App).
 *
 * Contrato (o mesmo que o botão manual sempre usou):
 *  - Envia o JSON montado por [com.ebd.controle.data.Repository.montarPayloadSync]
 *    no CORPO de um POST.
 *  - O Apps Script mescla na planilha (última alteração vence) e devolve o
 *    conjunto completo já mesclado, que é aplicado por
 *    [com.ebd.controle.data.Repository.aplicarSync].
 *
 * É tolerante ao formato da resposta: aceita tanto o JSON "cru"
 * ({"classes":[...], ...}) quanto embrulhado ({"ok":true,"data":{...}}).
 */
object SyncEngine {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Apps Script responde com 302 para script.googleusercontent.com;
            // seguir redirecionamentos é essencial.
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Envia o payload local e devolve o conjunto mesclado retornado pela planilha. */
    suspend fun enviarEReceber(url: String, payload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON))
                .build()

            client.newCall(req).execute().use { resp ->
                val corpo = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw java.io.IOException("HTTP ${resp.code} ao sincronizar")
                }
                desembrulhar(corpo)
            }
        }

    /** Para celular novo: baixa tudo enviando um payload vazio (a planilha devolve o total). */
    suspend fun baixar(url: String): JSONObject =
        enviarEReceber(url, JSONObject())

    /** Aceita resposta crua ou embrulhada em {ok/status, data}. */
    private fun desembrulhar(corpo: String): JSONObject {
        if (corpo.isBlank()) return JSONObject()
        val raiz = JSONObject(corpo)
        val data = raiz.optJSONObject("data")
        if (data != null) {
            // formato embrulhado; se vier ok=false, propaga a mensagem
            if (raiz.has("ok") && !raiz.optBoolean("ok", true)) {
                throw java.io.IOException(raiz.optString("erro", raiz.optString("message", "Falha na planilha")))
            }
            return data
        }
        return raiz
    }
}
