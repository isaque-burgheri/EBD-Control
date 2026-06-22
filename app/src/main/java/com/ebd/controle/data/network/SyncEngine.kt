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
 * Camada de rede da sincronização.
 *
 * Conversa com o Web App do Google Apps Script (doGet / doPost).
 *
 * Detalhe importante: o Apps Script SEMPRE responde com um 302 para um endereço
 * em `script.googleusercontent.com`, e o corpo de verdade só aparece quando esse
 * endereço é acessado via GET. O OkHttp já segue esse redirecionamento
 * automaticamente (inclusive trocando POST -> GET no 302), então recebemos o JSON
 * final sem precisar tratar o redirect na mão.
 */
object SyncEngine {

    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val TIPO_JSON = "application/json; charset=utf-8".toMediaType()

    /** POST: envia o payload local e devolve os dados mesclados que voltam da planilha. */
    suspend fun enviarEReceber(url: String, payload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(TIPO_JSON))
                .build()
            executar(req)
        }

    /** GET: baixa tudo o que está na planilha. */
    suspend fun baixar(url: String): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).get().build()
            executar(req)
        }

    private fun executar(req: Request): JSONObject {
        cliente.newCall(req).execute().use { resp ->
            val corpo = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Erro de rede (HTTP ${resp.code})")
            }
            if (corpo.isBlank()) return JSONObject()

            val json = try {
                JSONObject(corpo)
            } catch (e: Exception) {
                // Normalmente acontece quando a URL está errada e o Google devolve
                // uma página HTML de login em vez do JSON.
                throw IllegalStateException("Resposta inesperada da planilha. Confira a URL do Apps Script.")
            }

            // doPost devolve { ok, dados }; doGet devolve os dados direto.
            if (json.has("ok") && !json.optBoolean("ok", true)) {
                throw IllegalStateException(json.optString("erro", "Erro no servidor"))
            }
            return if (json.has("dados")) json.getJSONObject("dados") else json
        }
    }
}
