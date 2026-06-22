package com.ebd.controle.data

import org.json.JSONArray
import org.json.JSONObject

/** Exporta TODOS os dados (backup completo) para uma string JSON. */
suspend fun exportarBackup(repo: Repository): String {
    val root = JSONObject()
    root.put("versao", 2)

    val classes = JSONArray()
    repo.listarClasses().forEach { c ->
        classes.put(JSONObject().apply {
            put("id", c.id); put("nome", c.nome)
            put("faixaEtaria", c.faixaEtaria); put("professores", c.professores)
        })
    }
    root.put("classes", classes)

    val alunos = JSONArray()
    repo.listarTodosAlunos().forEach { a ->
        alunos.put(JSONObject().apply {
            put("id", a.id); put("classeId", a.classeId); put("nome", a.nome)
            put("dataNascimento", a.dataNascimento ?: JSONObject.NULL)
            put("telefone", a.telefone); put("cargo", a.cargo); put("ativo", a.ativo)
        })
    }
    root.put("alunos", alunos)

    val chamadas = JSONArray()
    repo.listarTodasChamadas().forEach { ch ->
        chamadas.put(JSONObject().apply {
            put("id", ch.id); put("classeId", ch.classeId); put("data", ch.data)
            put("licao", ch.licao); put("oferta", ch.oferta)
            put("dizimos", ch.dizimos); put("visitantes", ch.visitantes)
        })
    }
    root.put("chamadas", chamadas)

    val presencas = JSONArray()
    repo.listarTodasPresencas().forEach { p ->
        presencas.put(JSONObject().apply {
            put("chamadaId", p.chamadaId); put("alunoId", p.alunoId)
            put("presente", p.presente); put("biblia", p.biblia); put("revista", p.revista)
        })
    }
    root.put("presencas", presencas)

    val visitantes = JSONArray()
    repo.listarVisitantes().forEach { v ->
        visitantes.put(JSONObject().apply {
            put("nome", v.nome); put("telefone", v.telefone); put("data", v.data)
            put("classeId", v.classeId ?: JSONObject.NULL)
            put("observacao", v.observacao); put("convertido", v.convertido)
        })
    }
    root.put("visitantes", visitantes)

    val fin = JSONArray()
    repo.listarFinanceiro().forEach { f ->
        fin.put(JSONObject().apply {
            put("data", f.data); put("tipo", f.tipo); put("categoria", f.categoria)
            put("valor", f.valor); put("descricao", f.descricao)
        })
    }
    root.put("financeiro", fin)

    return root.toString(2)
}

/**
 * RESTAURA um backup: APAGA tudo que existe e recria a partir do JSON.
 * Os IDs são remapeados para manter os vínculos (aluno -> classe, presença -> chamada/aluno).
 */
suspend fun importarBackup(repo: Repository, json: String) {
    val root = JSONObject(json)

    repo.limparTudo()

    val mapaClasse = HashMap<Long, Long>()
    val classesArr = root.optJSONArray("classes") ?: JSONArray()
    for (i in 0 until classesArr.length()) {
        val o = classesArr.getJSONObject(i)
        val novo = repo.salvarClasse(
            Classe(nome = o.getString("nome"),
                faixaEtaria = o.optString("faixaEtaria", ""),
                professores = o.optString("professores", ""))
        )
        mapaClasse[o.optLong("id", -1)] = novo
    }

    val mapaAluno = HashMap<Long, Long>()
    val alunosArr = root.optJSONArray("alunos") ?: JSONArray()
    for (i in 0 until alunosArr.length()) {
        val o = alunosArr.getJSONObject(i)
        val classeNova = mapaClasse[o.optLong("classeId", -1)] ?: continue
        val nasc = if (o.isNull("dataNascimento")) null else o.optLong("dataNascimento")
        val novo = repo.salvarAluno(
            Aluno(classeId = classeNova, nome = o.getString("nome"), dataNascimento = nasc,
                telefone = o.optString("telefone", ""), cargo = o.optString("cargo", ""),
                ativo = o.optBoolean("ativo", true))
        )
        mapaAluno[o.optLong("id", -1)] = novo
    }

    val mapaChamada = HashMap<Long, Long>()
    val chamadasArr = root.optJSONArray("chamadas") ?: JSONArray()
    for (i in 0 until chamadasArr.length()) {
        val o = chamadasArr.getJSONObject(i)
        val classeNova = mapaClasse[o.optLong("classeId", -1)] ?: continue
        val novo = repo.inserirChamada(
            Chamada(classeId = classeNova, data = o.getLong("data"),
                licao = o.optInt("licao", 0), oferta = o.optDouble("oferta", 0.0),
                dizimos = o.optDouble("dizimos", 0.0), visitantes = o.optInt("visitantes", 0))
        )
        mapaChamada[o.optLong("id", -1)] = novo
    }

    val presArr = root.optJSONArray("presencas") ?: JSONArray()
    val presencas = ArrayList<Presenca>()
    for (i in 0 until presArr.length()) {
        val o = presArr.getJSONObject(i)
        val cid = mapaChamada[o.optLong("chamadaId", -1)] ?: continue
        val aid = mapaAluno[o.optLong("alunoId", -1)] ?: continue
        presencas.add(Presenca(chamadaId = cid, alunoId = aid,
            presente = o.optBoolean("presente", false),
            biblia = o.optBoolean("biblia", false),
            revista = o.optBoolean("revista", false)))
    }
    if (presencas.isNotEmpty()) repo.inserirPresencas(presencas)

    val visArr = root.optJSONArray("visitantes") ?: JSONArray()
    for (i in 0 until visArr.length()) {
        val o = visArr.getJSONObject(i)
        val classeNova = if (o.isNull("classeId")) null else mapaClasse[o.optLong("classeId", -1)]
        repo.salvarVisitante(
            Visitante(nome = o.getString("nome"), telefone = o.optString("telefone", ""),
                data = o.getLong("data"), classeId = classeNova,
                observacao = o.optString("observacao", ""), convertido = o.optBoolean("convertido", false))
        )
    }

    val finArr = root.optJSONArray("financeiro") ?: JSONArray()
    for (i in 0 until finArr.length()) {
        val o = finArr.getJSONObject(i)
        repo.salvarFinanceiro(
            Financeiro(data = o.getLong("data"), tipo = o.getString("tipo"),
                categoria = o.optString("categoria", ""), valor = o.getDouble("valor"),
                descricao = o.optString("descricao", ""))
        )
    }
}
