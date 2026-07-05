package com.ebd.controle.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.CriterioPontuacao
import com.ebd.controle.ui.AlunoPontosUi
import com.ebd.controle.ui.PontuacaoViewModel
import com.ebd.controle.ui.RankingLinhaUi
import com.ebd.controle.ui.components.Dropdown
import com.ebd.controle.ui.components.DateField
import com.ebd.controle.ui.components.Kicker
import com.ebd.controle.ui.theme.Azul
import com.ebd.controle.ui.theme.Verde

/** Rótulos amigáveis para os grupos de critérios. */
private val gruposOrdem = listOf("REGULAR", "ESPECIAL", "CAFE", "CESTA")
private fun rotuloGrupo(g: String): String = when (g) {
    "REGULAR" -> "Regulares"
    "ESPECIAL" -> "Especiais (inclusão)"
    "CAFE" -> "Alimento p/ o café"
    "CESTA" -> "Cesta básica"
    else -> g
}

@Composable
fun PontuacaoScreen() {
    val vm: PontuacaoViewModel = viewModel()
    val classes by vm.classes.collectAsState()
    val criterios by vm.criterios.collectAsState()

    var aba by remember { mutableStateOf(0) }
    var mostrarCriterios by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarCriterios = true },
                icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                text = { Text("Critérios") }
            )
        }
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = aba, containerColor = Color.Transparent) {
                Tab(aba == 0, onClick = { aba = 0 }, text = { Text("Marcar pontos") })
                Tab(aba == 1, onClick = { aba = 1; vm.recarregarRanking() }, text = { Text("Ranking") })
            }
            Spacer(Modifier.height(8.dp))

            if (aba == 0) AbaMarcar(vm, classes, criterios)
            else AbaRanking(vm, classes)
        }
    }

    if (mostrarCriterios) {
        CriteriosDialog(
            criterios = criterios,
            onSalvar = { vm.salvarCriterio(it) },
            onExcluir = { vm.deletarCriterio(it) },
            onFechar = { mostrarCriterios = false }
        )
    }
}

/* ============================ MARCAR PONTOS ============================ */
@Composable
private fun AbaMarcar(
    vm: PontuacaoViewModel,
    classes: List<com.ebd.controle.data.Classe>,
    criterios: List<CriterioPontuacao>
) {
    val data by vm.data.collectAsState()
    val classeId by vm.classeId.collectAsState()
    val alunos by vm.alunos.collectAsState()

    // Seleciona a 1ª classe automaticamente quando a tela abre.
    LaunchedEffect(classes) {
        if (classeId == null && classes.isNotEmpty()) vm.setClasse(classes.first().id)
    }

    val classeIdx = classes.indexOfFirst { it.id == classeId }.coerceAtLeast(0)

    if (classes.isEmpty()) {
        Text("Crie uma classe e membros primeiro.")
        return
    }

    Column {
        Dropdown("Classe", classes.map { it.nome }, classeIdx,
            { idx -> vm.setClasse(classes.getOrNull(idx)?.id) })
        Spacer(Modifier.height(8.dp))
        DateField("Data da aula", data, onPick = { vm.setData(it) })
        Spacer(Modifier.height(8.dp))

        if (criterios.isEmpty()) {
            Text("Cadastre critérios de pontuação (botão Critérios) antes de marcar.",
                color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            return@Column
        }
        if (alunos.isEmpty()) {
            Text("Nenhum aluno ativo nesta classe.")
            return@Column
        }

        LazyColumn {
            items(alunos, key = { it.aluno.id }) { linha ->
                CartaoAlunoPontos(linha, criterios, vm)
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun CartaoAlunoPontos(
    linha: AlunoPontosUi,
    criterios: List<CriterioPontuacao>,
    vm: PontuacaoViewModel
) {
    // Critérios do grupo certo: aluno especial vê ESPECIAL; os demais veem REGULAR.
    // Alimentos (CAFE/CESTA) aparecem para todos, pois qualquer aluno pode trazer.
    val grupoAluno = if (linha.aluno.especial) "ESPECIAL" else "REGULAR"
    val visiveis = criterios.filter {
        it.ativo && (it.grupo == grupoAluno || it.grupo == "CAFE" || it.grupo == "CESTA")
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(linha.aluno.nome, fontWeight = FontWeight.SemiBold)
                    if (linha.aluno.especial) {
                        Text("Especial (inclusão)", fontSize = 11.sp, color = Azul)
                    }
                }
                // Total do aluno no dia, em destaque.
                Surface(
                    color = if (linha.total > 0) Verde.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "${linha.total} pts",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        color = if (linha.total > 0) Verde else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            // Agrupa os chips por grupo, com o rótulo do grupo antes.
            gruposOrdem.forEach { g ->
                val doGrupo = visiveis.filter { it.grupo == g }
                if (doGrupo.isNotEmpty()) {
                    Kicker(rotuloGrupo(g))
                    Spacer(Modifier.height(4.dp))
                    FlowChips(doGrupo, linha, vm)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Linha(s) de chips que quebram naturalmente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(
    criterios: List<CriterioPontuacao>,
    linha: AlunoPontosUi,
    vm: PontuacaoViewModel
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        criterios.forEach { c ->
            val qtd = linha.marcas[c.id] ?: 0
            if (c.porQuantidade) {
                // Chip com stepper (− qtd +) para alimentos por unidade/peso.
                ChipQuantidade(
                    rotulo = c.nome,
                    qtd = qtd,
                    onMenos = { vm.marcar(linha.aluno.id, c, (qtd - 1).coerceAtLeast(0)) },
                    onMais = { vm.marcar(linha.aluno.id, c, qtd + 1) }
                )
            } else {
                // Toque único (presença, pontualidade...).
                FilterChip(
                    selected = qtd > 0,
                    onClick = { vm.alternar(linha.aluno.id, c, qtd == 0) },
                    label = { Text("${c.nome} +${c.pontos}") }
                )
            }
        }
    }
}

@Composable
private fun ChipQuantidade(rotulo: String, qtd: Int, onMenos: () -> Unit, onMais: () -> Unit) {
    val ativo = qtd > 0
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (ativo) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenos, modifier = Modifier.size(32.dp), enabled = ativo) {
                Icon(Icons.Filled.Remove, "Menos", modifier = Modifier.size(16.dp))
            }
            Text(
                if (ativo) "$rotulo ×$qtd" else rotulo,
                fontSize = 13.sp,
                fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Normal
            )
            IconButton(onClick = onMais, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, "Mais", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/* ============================ RANKING ============================ */
@Composable
private fun AbaRanking(
    vm: PontuacaoViewModel,
    classes: List<com.ebd.controle.data.Classe>
) {
    val trimestre by vm.trimestre.collectAsState()
    val classeRankingId by vm.classeRankingId.collectAsState()
    val ranking by vm.ranking.collectAsState()

    val opcoesFiltro = listOf("Todas as classes") + classes.map { it.nome }
    val filtroIdx = if (classeRankingId == null) 0
                    else (classes.indexOfFirst { it.id == classeRankingId } + 1).coerceAtLeast(0)

    Column {
        // Seletor de trimestre.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.setTrimestreRanking(trimestre.anterior()) }) {
                Icon(Icons.Filled.ChevronLeft, "Trimestre anterior")
            }
            Text(trimestre.rotulo, Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
            IconButton(onClick = { vm.setTrimestreRanking(trimestre.proximo()) }) {
                Icon(Icons.Filled.ChevronRight, "Próximo trimestre")
            }
        }
        Spacer(Modifier.height(4.dp))
        Dropdown("Filtrar por classe", opcoesFiltro, filtroIdx,
            { idx -> vm.setClasseRanking(if (idx == 0) null else classes.getOrNull(idx - 1)?.id) })
        Spacer(Modifier.height(8.dp))

        if (ranking.all { it.pontos == 0 }) {
            Text("Ainda não há pontos lançados neste trimestre.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        LazyColumn {
            itemsIndexed(ranking) { idx, linha ->
                LinhaRanking(idx + 1, linha)
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun LinhaRanking(posicao: Int, linha: RankingLinhaUi) {
    val corMedalha = when (posicao) {
        1 -> Color(0xFFD4AF37)   // ouro
        2 -> Color(0xFF9AA0A6)   // prata
        3 -> Color(0xFFB08D57)   // bronze
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(corMedalha.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (posicao <= 3) Icon(Icons.Filled.EmojiEvents, null, tint = corMedalha, modifier = Modifier.size(20.dp))
                else Text("$posicao", fontWeight = FontWeight.Bold, color = corMedalha)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(linha.aluno.nome, fontWeight = FontWeight.SemiBold)
                Text("${linha.classeNome} • ${linha.faltas} falta(s)",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${linha.pontos}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Verde)
            Text(" pts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ============================ CRITÉRIOS (catálogo) ============================ */
@Composable
private fun CriteriosDialog(
    criterios: List<CriterioPontuacao>,
    onSalvar: (CriterioPontuacao) -> Unit,
    onExcluir: (CriterioPontuacao) -> Unit,
    onFechar: () -> Unit
) {
    var editando by remember { mutableStateOf<CriterioPontuacao?>(null) }
    var criando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Critérios de pontuação") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Toque para editar. Cada critério vale a pontuação indicada.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                gruposOrdem.forEach { g ->
                    val doGrupo = criterios.filter { it.grupo == g }
                    if (doGrupo.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Kicker(rotuloGrupo(g))
                        doGrupo.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { editando = c }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.nome)
                                    if (c.porQuantidade)
                                        Text("por unidade", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("+${c.pontos}", fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = { onExcluir(c) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Delete, "Excluir",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { criando = true }) { Text("+ Novo critério") }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )

    val alvo = editando
    if (alvo != null || criando) {
        CriterioEditDialog(
            inicial = alvo,
            onConfirmar = { onSalvar(it); editando = null; criando = false },
            onCancelar = { editando = null; criando = false }
        )
    }
}

@Composable
private fun CriterioEditDialog(
    inicial: CriterioPontuacao?,
    onConfirmar: (CriterioPontuacao) -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var pontos by remember { mutableStateOf(inicial?.pontos?.toString() ?: "") }
    var grupoIdx by remember { mutableStateOf(gruposOrdem.indexOf(inicial?.grupo ?: "REGULAR").coerceAtLeast(0)) }
    var porQtd by remember { mutableStateOf(inicial?.porQuantidade ?: false) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (inicial == null) "Novo critério" else "Editar critério") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nome, { nome = it },
                    label = { Text("Nome (ex.: Presença)") }, singleLine = true)
                OutlinedTextField(pontos, { pontos = it.filter { c -> c.isDigit() } },
                    label = { Text("Pontos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Dropdown("Grupo", gruposOrdem.map { rotuloGrupo(it) }, grupoIdx, { grupoIdx = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(porQtd, onCheckedChange = { porQtd = it })
                    Text("Multiplicar por quantidade (ex.: 3 pacotes de arroz)")
                }
            }
        },
        confirmButton = {
            val p = pontos.toIntOrNull() ?: 0
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    onConfirmar(
                        (inicial ?: CriterioPontuacao(nome = "")).copy(
                            nome = nome.trim(), pontos = p,
                            grupo = gruposOrdem[grupoIdx], porQuantidade = porQtd
                        )
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
