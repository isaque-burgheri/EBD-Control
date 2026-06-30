package com.ebd.controle.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.Aluno
import com.ebd.controle.data.RevistaPreco
import com.ebd.controle.data.Trimestre
import com.ebd.controle.data.formatarMoeda
import com.ebd.controle.ui.RevistaAlunoUi
import com.ebd.controle.ui.RevistasViewModel
import com.ebd.controle.ui.components.Dropdown
import com.ebd.controle.ui.theme.Verde
import com.ebd.controle.ui.theme.Vermelho

@Composable
fun RevistasScreen() {
    val vm: RevistasViewModel = viewModel()
    val trimestre by vm.trimestre.collectAsState()
    val classes by vm.classes.collectAsState()
    val classeId by vm.classeId.collectAsState()
    val linhas by vm.linhas.collectAsState()
    val precos by vm.precos.collectAsState()

    var alunoSelecionado by remember { mutableStateOf<RevistaAlunoUi?>(null) }
    var mostrarPrecos by remember { mutableStateOf(false) }

    val opcoesFiltro = listOf("Todas as classes") + classes.map { it.nome }
    val filtroIdx = if (classeId == null) 0 else (classes.indexOfFirst { it.id == classeId } + 1).coerceAtLeast(0)

    val totalFisicas = linhas.count { it.entrega?.tipo == "FISICA" }
    val totalDigitais = linhas.count { it.entrega?.tipo == "DIGITAL" }
    val totalDespesa = linhas.filter { it.entrega?.tipo == "FISICA" }.sumOf { it.entrega?.preco ?: 0.0 }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarPrecos = true },
                icon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                text = { Text("Preços") }
            )
        }
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(8.dp))

            // Seletor de trimestre
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.setTrimestre(trimestre.anterior()) }) {
                    Icon(Icons.Filled.ChevronLeft, "Trimestre anterior")
                }
                Text(
                    trimestre.rotulo,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = { vm.setTrimestre(trimestre.proximo()) }) {
                    Icon(Icons.Filled.ChevronRight, "Próximo trimestre")
                }
            }

            Spacer(Modifier.height(4.dp))
            Dropdown(
                "Filtrar por classe", opcoesFiltro, filtroIdx,
                { idx -> vm.setClasse(if (idx == 0) null else classes.getOrNull(idx - 1)?.id) }
            )

            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ResumoItem("Físicas", totalFisicas.toString(), Verde)
                    ResumoItem("Digitais", totalDigitais.toString(), MaterialTheme.colorScheme.primary)
                    ResumoItem("Despesa", formatarMoeda(totalDespesa), Vermelho)
                }
            }

            Spacer(Modifier.height(8.dp))
            if (linhas.isEmpty()) {
                Text("Nenhum aluno ativo encontrado. Cadastre membros primeiro.")
            }
            LazyColumn {
                items(linhas) { linha ->
                    val e = linha.entrega
                    val (rotulo, cor) = when (e?.tipo) {
                        "FISICA" -> "Física • ${formatarMoeda(e.preco)}" to Verde
                        "DIGITAL" -> "Digital (PDF) • grátis" to MaterialTheme.colorScheme.primary
                        else -> "Sem revista" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { alunoSelecionado = linha },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(linha.aluno.nome, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(linha.classeNome) },
                            trailingContent = {
                                Text(rotulo, color = cor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    alunoSelecionado?.let { linha ->
        RevistaAlunoDialog(
            linha = linha,
            precos = precos,
            onConfirmar = { tipo, categoria, preco ->
                vm.definir(linha.aluno, tipo, categoria, preco)
                alunoSelecionado = null
            },
            onCancelar = { alunoSelecionado = null }
        )
    }

    if (mostrarPrecos) {
        PrecosDialog(
            precos = precos,
            onSalvar = { vm.salvarPreco(it) },
            onExcluir = { vm.deletarPreco(it) },
            onFechar = { mostrarPrecos = false }
        )
    }
}

@Composable
private fun ResumoItem(titulo: String, valor: String, cor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, color = cor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(titulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RevistaAlunoDialog(
    linha: RevistaAlunoUi,
    precos: List<RevistaPreco>,
    onConfirmar: (tipo: String?, categoria: String, preco: Double) -> Unit,
    onCancelar: () -> Unit
) {
    val e = linha.entrega
    // 0 = sem revista, 1 = física, 2 = digital
    var modo by remember {
        mutableStateOf(when (e?.tipo) { "FISICA" -> 1; "DIGITAL" -> 2; else -> 0 })
    }
    val categorias = precos.map { it.categoria }
    val catInicial = e?.categoria?.takeIf { it.isNotBlank() }
        ?: linha.classeNome.takeIf { categorias.contains(it) }
        ?: categorias.firstOrNull() ?: ""
    var catIdx by remember { mutableStateOf(categorias.indexOf(catInicial).coerceAtLeast(0)) }

    val categoriaSel = categorias.getOrNull(catIdx) ?: ""
    val precoSel = precos.getOrNull(catIdx)?.preco ?: 0.0

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(linha.aluno.nome) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Tipo de revista", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(modo == 0, onClick = { modo = 0 }, label = { Text("Nenhuma") })
                    FilterChip(modo == 1, onClick = { modo = 1 }, label = { Text("Física") })
                    FilterChip(modo == 2, onClick = { modo = 2 }, label = { Text("Digital") })
                }
                if (modo == 1) {
                    if (categorias.isEmpty()) {
                        Text("Cadastre ao menos uma categoria de preço (botão Preços).",
                            color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    } else {
                        Dropdown("Categoria da revista", categorias, catIdx, { catIdx = it })
                        Text(
                            "Despesa: ${formatarMoeda(precoSel)}",
                            color = Vermelho, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Lançada em Finanças como: Revista - $categoriaSel - ${linha.aluno.nome}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (modo == 2) {
                    Text("Revista digital (PDF) — gratuita. Nenhuma despesa será lançada.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Sem revista. Qualquer despesa anterior deste aluno neste trimestre será removida.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = modo != 1 || categorias.isNotEmpty(),
                onClick = {
                    when (modo) {
                        1 -> onConfirmar("FISICA", categoriaSel, precoSel)
                        2 -> onConfirmar("DIGITAL", categoriaSel, 0.0)
                        else -> onConfirmar(null, "", 0.0)
                    }
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun PrecosDialog(
    precos: List<RevistaPreco>,
    onSalvar: (RevistaPreco) -> Unit,
    onExcluir: (RevistaPreco) -> Unit,
    onFechar: () -> Unit
) {
    var editando by remember { mutableStateOf<RevistaPreco?>(null) }
    var criando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Preços das revistas") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Toque para editar o valor de cada categoria.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                precos.forEach { pr ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editando = pr }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pr.categoria, Modifier.weight(1f))
                        Text(formatarMoeda(pr.preco), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { onExcluir(pr) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Delete, "Excluir",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { criando = true }) { Text("+ Nova categoria") }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )

    val alvo = editando
    if (alvo != null || criando) {
        PrecoEditDialog(
            inicial = alvo,
            onConfirmar = { onSalvar(it); editando = null; criando = false },
            onCancelar = { editando = null; criando = false }
        )
    }
}

@Composable
private fun PrecoEditDialog(
    inicial: RevistaPreco?,
    onConfirmar: (RevistaPreco) -> Unit,
    onCancelar: () -> Unit
) {
    var categoria by remember { mutableStateOf(inicial?.categoria ?: "") }
    var valor by remember { mutableStateOf(inicial?.let { String.format("%.2f", it.preco).replace(".", ",") } ?: "") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (inicial == null) "Nova categoria" else "Editar preço") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(categoria, { categoria = it },
                    label = { Text("Categoria (ex.: Adultos)") }, singleLine = true,
                    enabled = inicial == null || true)
                OutlinedTextField(valor, { valor = it }, label = { Text("Preço (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = {
            val v = valor.replace(",", ".").toDoubleOrNull() ?: 0.0
            TextButton(
                enabled = categoria.isNotBlank() && v >= 0,
                onClick = {
                    onConfirmar(
                        (inicial ?: RevistaPreco(categoria = "")).copy(
                            categoria = categoria.trim(), preco = v
                        )
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
