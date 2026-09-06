package com.ebd.controle.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class Repository(private val db: AppDatabase) {
    private val classeDao = db.classeDao()
    private val alunoDao = db.alunoDao()
    private val chamadaDao = db.chamadaDao()
    private val presencaDao = db.presencaDao()
    private val revistaAlunoDao = db.revistaAlunoDao()
    private val criterioDao = db.criterioPontuacaoDao()
    private val pontoDao = db.pontoLancamentoDao()
    private val visitanteDao = db.visitanteDao()
    private val contribuicaoDao = db.contribuicaoDao()

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

    /** Presenças de várias chamadas em uma consulta, agrupadas por chamada. */
    suspend fun presencasDasChamadas(chamadaIds: List<Long>): Map<Long, List<Presenca>> =
        if (chamadaIds.isEmpty()) emptyMap()
        else presencaDao.listarPorChamadas(chamadaIds).groupBy { it.chamadaId }
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

        // A oferta vive em `chamadas.oferta`. O lançamento financeiro espelhado que
        // existia aqui morreu junto com a tela de Finanças, e era ele que duplicava a
        // receita quando o vínculo chamada->lançamento se perdia.
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
        chamadaDao.atualizar(chamada.copy(deleted = true, updatedAt = t))
    }

    // ---------------- Revistas (situação por trimestre) ----------------
    val revistasAlunos = revistaAlunoDao.observarTodas()

    suspend fun revistasDoTrimestre(ano: Int, trim: Int) = revistaAlunoDao.listarDoTrimestre(ano, trim)

    /**
     * Marca se o aluno tem a revista do trimestre e se pagou.
     *
     * O uid é derivado de aluno+ano+trimestre para que dois celulares marcando o mesmo
     * aluno atualizem a mesma linha em vez de criar duas.
     */
    suspend fun marcarRevista(alunoId: Long, ano: Int, trim: Int, temRevista: Boolean, pago: Boolean) {
        val alunoUid = alunoDao.porId(alunoId)?.uid ?: return
        val uid = "$alunoUid:$ano:$trim"
        val existente = revistaAlunoDao.porUid(uid)
        val linha = RevistaAluno(
            id = existente?.id ?: 0L, alunoId = alunoId, ano = ano, trimestre = trim,
            temRevista = temRevista, pago = pago,
            uid = uid, updatedAt = agora(), deleted = false
        )
        if (existente == null) revistaAlunoDao.inserir(linha) else revistaAlunoDao.atualizar(linha)
    }

    // ---------------- Pontuação ----------------
    val criterios = criterioDao.observarTodos()
    val pontos = pontoDao.observarTodos()
    suspend fun listarCriterios() = criterioDao.listarTodos()
    suspend fun contarCriterios() = criterioDao.contar()

    suspend fun salvarCriterio(c: CriterioPontuacao): Long {
        val t = agora()
        return if (c.id == 0L) criterioDao.inserir(c.copy(uid = c.uid ?: novoUid(), updatedAt = t, deleted = false))
        else { criterioDao.atualizar(c.copy(uid = c.uid ?: novoUid(), updatedAt = t)); c.id }
    }
    suspend fun deletarCriterio(c: CriterioPontuacao) {
        criterioDao.atualizar(c.copy(deleted = true, updatedAt = agora()))
    }

    /** Pontos já lançados de um aluno numa data (para a tela de marcação pré-marcar). */
    suspend fun pontosDoAlunoNaData(alunoId: Long, data: Long) =
        pontoDao.listarDoAlunoNaData(alunoId, data)

    /**
     * Marca (ou desmarca) um critério para um aluno numa data.
     *  - quantidade <= 0  -> remove o lançamento (soft-delete). Serve de "toggle off".
     *  - quantidade >= 1  -> cria/atualiza o lançamento; pontos = criterio.pontos
     *    (× quantidade quando o critério é por quantidade).
     * uid determinístico "alunoUid:criterioUid:data" garante 1 linha por
     * aluno+critério+dia e evita duplicar no sync.
     */
    suspend fun marcarPonto(alunoId: Long, criterio: CriterioPontuacao, data: Long, quantidade: Int) {
        val t = agora()
        val alunoUid = alunoDao.porId(alunoId)?.uid ?: return
        val criterioUid = criterio.uid ?: return
        val lancUid = "$alunoUid:$criterioUid:$data"
        val existente = pontoDao.porUid(lancUid)

        if (quantidade <= 0) {
            existente?.let { if (it.deleted != true) pontoDao.atualizar(it.copy(deleted = true, updatedAt = t)) }
            return
        }

        val qtd = if (criterio.porQuantidade) quantidade else 1
        val total = criterio.pontos * qtd
        val linha = PontoLancamento(
            id = existente?.id ?: 0L, alunoId = alunoId, criterioId = criterio.id,
            data = data, pontos = total, quantidade = qtd,
            uid = existente?.uid ?: lancUid, updatedAt = t, deleted = false
        )
        if (existente == null) pontoDao.inserir(linha) else pontoDao.atualizar(linha)
    }

    /** Lançamentos de pontos dentro de um período [ini, fim). */
    suspend fun pontosDoPeriodo(ini: Long, fim: Long) = pontoDao.listarPorPeriodo(ini, fim)

    // ---------------- Contribuições dos professores ----------------
    val contribuicoes = contribuicaoDao.observarTodas()

    /** Contribuições de um trimestre (os três meses de uma vez). */
    suspend fun contribuicoesDoTrimestre(t: Trimestre) =
        contribuicaoDao.listarDosMeses(t.ano, t.meses())

    /**
     * Registra (ou apaga) a contribuição de um professor num mês.
     *
     * valor <= 0 remove o lançamento (soft-delete), que é como a tela desfaz um
     * registro feito por engano. O uid vem de aluno+ano+mês para que dois celulares
     * lançando o mesmo mês atualizem a mesma linha em vez de somar duas.
     */
    suspend fun salvarContribuicao(
        alunoId: Long, ano: Int, mes: Int, valor: Double,
        forma: String, data: Long, observacao: String
    ) {
        val t = agora()
        val alunoUid = alunoDao.porId(alunoId)?.uid ?: return
        val uid = "contrib:$alunoUid:$ano:$mes"
        val existente = contribuicaoDao.porUid(uid)

        if (valor <= 0.0) {
            existente?.let { if (it.deleted != true) contribuicaoDao.atualizar(it.copy(deleted = true, updatedAt = t)) }
            return
        }

        val linha = ContribuicaoProfessor(
            id = existente?.id ?: 0L, alunoId = alunoId, ano = ano, mes = mes,
            valor = valor, forma = forma, data = data, observacao = observacao,
            uid = uid, updatedAt = t, deleted = false
        )
        if (existente == null) contribuicaoDao.inserir(linha) else contribuicaoDao.atualizar(linha)
    }

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

    /**
     * Fotografa as 10 tabelas para o backup. Usa `todosIncl()` de propósito: linhas com
     * exclusão lógica precisam entrar no arquivo para que a exclusão continue se
     * propagando depois de uma restauração.
     */
    suspend fun coletarParaBackup() = DadosBackup(
        classes = classeDao.todosIncl(),
        alunos = alunoDao.todosIncl(),
        chamadas = chamadaDao.todosIncl(),
        presencas = presencaDao.todosIncl(),
        visitantes = visitanteDao.todosIncl(),
        revistasAlunos = revistaAlunoDao.todosIncl(),
        criterios = criterioDao.todosIncl(),
        pontos = pontoDao.todosIncl(),
        contribuicoes = contribuicaoDao.todasIncl()
    )

    /**
     * Substitui o banco pelo conteúdo do backup, numa transação só.
     *
     * Grava cada linha como ela está no arquivo — id, uid e updatedAt inclusive. Como o
     * banco acabou de ser esvaziado, reusar os ids originais é seguro e preserva todos os
     * vínculos sem precisar remapear nada.
     */
    suspend fun restaurarDeBackup(d: DadosBackup) = db.withTransaction {
        limparTudo()
        d.classes.forEach { classeDao.inserir(it) }
        d.alunos.forEach { alunoDao.inserir(it) }
        d.chamadas.forEach { chamadaDao.inserir(it) }
        d.presencas.forEach { presencaDao.inserir(it) }
        d.visitantes.forEach { visitanteDao.inserir(it) }
        d.revistasAlunos.forEach { revistaAlunoDao.inserir(it) }
        d.criterios.forEach { criterioDao.inserir(it) }
        d.pontos.forEach { pontoDao.inserir(it) }
        d.contribuicoes.forEach { contribuicaoDao.inserir(it) }
    }

    /** Apaga TODOS os dados (restauração de backup / inicializar pela nuvem). */
    suspend fun limparTudo() {
        presencaDao.deletarTudo(); chamadaDao.deletarTudo(); visitanteDao.deletarTudo()
        revistaAlunoDao.deletarTudo()
        pontoDao.deletarTudo(); criterioDao.deletarTudo()
        contribuicaoDao.deletarTudo()
        alunoDao.deletarTudo(); classeDao.deletarTudo()
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
        val rev = revistaAlunoDao.todosIncl()
        val cri = criterioDao.todosIncl()
        val pts = pontoDao.todosIncl()
        val vis = visitanteDao.todosIncl()
        val ctb = contribuicaoDao.todasIncl()

        val uidClasse = cls.associate { it.id to (it.uid ?: "") }
        val uidAluno = alu.associate { it.id to (it.uid ?: "") }
        val uidChamada = cha.associate { it.id to (it.uid ?: "") }
        val uidCriterio = cri.associate { it.id to (it.uid ?: "") }

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
                .put("especial", b(it.especial)).put("professor", b(it.professor))
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
        root.put("visitantes", JSONArray().apply {
            vis.forEach { put(JSONObject()
                .put("uid", it.uid).put("nome", it.nome).put("telefone", it.telefone).put("data", it.data)
                .put("classeUid", it.classeId?.let { id -> uidClasse[id] } ?: "")
                .put("observacao", it.observacao).put("convertido", b(it.convertido))
                .put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("revistasAlunos", JSONArray().apply {
            rev.forEach { put(JSONObject()
                .put("uid", it.uid).put("alunoUid", uidAluno[it.alunoId] ?: "")
                .put("ano", it.ano).put("trimestre", it.trimestre)
                .put("temRevista", b(it.temRevista)).put("pago", b(it.pago))
                .put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("criterios", JSONArray().apply {
            cri.forEach { put(JSONObject()
                .put("uid", it.uid).put("nome", it.nome).put("pontos", it.pontos)
                .put("grupo", it.grupo).put("porQuantidade", b(it.porQuantidade))
                .put("ordem", it.ordem).put("ativo", b(it.ativo))
                .put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("pontos", JSONArray().apply {
            pts.forEach { put(JSONObject()
                .put("uid", it.uid).put("alunoUid", uidAluno[it.alunoId] ?: "")
                .put("criterioUid", uidCriterio[it.criterioId] ?: "")
                .put("data", it.data).put("pontos", it.pontos).put("quantidade", it.quantidade)
                .put("updatedAt", it.updatedAt ?: 0L).put("deleted", b(it.deleted))) }
        })
        root.put("contribuicoes", JSONArray().apply {
            ctb.forEach { put(JSONObject()
                .put("uid", it.uid).put("alunoUid", uidAluno[it.alunoId] ?: "")
                .put("ano", it.ano).put("mes", it.mes).put("valor", it.valor)
                .put("forma", it.forma).put("data", it.data).put("observacao", it.observacao)
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
                especial = jBool(o, "especial"), professor = jBool(o, "professor"),
                uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) alunoDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) alunoDao.atualizar(
                // Campo ausente é falta de informação, não exclusão: sem esta guarda um
                // aparelho com parsing quebrado apaga o aniversário em todos os outros.
                // Vale também para `professor`, que aparelhos numa versão anterior nem
                // sabem enviar — sem a guarda, o primeiro sync deles desmarcaria todos.
                dados2.copy(
                    id = local.id,
                    dataNascimento = dados2.dataNascimento ?: local.dataNascimento,
                    professor = if (temValor(o, "professor")) dados2.professor else local.professor
                )
            )
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
            else if (rUpd > (local.updatedAt ?: 0L)) chamadaDao.atualizar(
                // Data ilegível vira 0L (01/01/1970) e tiraria a chamada do trimestre.
                dados2.copy(id = local.id, data = if (dados2.data > 0L) dados2.data else local.data)
            )
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

        // REVISTAS (situação por trimestre)
        eachObj(dados.optJSONArray("revistasAlunos")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val aId = mapaAluno[jStr(o, "alunoUid")] ?: return@eachObj
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = revistaAlunoDao.porUid(uid)
            val dados2 = RevistaAluno(alunoId = aId, ano = jInt(o, "ano"),
                trimestre = jInt(o, "trimestre"), temRevista = jBool(o, "temRevista"),
                pago = jBool(o, "pago"), uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) revistaAlunoDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) revistaAlunoDao.atualizar(dados2.copy(id = local.id))
        }

        // PONTUAÇÃO - CRITÉRIOS
        eachObj(dados.optJSONArray("criterios")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = criterioDao.porUid(uid)
            val dados2 = CriterioPontuacao(nome = jStr(o, "nome"), pontos = jInt(o, "pontos"),
                grupo = jStr(o, "grupo").ifBlank { "REGULAR" }, porQuantidade = jBool(o, "porQuantidade"),
                ordem = jInt(o, "ordem"), ativo = jBool(o, "ativo"), uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) criterioDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) criterioDao.atualizar(dados2.copy(id = local.id))
        }
        val mapaCriterio = criterioDao.todosIncl().associate { (it.uid ?: "") to it.id }

        // PONTUAÇÃO - LANÇAMENTOS
        eachObj(dados.optJSONArray("pontos")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val aId = mapaAluno[jStr(o, "alunoUid")] ?: return@eachObj
            val crId = mapaCriterio[jStr(o, "criterioUid")] ?: return@eachObj
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = pontoDao.porUid(uid)
            val dados2 = PontoLancamento(alunoId = aId, criterioId = crId, data = jLong(o, "data"),
                pontos = jInt(o, "pontos"), quantidade = jInt(o, "quantidade").coerceAtLeast(1),
                uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) pontoDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) pontoDao.atualizar(dados2.copy(id = local.id))
        }

        // CONTRIBUIÇÕES DOS PROFESSORES
        eachObj(dados.optJSONArray("contribuicoes")) { o ->
            val uid = jStr(o, "uid"); if (uid.isBlank()) return@eachObj
            val aId = mapaAluno[jStr(o, "alunoUid")] ?: return@eachObj
            val rUpd = jLong(o, "updatedAt"); val rDel = jBool(o, "deleted")
            val local = contribuicaoDao.porUid(uid)
            val dados2 = ContribuicaoProfessor(alunoId = aId, ano = jInt(o, "ano"), mes = jInt(o, "mes"),
                valor = jDouble(o, "valor"), forma = jStr(o, "forma").ifBlank { FORMA_DINHEIRO },
                data = jLong(o, "data"), observacao = jStr(o, "observacao"),
                uid = uid, updatedAt = rUpd, deleted = rDel)
            if (local == null) contribuicaoDao.inserir(dados2)
            else if (rUpd > (local.updatedAt ?: 0L)) contribuicaoDao.atualizar(dados2.copy(id = local.id))
        }
    }
}

/* -------- helpers de leitura de JSON (tolerantes ao formato do Sheets) -------- */
private inline fun eachObj(arr: JSONArray?, action: (JSONObject) -> Unit) {
    if (arr == null) return
    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(action)
}
private fun jStr(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "")

/**
 * Se a chave chegou com conteúdo de verdade.
 *
 * Célula vazia na planilha e chave ausente no JSON significam a mesma coisa — "este
 * aparelho não sabe deste campo" — e as duas precisam preservar o valor local. Sem
 * isto, um celular numa versão anterior sincronizando desmarcaria os professores
 * de todos os outros.
 */
private fun temValor(o: JSONObject, k: String): Boolean =
    !o.isNull(k) && o.opt(k)?.toString()?.isNotBlank() == true
private fun jBool(o: JSONObject, k: String): Boolean = when (val v = o.opt(k)) {
    is Boolean -> v; is Number -> v.toInt() == 1; is String -> v == "1" || v.equals("true", true); else -> false
}
private fun jLongOrNull(o: JSONObject, k: String): Long? {
    if (o.isNull(k)) return null
    return when (val v = o.opt(k)) {
        is Number -> v.toLong()
        is String -> if (v.isBlank()) null else v.toDoubleOrNull()?.toLong() ?: parseDataTexto(v)
        else -> null
    }
}

/**
 * O Apps Script devolve células formatadas como data em texto, não em millis:
 * ISO-8601 quando o `getValues()` entrega um Date do JS, ou `dd/MM/yyyy` quando a
 * célula é lida como string. Sem isto o valor era descartado e o campo virava
 * null/1970 — foi o que apagava aniversários e jogava chamadas para fora do trimestre.
 */
private fun parseDataTexto(texto: String): Long? {
    val s = texto.trim()
    if (s.isEmpty()) return null
    val fuso = ZoneId.systemDefault()
    fun inicioDoDia(d: LocalDate) = d.atStartOfDay(fuso).toInstant().toEpochMilli()

    return runCatching { Instant.parse(s).toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(s).atZone(fuso).toInstant().toEpochMilli() }
        .recoverCatching { inicioDoDia(LocalDate.parse(s)) }
        .recoverCatching { inicioDoDia(LocalDate.parse(s, FORMATO_BR)) }
        .getOrNull()
}

private val FORMATO_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private fun jLong(o: JSONObject, k: String): Long = jLongOrNull(o, k) ?: 0L
private fun jInt(o: JSONObject, k: String): Int = jLong(o, k).toInt()
private fun jDouble(o: JSONObject, k: String): Double = when (val v = o.opt(k)) {
    is Number -> v.toDouble(); is String -> v.toDoubleOrNull() ?: 0.0; else -> 0.0
}
