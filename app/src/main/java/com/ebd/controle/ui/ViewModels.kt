package com.ebd.controle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebd.controle.EBDApp
import com.ebd.controle.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth

private fun Application.repo() = (this as EBDApp).repository

/* ----------------------- UI models ----------------------- */
data class AniversarianteUi(
    val nome: String, val classe: String, val dataNascimento: Long,
    val idadeQueFara: Int, val diaSemana: String, val diasAte: Long, val quando: String
)
data class ChamadaResumoUi(
    val data: Long, val licao: Int, val presentes: Int, val total: Int, val pct: Float, val oferta: Double
)

/* ===== Modelos do RELATÓRIO DO DIA ===== */
/** Linha por classe num determinado dia. Nas visões "geral" e "por classe" a estrutura é a mesma. */
data class RelDiaLinhaClasse(
    val classeId: Long,
    val classeNome: String,
    val matriculados: Int,
    val presentes: Int,
    val ausentes: Int,
    val visitantes: Int,
    val biblias: Int,
    val revistas: Int,
    val oferta: Double,
    val licao: Int
)
/** Pacote completo do relatório de uma data (dia da aula). */
data class RelDiaUi(
    val data: Long,
    val linhas: List<RelDiaLinhaClasse>
) {
    val matriculados get() = linhas.sumOf { it.matriculados }
    val presentes  get() = linhas.sumOf { it.presentes }
    val ausentes   get() = linhas.sumOf { it.ausentes }
    val visitantes get() = linhas.sumOf { it.visitantes }
    val biblias    get() = linhas.sumOf { it.biblias }
    val revistas   get() = linhas.sumOf { it.revistas }
    val oferta     get() = linhas.sumOf { it.oferta }
    val totalPresentes get() = presentes + visitantes
    val pctPresenca: Float get() = if (matriculados > 0) presentes.toFloat() / matriculados else 0f
}

/* ===== Modelos do RELATÓRIO DO TRIMESTRE =====
 * Agora pivotado: cada COLUNA é uma classe; cada LINHA é uma métrica.
 *  - matriculados = nº atual de membros da classe (cadastro), não soma de aulas.
 *  - demais métricas = soma do trimestre dentro daquela classe.
 *  - visitantes vêm da tabela Visitante (não do campo Chamada.visitantes),
 *    para cobrir os dois fluxos: o cadastrado dentro da Chamada e o cadastrado
 *    pela tela de Visitantes.
 */
data class RelTrimColuna(
    val classeId: Long,
    val classeNome: String,
    val matriculados: Int,
    val presentes: Int,
    val ausentes: Int,
    val visitantes: Int,
    val biblias: Int,
    val revistas: Int,
    val oferta: Double
)
/** Item da lista de aulas do trimestre, usado para clicar e abrir o relatório do dia. */
data class AulaListaUi(
    val data: Long,
    val classesQueLancaram: Int,
    val totalClasses: Int,
    val matriculados: Int,
    val presentes: Int,
    val pct: Float,
    val oferta: Double
)
/** Pacote do relatório do trimestre (uma coluna por classe, com totais). */
data class RelTrimUi(
    val trimestre: Trimestre,
    val colunas: List<RelTrimColuna>
) {
    val totalMatric  get() = colunas.sumOf { it.matriculados }
    val totalAusent  get() = colunas.sumOf { it.ausentes }
    val totalPres    get() = colunas.sumOf { it.presentes }
    val totalVisit   get() = colunas.sumOf { it.visitantes }
    val totalBibl    get() = colunas.sumOf { it.biblias }
    val totalRev     get() = colunas.sumOf { it.revistas }
    val totalOferta  get() = colunas.sumOf { it.oferta }
}
data class VisitanteUi(val visitante: Visitante, val classeNome: String)
data class DashboardState(
    val totalClasses: Int = 0, val totalAlunos: Int = 0,
    val ultimaData: Long? = null, val ultimaPct: Float = 0f,
    val ofertasMes: Double = 0.0, val aniversariantes: List<AniversarianteUi> = emptyList(),
    val visitantesPendentes: Int = 0
)

/* ----------------------- Classes ----------------------- */
class ClassesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()
    val classes = repo.classes.stateInDefault(viewModelScope, emptyList())
    fun salvar(c: Classe) = viewModelScope.launch { repo.salvarClasse(c) }
    fun deletar(c: Classe) = viewModelScope.launch { repo.deletarClasse(c) }
}

/* ----------------------- Alunos ----------------------- */
class AlunosViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()
    val classes = repo.classes.stateInDefault(viewModelScope, emptyList())
    val alunos = repo.alunos.stateInDefault(viewModelScope, emptyList())
    fun salvar(a: Aluno) = viewModelScope.launch { repo.salvarAluno(a) }
    fun deletar(a: Aluno) = viewModelScope.launch { repo.deletarAluno(a) }

    /* --- Revista do trimestre (substitui a tela de Revistas) --- */

    private val _trimestre = MutableStateFlow(Trimestre.atual())
    val trimestre: StateFlow<Trimestre> = _trimestre.asStateFlow()

    /** alunoId -> situação no trimestre selecionado. */
    val revistas: StateFlow<Map<Long, RevistaAluno>> =
        combine(repo.revistasAlunos, _trimestre) { todas, t ->
            todas.filter { it.ano == t.ano && it.trimestre == t.numero }
                .associateBy { it.alunoId }
        }.stateInDefault(viewModelScope, emptyMap())

    fun trimestreAnterior() { _trimestre.value = _trimestre.value.anterior() }
    fun trimestreProximo() { _trimestre.value = _trimestre.value.proximo() }

    fun marcarRevista(alunoId: Long, temRevista: Boolean, pago: Boolean) =
        viewModelScope.launch {
            val t = _trimestre.value
            // Pagar sem ter a revista não faz sentido: marcar "pago" implica "tem".
            repo.marcarRevista(alunoId, t.ano, t.numero, temRevista || pago, pago)
        }
}

/* ----------------------- Chamada ----------------------- */
class ChamadaViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()
    val classes = repo.classes.stateInDefault(viewModelScope, emptyList())

    private val _alunos = MutableStateFlow<List<Aluno>>(emptyList())
    val alunos: StateFlow<List<Aluno>> = _alunos

    // Chamada já existente para a classe+data selecionada (null = chamada nova)
    private val _chamadaExistente = MutableStateFlow<Chamada?>(null)
    val chamadaExistente: StateFlow<Chamada?> = _chamadaExistente.asStateFlow()

    // Presenças já registradas nessa chamada (para pré-marcar os alunos ao editar)
    private val _presencasExistentes = MutableStateFlow<List<Presenca>>(emptyList())
    val presencasExistentes: StateFlow<List<Presenca>> = _presencasExistentes.asStateFlow()

    fun carregarAlunos(classeId: Long) = viewModelScope.launch {
        _alunos.value = repo.listarAlunosPorClasse(classeId)
    }

    /**
     * Carrega os alunos da classe e, se já existir uma chamada para essa
     * classe+data, traz a chamada e suas presenças para edição.
     */
    fun carregar(classeId: Long, data: Long) = viewModelScope.launch {
        _alunos.value = repo.listarAlunosPorClasse(classeId)
        val ch = repo.buscarChamada(classeId, data)
        _chamadaExistente.value = ch
        _presencasExistentes.value = ch?.let { repo.presencasDaChamada(it.id) } ?: emptyList()
    }

    fun salvar(chamada: Chamada, presencas: List<Presenca>, visitantes: List<Visitante>, onDone: () -> Unit) =
        viewModelScope.launch {
            repo.salvarChamada(chamada, presencas)
            visitantes.forEach { repo.salvarVisitante(it) }
            onDone()
        }

    /** Exclui a chamada carregada (junto das presenças e da oferta vinculada). */
    fun excluirChamada(onDone: () -> Unit) = viewModelScope.launch {
        _chamadaExistente.value?.let { repo.deletarChamada(it) }
        _chamadaExistente.value = null
        _presencasExistentes.value = emptyList()
        onDone()
    }
}

/* ----------------------- Relatórios ----------------------- */
class RelatoriosViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()

    /** Lista de classes ativas (para o filtro). */
    val classes = repo.classes.stateInDefault(viewModelScope, emptyList())

    /** Trimestre selecionado. Inicia no atual. */
    private val _trimestre = MutableStateFlow(Trimestre.atual())
    val trimestre: StateFlow<Trimestre> = _trimestre.asStateFlow()

    /** Classe selecionada para filtrar. `null` = visão geral (todas somadas). */
    private val _classeId = MutableStateFlow<Long?>(null)
    val classeId: StateFlow<Long?> = _classeId.asStateFlow()

    /** Lista de aulas do trimestre selecionado (uma entrada por DATA, agregada). */
    private val _aulas = MutableStateFlow<List<AulaListaUi>>(emptyList())
    val aulas: StateFlow<List<AulaListaUi>> = _aulas.asStateFlow()

    /** Relatório do trimestre selecionado (formato CPAD). */
    private val _relTrim = MutableStateFlow<RelTrimUi?>(null)
    val relTrim: StateFlow<RelTrimUi?> = _relTrim.asStateFlow()

    /** Relatório do dia atualmente aberto (quando o usuário clica numa aula). */
    private val _relDia = MutableStateFlow<RelDiaUi?>(null)
    val relDia: StateFlow<RelDiaUi?> = _relDia.asStateFlow()

    init { recarregar() }

    fun selecionarTrimestre(t: Trimestre) { _trimestre.value = t; recarregar() }
    fun trimestreAnterior() = selecionarTrimestre(_trimestre.value.anterior())
    fun trimestreProximo()  = selecionarTrimestre(_trimestre.value.proximo())
    fun selecionarClasse(id: Long?) { _classeId.value = id; recarregar() }

    /** Recalcula a lista de aulas e o relatório do trimestre conforme filtros. */
    // Dispatchers.Default: o agrupamento e a ordenação abaixo somam centenas de ms
    // num trimestre cheio, e viewModelScope roda em Main por padrão.
    private fun recarregar() = viewModelScope.launch(Dispatchers.Default) {
        val t = _trimestre.value
        val filtroClasse = _classeId.value
        val noTrim: (Long) -> Boolean = { it in t.inicioMillis() until t.fimExclusivoMillis() }

        // Lista de classes consideradas (todas, ou só a filtrada)
        val todasClasses = repo.listarClasses()
        val classesAtivas = if (filtroClasse == null) todasClasses
                            else todasClasses.filter { it.id == filtroClasse }

        // Todos os alunos ativos (para contar "matriculados" por classe — número atual)
        val matriculadosPorClasse: Map<Long, Int> = classesAtivas.associate { c ->
            c.id to repo.listarAlunosPorClasse(c.id).size
        }

        // Chamadas do trimestre (respeitando filtro de classe)
        val chamadasTrim = repo.listarTodasChamadas()
            .filter { noTrim(it.data) }
            .filter { filtroClasse == null || it.classeId == filtroClasse }

        // Pré-calcula presenças/bíblias/revistas por chamada. Uma consulta para o
        // trimestre inteiro: antes era uma por chamada, em série.
        data class Agg(val pres: Int, val bibl: Int, val rev: Int)
        val presencasPorChamada = repo.presencasDasChamadas(chamadasTrim.map { it.id })
        val porChamada: Map<Long, Agg> = chamadasTrim.associate { ch ->
            val presencas = presencasPorChamada[ch.id].orEmpty()
            ch.id to Agg(
                pres = presencas.count { it.presente },
                bibl = presencas.count { it.biblia && it.presente },
                rev  = presencas.count { it.revista && it.presente }
            )
        }

        // Visitantes do trimestre (vem da TABELA visitantes — cobre os dois fluxos:
        // os cadastrados via Chamada e os cadastrados via tela Visitantes)
        val visitantesTrim = repo.listarVisitantes()
            .filter { noTrim(it.data) }
            .filter { filtroClasse == null || it.classeId == filtroClasse }

        // -------- LISTA DE AULAS (continua agrupada por data) --------
        val totalClassesQueExistem = if (filtroClasse == null) todasClasses.size else 1
        val agrupado = chamadasTrim.groupBy { it.data }.toSortedMap()
        _aulas.value = agrupado.map { (data, chamadasDoDia) ->
            // matriculados na lista de aulas = soma dos matriculados das classes
            // que lançaram naquela data (visão do dia, não estado total)
            val mat = chamadasDoDia.sumOf { matriculadosPorClasse[it.classeId] ?: 0 }
            val pres = chamadasDoDia.sumOf { porChamada[it.id]?.pres ?: 0 }
            val oferta = chamadasDoDia.sumOf { it.oferta }
            AulaListaUi(
                data = data,
                classesQueLancaram = chamadasDoDia.size,
                totalClasses = totalClassesQueExistem,
                matriculados = mat,
                presentes = pres,
                pct = if (mat > 0) pres.toFloat() / mat else 0f,
                oferta = oferta
            )
        }

        // -------- RELATÓRIO DO TRIMESTRE (pivot por CLASSE) --------
        _relTrim.value = RelTrimUi(
            trimestre = t,
            colunas = classesAtivas.sortedBy { it.nome }.map { c ->
                val chamadasDaClasse = chamadasTrim.filter { it.classeId == c.id }
                val matric = matriculadosPorClasse[c.id] ?: 0
                val presentes = chamadasDaClasse.sumOf { porChamada[it.id]?.pres ?: 0 }
                val ausentesSoma = chamadasDaClasse.sumOf { ch ->
                    val matriculadosNaData = matric  // estado atual; aproximação
                    val p = porChamada[ch.id]?.pres ?: 0
                    (matriculadosNaData - p).coerceAtLeast(0)
                }
                val biblias = chamadasDaClasse.sumOf { porChamada[it.id]?.bibl ?: 0 }
                val revistas = chamadasDaClasse.sumOf { porChamada[it.id]?.rev ?: 0 }
                val oferta = chamadasDaClasse.sumOf { it.oferta }
                val visitantesClasse = visitantesTrim.count { it.classeId == c.id }
                RelTrimColuna(
                    classeId = c.id,
                    classeNome = c.nome,
                    matriculados = matric,
                    presentes = presentes,
                    ausentes = ausentesSoma,
                    visitantes = visitantesClasse,
                    biblias = biblias,
                    revistas = revistas,
                    oferta = oferta
                )
            }
        )
    }

    /** Carrega o relatório detalhado de UMA aula (data específica), respeitando o filtro de classe. */
    fun abrirRelatorioDia(data: Long) = viewModelScope.launch(Dispatchers.Default) {
        val filtroClasse = _classeId.value
        val nomes = repo.listarClasses().associate { it.id to it.nome }
        val chamadasDoDia = repo.listarTodasChamadas()
            .filter { it.data == data }
            .filter { filtroClasse == null || it.classeId == filtroClasse }

        // Visitantes contados da TABELA visitantes, igual ao relatório do trimestre.
        // Antes esta tela usava o contador `Chamada.visitantes`, acumulado na tela de
        // Chamada, e as duas telas mostravam números diferentes para o mesmo domingo.
        val visitantesDoDia = repo.listarVisitantes()
            .filter { it.data == data }
            .filter { filtroClasse == null || it.classeId == filtroClasse }
            .groupingBy { it.classeId }
            .eachCount()

        val presencasPorChamada = repo.presencasDasChamadas(chamadasDoDia.map { it.id })
        val linhas = chamadasDoDia.map { ch ->
            val presencas = presencasPorChamada[ch.id].orEmpty()
            val matric = presencas.size
            val pres = presencas.count { it.presente }
            val bibl = presencas.count { it.biblia && it.presente }
            val rev  = presencas.count { it.revista && it.presente }
            RelDiaLinhaClasse(
                classeId = ch.classeId,
                classeNome = nomes[ch.classeId] ?: "—",
                matriculados = matric,
                presentes = pres,
                ausentes = matric - pres,
                visitantes = visitantesDoDia[ch.classeId] ?: 0,
                biblias = bibl,
                revistas = rev,
                oferta = ch.oferta,
                licao = ch.licao
            )
        }.sortedBy { it.classeNome }
        _relDia.value = RelDiaUi(data = data, linhas = linhas)
    }

    fun fecharRelatorioDia() { _relDia.value = null }
}

/* ----------------------- Pontuação ----------------------- */
/** Estado de marcação de um aluno na tela de pontos (id do critério -> quantidade marcada). */
data class AlunoPontosUi(
    val aluno: Aluno,
    val marcas: Map<Long, Int>,   // criterioId -> quantidade (0/ausente = não marcado)
    val total: Int
)
/** Linha do ranking: aluno + total de pontos no período + nº de faltas (desempate). */
data class RankingLinhaUi(
    val aluno: Aluno,
    val classeNome: String,
    val pontos: Int,
    val faltas: Int
)

class PontuacaoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()

    val classes = repo.classes.stateInDefault(viewModelScope, emptyList())
    val criterios = repo.criterios.stateInDefault(viewModelScope, emptyList())

    /* ---- Marcação rápida (por classe + data) ---- */
    private val _classeId = MutableStateFlow<Long?>(null)
    val classeId: StateFlow<Long?> = _classeId.asStateFlow()

    private val _data = MutableStateFlow(hojeMillis())
    val data: StateFlow<Long> = _data.asStateFlow()

    private val _alunos = MutableStateFlow<List<AlunoPontosUi>>(emptyList())
    val alunos: StateFlow<List<AlunoPontosUi>> = _alunos.asStateFlow()

    fun setClasse(id: Long?) { _classeId.value = id; recarregarMarcacao() }
    fun setData(d: Long) { _data.value = d; recarregarMarcacao() }

    /** (Re)carrega os alunos da classe/data e o que já foi marcado naquele dia. */
    fun recarregarMarcacao() = viewModelScope.launch {
        val cid = _classeId.value
        if (cid == null) { _alunos.value = emptyList(); return@launch }
        val d = _data.value
        val lista = repo.listarAlunosPorClasse(cid)
        _alunos.value = lista.map { a ->
            val lancs = repo.pontosDoAlunoNaData(a.id, d)
            val marcas = lancs.associate { it.criterioId to it.quantidade }
            AlunoPontosUi(a, marcas, lancs.sumOf { it.pontos })
        }
    }

    /**
     * Marca/atualiza um critério para um aluno. quantidade<=0 remove (toggle off).
     * Recarrega só o aluno afetado para manter a tela fluida.
     */
    fun marcar(alunoId: Long, criterio: CriterioPontuacao, quantidade: Int) = viewModelScope.launch {
        repo.marcarPonto(alunoId, criterio, _data.value, quantidade)
        val lancs = repo.pontosDoAlunoNaData(alunoId, _data.value)
        val marcas = lancs.associate { it.criterioId to it.quantidade }
        val total = lancs.sumOf { it.pontos }
        _alunos.value = _alunos.value.map {
            if (it.aluno.id == alunoId) it.copy(marcas = marcas, total = total) else it
        }
    }

    /** Alterna um critério de toque único (presença, pontualidade...). */
    fun alternar(alunoId: Long, criterio: CriterioPontuacao, marcadoAgora: Boolean) =
        marcar(alunoId, criterio, if (marcadoAgora) 1 else 0)

    /* ---- Critérios (catálogo editável) ---- */
    fun salvarCriterio(c: CriterioPontuacao) = viewModelScope.launch {
        repo.salvarCriterio(c); recarregarMarcacao()
    }
    fun deletarCriterio(c: CriterioPontuacao) = viewModelScope.launch { repo.deletarCriterio(c) }

    /* ---- Ranking / relatório ---- */
    private val _trimestre = MutableStateFlow(Trimestre.atual())
    val trimestre: StateFlow<Trimestre> = _trimestre.asStateFlow()

    private val _classeRankingId = MutableStateFlow<Long?>(null)
    val classeRankingId: StateFlow<Long?> = _classeRankingId.asStateFlow()

    private val _ranking = MutableStateFlow<List<RankingLinhaUi>>(emptyList())
    val ranking: StateFlow<List<RankingLinhaUi>> = _ranking.asStateFlow()

    fun setTrimestreRanking(t: Trimestre) { _trimestre.value = t; recarregarRanking() }
    fun setClasseRanking(id: Long?) { _classeRankingId.value = id; recarregarRanking() }

    /** Soma os pontos do período por aluno e conta faltas (para desempate). */
    fun recarregarRanking() = viewModelScope.launch(Dispatchers.Default) {
        val t = _trimestre.value
        val ini = t.inicioMillis(); val fim = t.fimExclusivoMillis()
        val filtro = _classeRankingId.value
        val nomes = repo.listarClasses().associate { it.id to it.nome }
        val alunos = repo.listarTodosAlunos().filter { it.ativo }
            .filter { filtro == null || it.classeId == filtro }

        val pontosPorAluno = repo.pontosDoPeriodo(ini, fim)
            .groupBy { it.alunoId }
            .mapValues { (_, l) -> l.sumOf { it.pontos } }

        // Faltas = chamadas da classe no período em que o aluno constava ausente.
        val chamadas = repo.listarTodasChamadas().filter { it.data in ini until fim }
        val faltasPorAluno = HashMap<Long, Int>()
        repo.presencasDasChamadas(chamadas.map { it.id }).values.flatten().forEach { p ->
            if (!p.presente) faltasPorAluno[p.alunoId] = (faltasPorAluno[p.alunoId] ?: 0) + 1
        }

        _ranking.value = alunos.map { a ->
            RankingLinhaUi(
                aluno = a,
                classeNome = nomes[a.classeId] ?: "",
                pontos = pontosPorAluno[a.id] ?: 0,
                faltas = faltasPorAluno[a.id] ?: 0
            )
        }.sortedWith(compareByDescending<RankingLinhaUi> { it.pontos }.thenBy { it.faltas }.thenBy { it.aluno.nome })
    }

    init { recarregarRanking() }
}

/* ----------------------- Visitantes ----------------------- */
class VisitantesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()
    val classes = repo.classes.stateInDefault(viewModelScope, emptyList())
    val lista: StateFlow<List<VisitanteUi>> =
        combine(repo.visitantes, repo.classes) { vis, classes ->
            val nomes = classes.associate { it.id to it.nome }
            vis.map { VisitanteUi(it, it.classeId?.let { id -> nomes[id] } ?: "") }
        }.stateInDefault(viewModelScope, emptyList())

    fun salvar(v: Visitante) = viewModelScope.launch { repo.salvarVisitante(v) }
    fun deletar(v: Visitante) = viewModelScope.launch { repo.deletarVisitante(v) }
    fun converter(v: Visitante, classeId: Long) = viewModelScope.launch {
        repo.converterVisitanteEmAluno(v, classeId)
    }
}

/* ----------------------- Dashboard ----------------------- */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()

    val state: StateFlow<DashboardState> = combine(
        repo.classes, repo.alunos, repo.chamadas, repo.visitantes
    ) { classes, alunos, chamadas, visit ->
        val ultima = chamadas.firstOrNull()

        // Ofertas do mês, somadas das chamadas. Antes era o saldo da tabela
        // `financeiro`, que saiu com a tela de Finanças; a oferta lançada na chamada é
        // o único dinheiro que o app registra de fato.
        val mesAtual = try { YearMonth.now() } catch (e: Exception) { null }
        val ofertas = if (mesAtual != null) {
            chamadas.filter {
                try { YearMonth.from(it.data.toLocalDate()) == mesAtual } catch (e: Exception) { false }
            }.sumOf { it.oferta }
        } else 0.0

        val nomesClasse = classes.associate { it.id to it.nome }
        val aniv = alunos.filter { it.dataNascimento != null }.mapNotNull { a ->
            try {
                val info = calcularAniversario(a.dataNascimento!!)
                AniversarianteUi(a.nome, nomesClasse[a.classeId] ?: "", a.dataNascimento,
                    info.idadeQueFara, info.diaSemana, info.diasAte, quandoLabel(info.diasAte))
            } catch (e: Exception) { null }
        }.filter { it.diasAte <= 30 }.sortedBy { it.diasAte }

        DashboardState(
            totalClasses = classes.size,
            totalAlunos = alunos.size,
            ultimaData = ultima?.data,
            ultimaPct = 0f,
            ofertasMes = ofertas,
            aniversariantes = aniv,
            visitantesPendentes = visit.count { !it.convertido }
        )
    }.stateInDefault(viewModelScope, DashboardState())
}

/* ----------------------- Aniversariantes ----------------------- */
class AniversariantesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()
    val lista: StateFlow<List<AniversarianteUi>> =
        combine(repo.alunos, repo.classes) { alunos, classes ->
            val nomes = classes.associate { it.id to it.nome }
            alunos.filter { it.dataNascimento != null }.mapNotNull { a ->
                try {
                    val info = calcularAniversario(a.dataNascimento!!)
                    AniversarianteUi(a.nome, nomes[a.classeId] ?: "", a.dataNascimento,
                        info.idadeQueFara, info.diaSemana, info.diasAte, quandoLabel(info.diasAte))
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.diasAte }
        }.stateInDefault(viewModelScope, emptyList())
}

private fun quandoLabel(dias: Long): String = when {
    dias == 0L -> "🎂 Hoje"
    dias <= 7 -> "Esta semana"
    dias <= 31 -> "Este mês"
    else -> "—"
}

private fun <T> Flow<T>.stateInDefault(scope: kotlinx.coroutines.CoroutineScope, initial: T) =
    stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)

/* ----------------------- Configurações ----------------------- */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo()
    private val sync = (app as EBDApp).syncManager
    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _syncUrl = MutableStateFlow(prefs.getString("sync_url", "") ?: "")
    val syncUrl: StateFlow<String> = _syncUrl.asStateFlow()

    private val _autoSync = MutableStateFlow(prefs.getBoolean("auto_sync", true))
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    /** Estado e horário da última sincronização vêm direto do SyncManager. */
    val syncStatus: StateFlow<SyncStatus> = sync.status
    val ultimaSync: StateFlow<Long> = sync.ultimaSync

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit().putBoolean("dark_mode", enabled).apply()
            _isDarkMode.value = enabled
        }
    }

    fun setSyncUrl(url: String) {
        val limpa = url.trim()
        prefs.edit().putString("sync_url", limpa).apply()
        _syncUrl.value = limpa
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sync", enabled).apply()
        _autoSync.value = enabled
        if (enabled) sync.aoAbrir()   // ligou: já sincroniza e religa o periódico
    }

    /** Botão "Sincronizar agora": força uma sincronização imediata de mão dupla. */
    fun sincronizar(onResult: (Boolean, String) -> Unit) {
        sync.disparar(manual = true, onResult = onResult)
    }

    /**
     * Apaga TODOS os dados do app (classes, membros, chamadas, finanças,
     * visitantes) deixando o aplicativo "do zero". NÃO mexe nas preferências:
     * a URL da planilha e o tema escolhido permanecem.
     */
    fun resetarApp(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.limparTudo()
                onResult(true, "App resetado. Todos os dados foram apagados.")
            } catch (e: Exception) {
                onResult(false, "Falha ao resetar: ${e.message}")
            }
        }
    }
}
