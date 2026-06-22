package com.ebd.controle.data.network

import com.ebd.controle.data.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

data class SyncPayload(
    val classes: List<Classe>,
    val alunos: List<Aluno>,
    val chamadas: List<Chamada>,
    val financeiro: List<Financeiro>,
    val visitantes: List<Visitante>
)

interface SyncApi {
    @POST
    suspend fun uploadDados(
        @Url url: String,
        @Body payload: SyncPayload
    ): Response<Unit>
}
