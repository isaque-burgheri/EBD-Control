package com.ebd.controle.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup local completo — as 10 tabelas, campo a campo, incluindo a identidade de
 * sincronização (`uid`, `updatedAt`, `deleted`) e os ids originais.
 *
 * Duas decisões que valem explicação:
 *
 * 1. **Os ids são preservados, não remapeados.** A restauração esvazia o banco antes de
 *    gravar, então inserir com o id original é seguro e mantém todos os vínculos
 *    (aluno→classe, presença→chamada, ponto→critério) exatos. A versão anterior remapeava
 *    e descartava em silêncio qualquer registro cujo pai não fosse encontrado.
 *
 * 2. **`uid` e `updatedAt` viajam no arquivo.** Sem eles, a restauração gerava uid novo
 *    para cada linha; com o sync ligado, o app subia a base inteira como se fosse nova, a
 *    planilha duplicava tudo e o pull devolvia as linhas antigas — o banco terminava com
 *    duas cópias completas, e a planilha compartilhada ia junto.
 *
 * Registros com exclusão lógica (`deleted = 1`) também são exportados, de propósito: a
 * exclusão precisa continuar se propagando depois da restauração.
 */

const val VERSAO_BACKUP = 3

/** As 10 tabelas do app, em ordem segura para gravação (pais antes dos filhos). */
data class DadosBackup(
    val classes: List<Classe> = emptyList(),
    val alunos: List<Aluno> = emptyList(),
    val chamadas: List<Chamada> = emptyList(),
    val presencas: List<Presenca> = emptyList(),
    val financeiro: List<Financeiro> = emptyList(),
    val visitantes: List<Visitante> = emptyList(),
    val revistasPrecos: List<RevistaPreco> = emptyList(),
    val revistasEntregas: List<RevistaEntrega> = emptyList(),
    val criterios: List<CriterioPontuacao> = emptyList(),
    val pontos: List<PontoLancamento> = emptyList()
) {
    val total: Int
        get() = classes.size + alunos.size + chamadas.size + presencas.size + financeiro.size +
            visitantes.size + revistasPrecos.size + revistasEntregas.size + criterios.size + pontos.size
}

/** Lançada quando o arquivo escolhido não é um backup válido. */
class BackupInvalidoException(mensagem: String) : Exception(mensagem)

/* =========================== EXPORTAÇÃO =========================== */

suspend fun exportarBackup(repo: Repository): String {
    val d = repo.coletarParaBackup()
    val root = JSONObject()
    root.put("versao", VERSAO_BACKUP)
    root.put("geradoEm", System.currentTimeMillis())
    root.put("esquemaRoom", AppDatabase.VERSAO_ESQUEMA)

    root.put("classes", arr(d.classes) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("nome", it.nome).put("faixaEtaria", it.faixaEtaria)
            .put("professores", it.professores)
    })
    root.put("alunos", arr(d.alunos) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("classeId", it.classeId).put("nome", it.nome)
            .put("dataNascimento", it.dataNascimento ?: JSONObject.NULL)
            .put("telefone", it.telefone).put("cargo", it.cargo)
            .put("ativo", it.ativo).put("especial", it.especial)
    })
    root.put("chamadas", arr(d.chamadas) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("classeId", it.classeId).put("data", it.data).put("licao", it.licao)
            .put("oferta", it.oferta).put("dizimos", it.dizimos).put("visitantes", it.visitantes)
    })
    root.put("presencas", arr(d.presencas) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("chamadaId", it.chamadaId).put("alunoId", it.alunoId)
            .put("presente", it.presente).put("biblia", it.biblia).put("revista", it.revista)
    })
    root.put("financeiro", arr(d.financeiro) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("data", it.data).put("tipo", it.tipo).put("categoria", it.categoria)
            .put("valor", it.valor).put("descricao", it.descricao)
            .put("chamadaId", it.chamadaId ?: JSONObject.NULL)
    })
    root.put("visitantes", arr(d.visitantes) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("nome", it.nome).put("telefone", it.telefone).put("data", it.data)
            .put("classeId", it.classeId ?: JSONObject.NULL)
            .put("observacao", it.observacao).put("convertido", it.convertido)
    })
    root.put("revistasPrecos", arr(d.revistasPrecos) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("categoria", it.categoria).put("preco", it.preco)
    })
    root.put("revistasEntregas", arr(d.revistasEntregas) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("alunoId", it.alunoId).put("ano", it.ano).put("trimestre", it.trimestre)
            .put("tipo", it.tipo).put("categoria", it.categoria).put("preco", it.preco)
    })
    root.put("criterios", arr(d.criterios) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("nome", it.nome).put("pontos", it.pontos).put("grupo", it.grupo)
            .put("porQuantidade", it.porQuantidade).put("ordem", it.ordem).put("ativo", it.ativo)
    })
    root.put("pontos", arr(d.pontos) {
        obj(it.id, it.uid, it.updatedAt, it.deleted)
            .put("alunoId", it.alunoId).put("criterioId", it.criterioId).put("data", it.data)
            .put("pontos", it.pontos).put("quantidade", it.quantidade)
    })

    return root.toString(2)
}

private inline fun <T> arr(lista: List<T>, monta: (T) -> JSONObject) =
    JSONArray().apply { lista.forEach { put(monta(it)) } }

private fun obj(id: Long, uid: String?, updatedAt: Long?, deleted: Boolean?) = JSONObject()
    .put("id", id)
    .put("uid", uid ?: JSONObject.NULL)
    .put("updatedAt", updatedAt ?: JSONObject.NULL)
    .put("deleted", deleted ?: false)

/* =========================== IMPORTAÇÃO =========================== */

/**
 * RESTAURA um backup: apaga tudo e recria a partir do arquivo, dentro de uma
 * transação única. Se qualquer registro falhar, nada é aplicado.
 */
suspend fun importarBackup(repo: Repository, json: String): Int {
    val root = JSONObject(json)
    validarBackup(root)
    val dados = lerDados(root)
    repo.restaurarDeBackup(dados)
    return dados.total
}

/**
 * Confere se o JSON tem cara de backup ANTES de qualquer destruição — a restauração
 * esvazia o banco, então o que dá para checar tem de ser checado aqui.
 *
 * Sem isto, um arquivo `{}` passava direto: o banco era esvaziado, nada era importado
 * e a tela ainda dizia "Backup restaurado".
 */
private fun validarBackup(root: JSONObject) {
    if (!root.has("versao")) {
        throw BackupInvalidoException(
            "Este arquivo não é um backup do EBD Controle (falta o campo \"versao\")."
        )
    }
    val versao = root.optInt("versao", 0)
    if (versao !in 1..VERSAO_BACKUP) {
        throw BackupInvalidoException(
            "Backup na versão $versao; este app entende até a $VERSAO_BACKUP. Atualize o app antes de restaurar."
        )
    }

    val classes = root.optJSONArray("classes")
    if (classes == null || classes.length() == 0) {
        throw BackupInvalidoException(
            "O backup não tem nenhuma classe — restaurar apagaria tudo sem repor nada."
        )
    }
    // Um despejo da sincronização usa os mesmos nomes de array, mas identifica a classe
    // por "uid" em vez de "id". Sem esta checagem ele era aceito e todos os alunos
    // acabavam jogados numa única classe.
    val primeira = classes.optJSONObject(0)
    if (primeira != null && !primeira.has("id") && primeira.has("uid")) {
        throw BackupInvalidoException(
            "Este arquivo é um despejo da sincronização, não um backup. Restaurá-lo embaralharia as classes."
        )
    }
}

private fun lerDados(root: JSONObject) = DadosBackup(
    classes = mapa(root, "classes") {
        Classe(id = id(it), nome = it.getString("nome"),
            faixaEtaria = it.optString("faixaEtaria", ""),
            professores = it.optString("professores", ""),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    alunos = mapa(root, "alunos") {
        Aluno(id = id(it), classeId = it.getLong("classeId"), nome = it.getString("nome"),
            dataNascimento = longOuNulo(it, "dataNascimento"),
            telefone = it.optString("telefone", ""), cargo = it.optString("cargo", ""),
            ativo = it.optBoolean("ativo", true), especial = it.optBoolean("especial", false),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    chamadas = mapa(root, "chamadas") {
        Chamada(id = id(it), classeId = it.getLong("classeId"), data = it.getLong("data"),
            licao = it.optInt("licao", 0), oferta = it.optDouble("oferta", 0.0),
            dizimos = it.optDouble("dizimos", 0.0), visitantes = it.optInt("visitantes", 0),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    presencas = mapa(root, "presencas") {
        Presenca(id = id(it), chamadaId = it.getLong("chamadaId"), alunoId = it.getLong("alunoId"),
            presente = it.optBoolean("presente", false), biblia = it.optBoolean("biblia", false),
            revista = it.optBoolean("revista", false),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    financeiro = mapa(root, "financeiro") {
        Financeiro(id = id(it), data = it.getLong("data"), tipo = it.getString("tipo"),
            categoria = it.optString("categoria", ""), valor = it.optDouble("valor", 0.0),
            descricao = it.optString("descricao", ""), chamadaId = longOuNulo(it, "chamadaId"),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    visitantes = mapa(root, "visitantes") {
        Visitante(id = id(it), nome = it.getString("nome"), telefone = it.optString("telefone", ""),
            data = it.getLong("data"), classeId = longOuNulo(it, "classeId"),
            observacao = it.optString("observacao", ""), convertido = it.optBoolean("convertido", false),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    revistasPrecos = mapa(root, "revistasPrecos") {
        RevistaPreco(id = id(it), categoria = it.optString("categoria", ""),
            preco = it.optDouble("preco", 0.0),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    revistasEntregas = mapa(root, "revistasEntregas") {
        RevistaEntrega(id = id(it), alunoId = it.getLong("alunoId"), ano = it.optInt("ano", 0),
            trimestre = it.optInt("trimestre", 0), tipo = it.optString("tipo", "FISICA"),
            categoria = it.optString("categoria", ""), preco = it.optDouble("preco", 0.0),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    criterios = mapa(root, "criterios") {
        CriterioPontuacao(id = id(it), nome = it.getString("nome"), pontos = it.optInt("pontos", 0),
            grupo = it.optString("grupo", "REGULAR"),
            porQuantidade = it.optBoolean("porQuantidade", false),
            ordem = it.optInt("ordem", 0), ativo = it.optBoolean("ativo", true),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    },
    pontos = mapa(root, "pontos") {
        PontoLancamento(id = id(it), alunoId = it.getLong("alunoId"),
            criterioId = it.getLong("criterioId"), data = it.getLong("data"),
            pontos = it.optInt("pontos", 0), quantidade = it.optInt("quantidade", 1).coerceAtLeast(1),
            uid = uid(it), updatedAt = updatedAt(it), deleted = deleted(it))
    }
)

private inline fun <T> mapa(root: JSONObject, chave: String, monta: (JSONObject) -> T): List<T> {
    val lista = root.optJSONArray(chave) ?: return emptyList()
    return (0 until lista.length()).mapNotNull { lista.optJSONObject(it) }.map { monta(it) }
}

// id 0 faz o Room gerar um novo; preservamos o original sempre que o arquivo o traz.
private fun id(o: JSONObject) = o.optLong("id", 0L)
private fun uid(o: JSONObject) = if (o.isNull("uid")) null else o.optString("uid").ifBlank { null }
private fun updatedAt(o: JSONObject) = longOuNulo(o, "updatedAt")
private fun deleted(o: JSONObject) = o.optBoolean("deleted", false)
private fun longOuNulo(o: JSONObject, k: String): Long? =
    if (!o.has(k) || o.isNull(k)) null else o.optLong(k)
