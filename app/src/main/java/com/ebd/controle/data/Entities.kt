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
    /** Aluno especial (inclusão): usa o grupo de critérios adaptados na tela de pontos. */
    val especial: Boolean = false,
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

/**
 * Tabela de preços das revistas por categoria (ex.: "Adultos", "Jovens").
 * O preço é editável na tela; a categoria física é o que entra na descrição
 * do lançamento financeiro. Revista digital (PDF) é sempre gratuita.
 */
@Entity(tableName = "revistas_precos")
data class RevistaPreco(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoria: String,
    val preco: Double = 0.0,
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)

/**
 * Entrega de revista a um aluno num trimestre.
 *  - tipo: "FISICA" (gera despesa) ou "DIGITAL" (gratuita)
 *  - categoria/preco: copiados no momento da entrega (histórico fica fixo
 *    mesmo que o preço de tabela mude depois)
 *  - ano/trimestre: identificam o trimestre EBD (1..4)
 * Um registro por aluno por trimestre (uid determinístico evita duplicar).
 */
@Entity(tableName = "revistas_entregas")
data class RevistaEntrega(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alunoId: Long,
    val ano: Int,
    val trimestre: Int,
    val tipo: String = "FISICA",
    val categoria: String = "",
    val preco: Double = 0.0,
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)

/**
 * Catálogo de critérios de pontuação (o "quanto vale cada coisa").
 * É totalmente editável pela tela — como a tabela de preços das revistas.
 *
 *  - nome: rótulo curto que aparece no botão ("Presença", "Pontualidade",
 *    "Visitante", "Alimento café", "Arroz/óleo"...).
 *  - pontos: valor padrão do critério.
 *  - grupo: só organiza a tela. Valores usados:
 *      "REGULAR"  -> alunos regulares (presença, pontualidade, participação...)
 *      "ESPECIAL" -> metas adaptadas (inclusão)
 *      "CAFE"     -> alimento p/ o café (pontuação fixa, valoriza a atitude)
 *      "CESTA"    -> cesta básica / ação social (tabelado por item)
 *  - porQuantidade: se true, o lançamento multiplica pontos × quantidade
 *    (ex.: 3 pacotes de arroz). Se false, é toque único (presença etc.).
 *  - ordem: posição na lista (menor primeiro).
 *  - ativo: permite "arquivar" um critério sem apagar o histórico.
 */
@Entity(tableName = "criterios_pontuacao")
data class CriterioPontuacao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val pontos: Int = 0,
    val grupo: String = "REGULAR",
    val porQuantidade: Boolean = false,
    val ordem: Int = 0,
    val ativo: Boolean = true,
    val uid: String? = null,
    val updatedAt: Long? = null,
    val deleted: Boolean? = null
)

/**
 * Um ponto marcado para um aluno numa data, referente a um critério.
 *  - pontos: valor JÁ calculado no momento da marcação (fica fixo no histórico
 *    mesmo que o critério mude de valor depois — igual ao preço da revista).
 *  - quantidade: nº de unidades (1 para critérios de toque único).
 * uid determinístico "alunoUid:criterioUid:data" -> nunca duplica no sync;
 * marcar de novo o mesmo critério no mesmo dia atualiza a linha (toggle).
 */
@Entity(tableName = "pontos_lancamentos")
data class PontoLancamento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alunoId: Long,
    val criterioId: Long,
    val data: Long,
    val pontos: Int = 0,
    val quantidade: Int = 1,
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
