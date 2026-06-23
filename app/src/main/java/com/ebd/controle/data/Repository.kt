package com.ebd.controle.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class Repository(private val db: AppDatabase) {
    private val classeDao = db.classeDao()
    private val alunoDao = db.alunoDao()
    private val chamadaDao = db.chamadaDao()
    private val presencaDao = db.presencaDao()
    private val financeiroDao = db.financeiroDao()
    private val visitanteDao = db.visitanteDao()

    private fun novoUid() = UUID.randomUUID().toString()
    private fun agora() = System.currentTimeMillis()

    // ---------------- Classes ----------------
    val classes = classeDao.observarTodas()
    suspend fun listarClasses() = classeDao.listarTodas()
    suspend fun salvarClasse(c: Classe): Long {
        val t = agora()
        return if (c.id == 0L) classeDao.inserir(c.copy(uid = c.uid ?: novoUid(), updatedAt = t, deleted = false))
        else { classeDao.atualizar(c.copy(uid = c.uid ?: novoUid(), updatedAt = t)); c.id }
    }
    suspend fun deletarClasse(c: Classe) { classeDao.atualizar(c.copy(deleted = true, updatedAt = agora())) }

    // ---------------- Alunos ----------------
    val alunos = alunoDao.observarTodos()
    fun alunosPorClasse(classeId: Long) = alunoDao.observarPorClasse(classeId)
    suspend fun listarAlunosPorClasse(classeId: Long) = alunoDao.listarPorClasse(classeId)
    suspend fun listarTodosAlunos() = alunoDao.listarTodos()
    suspend fun salvarAluno(a: Aluno): Long {
        val t = agora()
        return if (a.id == 0L) alunoDao.inserir(a.copy(uid = a.uid ?: novoUid(), updatedAt = t, deleted = false))
        else { alunoDao.atualizar(a.copy(uid = a.uid ?: novoUid(), updatedAt = t)); a.id }
    }
    suspend fun deletarAluno(a: Aluno) { alunoDao.atualizar(a.copy(deleted = true, updatedAt = agora())) }

    // ---------------- Chamadas / presenças ----------------
    val chamadas = chamadaDao.observarTodas()
    suspend fun chamadasDaClasse(classeId: Long) = chamadaDao.listarPorClasse(classeId)
    suspend fun ultimaChamada() = chamadaDao.ultima()
    suspend fun presencasDaChamada(chamadaId: Long) = presencaDao.listarPorChamada(chamadaId)
    suspend fun contarPresentes(chamadaId: Long) = presencaDao.contarPresentes(chamadaId)
    suspend fun contarTotal(chamadaId: Long) = presencaDao.contarTotal(chamadaId)

    // usados pelo backup local
    suspend fun listarTodasChamadas() = chamadaDao.listarTodas()
    suspend fun listarTodasPresencas() = presencaDao.todosIncl().filter { it.deleted != true }
    suspend fun inserirChamada(c: Chamada) =
        chamadaDao.inserir(c.copy(uid = c.uid ?: novoUid(), updatedAt = agora(), deleted = false))
    suspend fun inserirPresencas(lista: List<Presenca>) =
        presencaDao.inserirVarias(lista.map { it.copy(uid = it.uid ?: novoUid(), updatedAt = agora(), deleted = false) })

    suspend fun salvarChamada(chamada: Chamada, presencas: List<Presenca>): Long {
        val t = agora()
        val (ini, fim) = chamada.data.intervaloDoDiaUtc()
        val existente = chamadaDao.buscarNoDia(chamada.classeId, ini, fim)
        val chamadaUid = existente?.uid ?: chamada.uid ?: novoUid()
        val cid: Long = if (existente == null) {
            chamadaDao.inserir(chamada.copy(uid = chamadaUid, updatedAt = t, deleted = false))
        } else {
            chamadaDao.atualizar(chamada.copy(id = existente.id, uid = chamadaUid, updatedAt = t, deleted = false))
            existente.id
        }

        // Presenças: um registro por aluno por chamada (uid determinístico -> nunca duplica)
        for (p in presencas) {
            val alunoUid = alunoDao.porId(p.alunoId)?.uid ?: continue
            val puid = "$chamadaUid:$alunoUid"
            val ex = presencaDao.porUid(puid)
            val linha = p.copy(id = ex?.id ?: 0L, chamadaId = cid, uid = puid, updatedAt = t, deleted = false)
            if (ex == null) presencaDao.inserir(linha) else presencaDao.atualizar(linha)
        }

        // Oferta -> lançamento financeiro vinculado
        val finUid = "$chamadaUid:oferta"
        val finEx = financeiroDao.buscarPorChamada(cid) ?: financeiroDao.porUid(finUid)
        if (chamada.oferta > 0) {
            val f = Financeiro(
                id = finEx?.id ?: 0L, data = chamada.data, tipo = "ENTRADA",
                categoria = "Oferta (Classe)", valor = chamada.oferta,
                descricao = "Oferta registrada na chamada da classe", chamadaId = cid,
                uid = finEx?.uid ?: finUid, updatedAt = t, deleted = false
            )
            if (finEx == null) financeiroDao.inserir(f) else financeiroDao.atualizar(f)
        } else if (finEx != null) {
            financeiroDao.atualizar(finEx.copy(deleted = true, updatedAt = t))
        }
        return cid
    }

    /** Chamada já registrada para uma classe numa data (null se não existir). */
    suspend fun buscarChamada(classeId: Long, data: Long): Chamada? {
        val (ini, fim) = data.intervaloDoDiaUtc()
        return chamadaDao.buscarNoDia(classeId, ini, fim)
    }

    /**
     * Exclui uma chamada (soft-delete, para sincronizar a exclusão): marca a
     * chamada, todas as suas presenças e a oferta vinculada como excluídas.
     */
    suspend fun deletarChamada(chamada: Chamada) {
        val t = agora()
        presencaDao.listarPorChamada(chamada.id).forEach {
            presencaDao.atualizar(it.copy(deleted = true, updatedAt = t))
        }
        financeiroDao.buscarPorChamada(chamada.id)?.let {
            financeiroDao.atualizar(it.copy(deleted = true, updatedAt = t))
        }
        chamadaDao.atualizar(chamada.copy(deleted = true, updatedAt = t))
    }

    // ---------------- Financeiro ----------------
    val financeiro = financeiroDao.observarTodos()
    suspend fun listarFinanceiro() = financeiroDao.listarTodos()
    suspend fun salvarFinanceiro(f: Financeiro): Long {
        val t = agora()
        return if (f.id == 0L) financeiroDao.inserir(f.copy(uid = f.uid ?: novoUid(), updatedAt = t, deleted = false))
        else { financeiroDao.atualizar(f.copy(uid = f.uid ?: novoUid(), updatedAt = t)); f.id }
    }
    suspend fun deletarFinanceiro(f: Financeiro) { financeiroDao.atualizar(f.copy(deleted = true, updatedAt = agora())) }

    // ---------------- Visitantes ----------------
    val visitantes = visitanteDao.observarTodos()
    suspend fun listarVisitantes() = visitanteDao.listarTodos()
    suspend fun salvarVisitante(v: Visitante): Long {
        val t = agora()
        return if (v.id == 0L) visitanteDao.inserir(v.copy(uid = v.uid ?: novoUid(), updatedAt = t, deleted = false))
        else { visitanteDao.atualizar(v.copy(uid = v.uid ?: novoUid(), updatedAt = t)); v.id }
    }
    suspend fun deletarVisitante(v: Visitante) { visitanteDao.atualizar(v.copy(deleted = true, updatedAt = agora())) }

    suspend fun converterVisitanteEmAluno(v: Visitante, classeId: Long) {
        salvarAluno(Aluno(classeId = classeId, nome = v.nome, telefone = v.telefone, cargo = "Membro"))
        salvarVisitante(v.copy(convertido = true))
    }

    // ---------------- Dashboard ----------------
    suspend fun totalAlunos() = alunoDao.contarAtivos()
    suspend fun totalClasses() = classeDao.contar()
    suspend fun visitantesPendentes() = visitanteDao.contarPendentes()

    /** Apaga TODOS os dados (restauração de backup / inicializar pela nuvem). */
    suspend fun limparTudo() {
        presencaDao.deletarTudo(); chamadaDao.deletarTudo(); visitanteDao.deletarTudo()
        alunoDao.deletarTudo(); classeDao.deletarTudo(); financeiroDao.deletarTudo()
    }

    /* ===================================================================
     *  SINCRONIZAÇÃO
     * =================================================================== */

    private fun b(v: Boolean?): Int = if (v == true) 1 else 0

    /** Monta o JSON com TODOS os registros locais (inclusive excluídos), usando uids. */
    suspend fun montarPayloadSync(): JSONObject {
        val cls = classeDao.todosIncl()
        val alu = alunoDao.todosIncl()
        val cha = chamadaDao.todosIncl()
        val pre = presencaDao.todosIncl()
        val fin = financeiroDao.todosIncl()
        val vis = visitanteDao.todosIncl()

        val uidClasse = cls.associate { it.id to (it.uid ?: "") }
        val uidAluno = alu.associate { it.id to (it.uid ?: "") }
        val uidChamada = cha.associate { it.id to (it.uid ?: "") }

        val root = JSONObject()
        root.put("classes", JSONArray().apply {
            cls.forEach { put(JSONObject()
                .put("uid", it.uid).put("nome", it.nome).put("faixaEtaria", it.faixaEtaria)
                .put("professores", it.professores).put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("alunos", JSONArray().apply {
            alu.forEach { put(JSONObject()
                .put("uid", it.uid).put("classeUid", uidClasse[it.classeId] ?: "")
                .put("nome", it.nome).put("dataNascimento", it.dataNascimento ?: JSONObject.NULL)
                .put("telefone", it.telefone).put("cargo", it.cargo).put("ativo", b(it.ativo))
                .put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("chamadas", JSONArray().apply {
            cha.forEach { put(JSONObject()
                .put("uid", it.uid).put("classeUid", uidClasse[it.classeId] ?: "")
                .put("data", it.data).put("licao", it.licao).put("oferta", it.oferta)
                .put("visitantes", it.visitantes).put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("presencas", JSONArray().apply {
            pre.forEach { put(JSONObject()
                .put("uid", it.uid).put("chamadaUid", uidChamada[it.chamadaId] ?: "")
                .put("alunoUid", uidAluno[it.alunoId] ?: "").put("presente", b(it.presente))
                .put("biblia", b(it.biblia)).put("revista", b(it.revista))
                .put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("financeiro", JSONArray().apply {
            fin.forEach { put(JSONObject()
                .put("uid", it.uid).put("data", it.data).put("tipo", it.tipo)
                .put("categoria", it.categoria).put("valor", it.valor).put("descricao", it.descricao)
                .put("chamadaUid", it.chamadaId?.let { id -> uidChamada[id] } ?: "")
                .put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("visitantes", JSONArray().apply {
            vis.forEach { put(JSONObject()
                .put("uid", it.uid).put("nome", it.nome).put("telefone", it.telefone).put("data", it.data)
                .put("classeUid", it.classeId?.let { id -> uidClasse[id] } ?: "")
                .put("observacao", it.observacao).put("convertido", b(it.convertido))
                .put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        return root
    }

    /** Aplica os dados vindos da planilha (mescla por uid, última alteração vence). */
    suspend fun aplicarSync(dados: JSONObject) = db.withTransaction {
        // CLASSES
        eachObj(dados.optJSONArray("classes")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = classeDao.porUid(uid)
            if (local == null) classeDao.inserir(Classe(nome = jStr(o, "nome"), faixaEtaria = jStr(o, "faixaEtaria"),
                professores = jStr(o, "professores"), uid = uid, updatedAt = rUpd, deleted = rDel))
            else if (rUpd > (local.updatedAt ?: 0L)) classeDao.atualizar(local.copy(nome = jStr(o, "nome"),
                faixaEtaria = jStr(o, "faixaEtaria"), professores = jStr(o, "professores"), updatedAt = rUpd, deleted = rDel))
        }
        val mapaClasse = classeDao.todosIncl().associate { (it.uid ?: "") to it.id }

        // ALUNOS
        eachObj(dados.optJSONArray("alunos")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val cId = mapaClasse[jStr(o, "classeUid")] ?: return@eachObj
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = alunoDao.porUid(uid)
            val dados2 = Aluno(classeId = cId, nome = jStr(o, "nome"), dataNascimento = jLongOrNull(o, "dataNascimento"),
                telefone = jStr(o, "telefone"), cargo = jStr(o, "cargo"), ativo = jBool(o, "ativo"),
                uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) alunoDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) alunoDao.atualizar(dados2.copy(id = local.id))
        }
        val mapaAluno = alunoDao.todosIncl().associate { (it.uid ?: "") to it.id }

        // CHAMADAS
        eachObj(dados.optJSONArray("chamadas")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val cId = mapaClasse[jStr(o, "classeUid")] ?: return@eachObj
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = chamadaDao.porUid(uid)
            val dados2 = Chamada(classeId = cId, data = jLong(o, "data"), licao = jInt(o, "licao"),
                oferta = jDouble(o, "oferta"), visitantes = jInt(o, "visitantes"),
                uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) chamadaDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) chamadaDao.atualizar(dados2.copy(id = local.id))
        }
        val mapaChamada = chamadaDao.todosIncl().associate { (it.uid ?: "") to it.id }

        // PRESENÇAS
        eachObj(dados.optJSONArray("presencas")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val chId = mapaChamada[jStr(o, "chamadaUid")] ?: return@eachObj
            val aId = mapaAluno[jStr(o, "alunoUid")] ?: return@eachObj
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = presencaDao.porUid(uid)
            val dados2 = Presenca(chamadaId = chId, alunoId = aId, presente = jBool(o, "presente"),
                biblia = jBool(o, "biblia"), revista = jBool(o, "revista"), uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) presencaDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) presencaDao.atualizar(dados2.copy(id = local.id))
        }

        // FINANCEIRO
        eachObj(dados.optJSONArray("financeiro")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val chUid = jStr(o, "chamadaUid")
            val chId = if (chUid.isBlank()) null else mapaChamada[chUid]
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = financeiroDao.porUid(uid)
            val dados2 = Financeiro(data = jLong(o, "data"), tipo = jStr(o, "tipo"), categoria = jStr(o, "categoria"),
                valor = jDouble(o, "valor"), descricao = jStr(o, "descricao"), chamadaId = chId,
                uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) financeiroDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) financeiroDao.atualizar(dados2.copy(id = local.id))
        }

        // VISITANTES
        eachObj(dados.optJSONArray("visitantes")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val clUid = jStr(o, "classeUid")
            val clId = if (clUid.isBlank()) null else mapaClasse[clUid]
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = visitanteDao.porUid(uid)
            val dados2 = Visitante(nome = jStr(o, "nome"), telefone = jStr(o, "telefone"), data = jLong(o, "data"),
                classeId = clId, observacao = jStr(o, "observacao"), convertido = jBool(o, "convertido"),
                uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) visitanteDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) visitanteDao.atualizar(dados2.copy(id = local.id))
        }
    }
}

/* -------- helpers de leitura de JSON (tolerantes ao formato do Sheets) -------- */
private inline fun eachObj(arr: JSONArray?, action: (JSONObject) -> Unit) {
    if (arr == null) return
    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(action)
}
private fun jStr(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "")
private fun jBool(o: JSONObject, k: String): Boolean = when (val v = o.opt(k)) {
    is Boolean -> v; is Number -> v.toInt() == 1; is String -> v == "1" || v.equals("true", true); else -> false
}
private fun jLongOrNull(o: JSONObject, k: String): Long? {
    if (o.isNull(k)) return null
    return when (val v = o.opt(k)) {
        is Number -> v.toLong(); is String -> if (v.isBlank()) null else v.toDoubleOrNull()?.toLong(); else -> null
    }
}
private fun jLong(o: JSONObject, k: String): Long = jLongOrNull(o, k) ?: 0L
private fun jInt(o: JSONObject, k: String): Int = jLong(o, k).toInt()
private fun jDouble(o: JSONObject, k: String): Double = when (val v = o.opt(k)) {
    is Number -> v.toDouble(); is String -> v.toDoubleOrNull() ?: 0.0; else -> 0.0
}
