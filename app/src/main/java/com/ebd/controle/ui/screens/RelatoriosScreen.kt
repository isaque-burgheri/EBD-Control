package com.ebd.controle.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.Classe
import com.ebd.controle.data.Trimestre
import com.ebd.controle.data.formatarData
import com.ebd.controle.data.formatarDataCurta
import com.ebd.controle.data.formatarMoeda
import com.ebd.controle.ui.AulaListaUi
import com.ebd.controle.ui.RelDiaUi
import com.ebd.controle.ui.RelTrimUi
import com.ebd.controle.ui.RelatoriosViewModel
import com.ebd.controle.ui.theme.Azul
import com.ebd.controle.ui.theme.LocalAppBrushes
import com.ebd.controle.ui.theme.Verde
import com.ebd.controle.ui.theme.Vermelho

@Composable
fun RelatoriosScreen() {
    val vm: RelatoriosViewModel = viewModel()
    val classes by vm.classes.collectAsState()
    val trimestre by vm.trimestre.collectAsState()
    val classeId by vm.classeId.collectAsState()
    val aulas by vm.aulas.collectAsState()
    val relTrim by vm.relTrim.collectAsState()
    val relDia by vm.relDia.collectAsState()

    var mostrarTrim by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        SeletorTrimestre(
            trimestre = trimestre,
            onAnterior = { vm.trimestreAnterior() },
            onProximo = { vm.trimestreProximo() }
        )
        Spacer(Modifier.height(10.dp))
        FiltroClasses(classes = classes, selecionada = classeId, onSelecionar = vm::selecionarClasse)
        Spacer(Modifier.height(12.dp))
        BotaoRelatorioTrimestre(onClick = { mostrarTrim = true })
        Spacer(Modifier.height(14.dp))

        Text(
            "Aulas do trimestre",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))

        if (aulas.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma aula lançada neste trimestre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(aulas.sortedByDescending { it.data }) { a ->
                    CardAula(a = a, soUmaClasse = classeId != null) {
                        vm.abrirRelatorioDia(a.data)
                    }
                }
            }
        }
    }

    // Relatório do DIA (modal full-screen)
    relDia?.let { dia ->
        DialogoRelatorioDia(
            dia = dia,
            classes = classes,
            classeFiltro = classeId,
            onFechar = { vm.fecharRelatorioDia() }
        )
    }

    // Relatório do TRIMESTRE (modal full-screen)
    if (mostrarTrim) {
        relTrim?.let { rt ->
            DialogoRelatorioTrimestre(
                rt = rt,
                classeNome = classes.firstOrNull { it.id == classeId }?.nome,
                onFechar = { mostrarTrim = false }
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* ----------------------- Seleção de trimestre --------------------- */
/* ------------------------------------------------------------------ */

@Composable
private fun SeletorTrimestre(trimestre: Trimestre, onAnterior: () -> Unit, onProximo: () -> Unit) {
    val escuro = LocalAppBrushes.current.escuro
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (escuro) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAnterior) { Icon(Icons.Filled.ChevronLeft, "Anterior") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    trimestre.rotulo,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    trimestre.mesesAbreviados.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onProximo) { Icon(Icons.Filled.ChevronRight, "Próximo") }
        }
    }
}

/* ------------------------------------------------------------------ */
/* ------------------------ Filtro de classe ------------------------ */
/* ------------------------------------------------------------------ */

@Composable
private fun FiltroClasses(classes: List<Classe>, selecionada: Long?, onSelecionar: (Long?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChipClasse(rotulo = "Todas", ativo = selecionada == null) { onSelecionar(null) }
        classes.forEach { c ->
            ChipClasse(rotulo = c.nome, ativo = selecionada == c.id) { onSelecionar(c.id) }
        }
    }
}

@Composable
private fun ChipClasse(rotulo: String, ativo: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = ativo,
        onClick = onClick,
        label = { Text(rotulo) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

/* ------------------------------------------------------------------ */
/* ------------------- Botão / cards de aula ------------------------ */
/* ------------------------------------------------------------------ */

@Composable
private fun BotaoRelatorioTrimestre(onClick: () -> Unit) {
    val escuro = LocalAppBrushes.current.escuro
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (escuro) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.TableChart, null, tint = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Relatório do trimestre", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Visão consolidada estilo CPAD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CardAula(a: AulaListaUi, soUmaClasse: Boolean, onClick: () -> Unit) {
    val escuro = LocalAppBrushes.current.escuro
    val pct = (a.pct * 100).toInt()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (escuro) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatarData(a.data), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                val info = buildString {
                    if (!soUmaClasse) append("${a.classesQueLancaram}/${a.totalClasses} classes • ")
                    append("${a.presentes}/${a.matriculados} presentes")
                    if (a.oferta > 0) append(" • Oferta ${formatarMoeda(a.oferta)}")
                }
                Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            BadgePresenca(pct = pct)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BadgePresenca(pct: Int) {
    val cor = when {
        pct >= 80 -> Verde
        pct >= 50 -> Azul
        else -> Vermelho
    }
    Surface(
        color = cor.copy(alpha = 0.18f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            "$pct%",
            color = cor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/* ------------------------------------------------------------------ */
/* ----------------------- Diálogo RELATÓRIO DO DIA ----------------- */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoRelatorioDia(
    dia: RelDiaUi,
    classes: List<Classe>,
    classeFiltro: Long?,
    onFechar: () -> Unit
) {
    Dialog(onDismissRequest = onFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Relatório da aula", style = MaterialTheme.typography.titleLarge)
                            Text(
                                formatarData(dia.data),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onFechar) { Icon(Icons.Filled.Close, "Fechar") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Resumo de cima
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (classeFiltro == null) "Visão geral"
                                else "Classe: ${classes.firstOrNull { it.id == classeFiltro }?.nome ?: "—"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ResumoMini("Matriculados", dia.matriculados.toString(), Modifier.weight(1f))
                                ResumoMini("Presentes", dia.presentes.toString(), Modifier.weight(1f), Verde)
                                ResumoMini("Ausentes", dia.ausentes.toString(), Modifier.weight(1f), Vermelho)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ResumoMini("Visitantes", dia.visitantes.toString(), Modifier.weight(1f))
                                ResumoMini("Bíblias", dia.biblias.toString(), Modifier.weight(1f))
                                ResumoMini("Revistas", dia.revistas.toString(), Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Presença", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${(dia.pctPresenca * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Oferta", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(formatarMoeda(dia.oferta), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    // Quebra por classe (sempre que houver mais de uma classe na visão)
                    if (dia.linhas.size > 1) {
                        Text(
                            "Por classe",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        dia.linhas.forEach { l ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row {
                                        Text(l.classeNome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        val pct = if (l.matriculados > 0) (l.presentes * 100 / l.matriculados) else 0
                                        BadgePresenca(pct = pct)
                                    }
                                    if (l.licao > 0) {
                                        Text(
                                            "Lição ${l.licao}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LinhaInfo("Matriculados", l.matriculados.toString())
                                    LinhaInfo("Presentes", l.presentes.toString())
                                    LinhaInfo("Ausentes", l.ausentes.toString())
                                    LinhaInfo("Visitantes", l.visitantes.toString())
                                    LinhaInfo("Bíblias", l.biblias.toString())
                                    LinhaInfo("Revistas", l.revistas.toString())
                                    LinhaInfo("Oferta", formatarMoeda(l.oferta))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ResumoMini(rotulo: String, valor: String, modifier: Modifier = Modifier, cor: Color? = null) {
    Column(modifier) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            valor,
            style = MaterialTheme.typography.titleLarge,
            color = cor ?: MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun LinhaInfo(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/* ------------------------------------------------------------------ */
/* --------------- Diálogo RELATÓRIO DO TRIMESTRE (CPAD) ------------ */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoRelatorioTrimestre(rt: RelTrimUi, classeNome: String?, onFechar: () -> Unit) {
    Dialog(onDismissRequest = onFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Relatório do trimestre", style = MaterialTheme.typography.titleLarge)
                            Text(
                                buildString {
                                    append(rt.trimestre.rotulo)
                                    if (classeNome != null) append(" • $classeNome")
                                    else append(" • Geral")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onFechar) { Icon(Icons.Filled.Close, "Fechar") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                if (rt.colunas.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Sem aulas lançadas neste trimestre.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    TabelaTrimestre(rt = rt, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp))
                }
            }
        }
    }
}

/**
 * A tabela do trimestre: linhas fixas à esquerda (rótulos das métricas), e
 * à direita uma área que rola horizontalmente, com uma coluna por CLASSE +
 * uma coluna TOTAL no final.
 */
@Composable
private fun TabelaTrimestre(rt: RelTrimUi, modifier: Modifier = Modifier) {
    val labelW = 130.dp   // largura da coluna de rótulos
    val colW = 110.dp     // colunas de classe: mais largas pra caber o nome
    val rowH = 40.dp
    val cellBg = MaterialTheme.colorScheme.surface
    val rowAlt = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val borda = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val headerBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    // Cada linha: par (rótulo, valores por classe, total)
    data class Linha(val rotulo: String, val valores: List<String>, val total: String)
    fun n(i: Int) = i.toString()
    fun d(v: Double) = String.format(java.util.Locale("pt","BR"), "%.2f", v)

    val linhas = listOf(
        Linha("Matriculados", rt.colunas.map { n(it.matriculados) }, n(rt.totalMatric)),
        Linha("Ausentes",     rt.colunas.map { n(it.ausentes) },     n(rt.totalAusent)),
        Linha("Presentes",    rt.colunas.map { n(it.presentes) },    n(rt.totalPres)),
        Linha("Visitantes",   rt.colunas.map { n(it.visitantes) },   n(rt.totalVisit)),
        Linha("Bíblias",      rt.colunas.map { n(it.biblias) },      n(rt.totalBibl)),
        Linha("Revistas",     rt.colunas.map { n(it.revistas) },     n(rt.totalRev)),
        Linha("Ofertas (R$)", rt.colunas.map { d(it.oferta) },       d(rt.totalOferta))
    )

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(modifier) {
        // ===== Cabeçalho =====
        Row(
            Modifier.fillMaxWidth().height(rowH)
                .background(headerBg)
                .border(0.5.dp, borda)
        ) {
            CelulaCabec("RELATÓRIO", labelW, rowH)
            Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                rt.colunas.forEach { c ->
                    CelulaCabec(c.classeNome, colW, rowH)
                }
                CelulaCabec("TOTAL", colW, rowH)
            }
        }

        // ===== Linhas de métricas =====
        Column(Modifier.weight(1f).verticalScroll(vScroll)) {
            linhas.forEachIndexed { idx, linha ->
                val bg = if (idx % 2 == 0) cellBg else rowAlt
                Row(Modifier.fillMaxWidth().height(rowH).background(bg).border(0.5.dp, borda)) {
                    CelulaRotulo(linha.rotulo, labelW, rowH)
                    Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                        linha.valores.forEach { txt -> CelulaValor(txt, colW, rowH) }
                        CelulaValor(linha.total, colW, rowH, destaque = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CelulaCabec(texto: String, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(w).height(h)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun CelulaRotulo(texto: String, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(w).height(h).padding(horizontal = 8.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun CelulaValor(
    texto: String,
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    destaque: Boolean = false
) {
    Box(
        Modifier.width(w).height(h)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            .background(
                if (destaque) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
            color = if (destaque) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
