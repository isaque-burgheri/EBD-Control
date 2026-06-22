package com.ebd.controle.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * Campos de sincronização presentes em TODAS as tabelas:
 *  - uid: código permanente e único do registro (igual em todos os celulares)
 *  - updatedAt: carimbo da última alteração (epoch millis) -> "última alteração vence"
 *  - deleted: exclusão lógica (a linha não some, fica marcada) para a exclusão se propagar
 * São anuláveis para a migração ser segura; o app sempre preenche ao salvar.
 */

@Entity(tableName = "classes")
data class Classe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val faixaEtaria: String = "",
    val professores: String = "",
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)

@Entity(tableName = "alunos")
data class Aluno(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classeId: Long,
    val nome: String,
    val dataNascimento: Long? = null,
    val telefone: String = "",
    val cargo: String = "",
    val ativo: Boolean = true,
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)

@Entity(tableName = "chamadas")
data class Chamada(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classeId: Long,
    val data: Long,
    val licao: Int = 0,
    val oferta: Double = 0.0,
    val dizimos: Double = 0.0,
    val visitantes: Int = 0,
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)

@Entity(tableName = "presencas")
data class Presenca(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chamadaId: Long,
    val alunoId: Long,
    val presente: Boolean = false,
    val biblia: Boolean = false,
    val revista: Boolean = false,
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)

@Entity(tableName = "financeiro")
data class Financeiro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val tipo: String,
    val categoria: String,
    val valor: Double,
    val descricao: String = "",
    val chamadaId: Long? = null,
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)

@Entity(tableName = "visitantes")
data class Visitante(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val telefone: String = "",
    val data: Long,
    val classeId: Long? = null,
    val observacao: String = "",
    val convertido: Boolean = false,
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)
