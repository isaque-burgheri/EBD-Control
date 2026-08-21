package com.ebd.controle.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.Chamada
import com.ebd.controle.data.Presenca
import com.ebd.controle.data.Visitante
import com.ebd.controle.data.formatarData
import com.ebd.controle.data.hojeMillis
import com.ebd.controle.ui.ChamadaViewModel
import com.ebd.controle.ui.components.DateField
import com.ebd.controle.ui.components.Dropdown
import com.ebd.controle.ui.theme.Azul
import kotlinx.coroutines.launch

private data class Marca(val presente: Boolean, val biblia: Boolean, val revista: Boolean)

/*
 * Savers para o estado do formulário sobreviver à recriação da Activity (rotação,
 * mudança de tema do sistema, morte de processo em segundo plano). Sem eles, girar o
 * celular no meio de uma chamada de 25 alunos apagava tudo — e o AndroidManifest não
 * declara `configChanges`, então a recriação acontece mesmo.
 *
 * Tudo é serializado como String porque é o que o Bundle guarda sem cerimônia.
 */
private fun bit(b: Boolean) = if (b) '1' else '0'

private val marcasSaver = listSaver<SnapshotStateMap<Long, Marca>, String>(
    save = { mapa -> mapa.map { (id, m) -> "$id|${bit(m.presente)}${bit(m.biblia)}${bit(m.revista)}" } },
    restore = { itens ->
        mutableStateMapOf<Long, Marca>().apply {
            itens.forEach { linha ->
                val id = linha.substringBefore('|').toLongOrNull() ?: return@forEach
                val f = linha.substringAfter('|')
                if (f.length == 3) put(id, Marca(f[0] == '1', f[1] == '1', f[2] == '1'))
            }
        }
    }
)

/**
 * Nome e telefone viram duas entradas seguidas da lista, em vez de um campo só com
 * separador: qualquer caractere escolhido como separador pode aparecer num nome ou
 * num telefone, e aí o par volta partido.
 */
private val visitantesSaver = listSaver<SnapshotStateList<Pair<String, String>>, String>(
    save = { lista -> lista.flatMap { listOf(it.first, it.second) } },
    restore = { itens ->
        mutableStateListOf<Pair<String, String>>().apply {
            itens.chunked(2).forEach { par ->
                if (par.size == 2) add(par[0] to par[1])
            }
        }
    }
)

private val tocadosSaver = listSaver<SnapshotStateList<Long>, Long>(
    save = { it.toList() },
    restore = { mutableStateListOf<Long>().apply { addAll(it) } }
)

@Composable
fun ChamadaScreen() {
    val vm: ChamadaViewModel = viewModel()
    val classes by vm.classes.collectAsState()
    val alunos by vm.alunos.collectAsState()
    val chamadaExistente by vm.chamadaExistente.collectAsState()
    val presencasExistentes by vm.presencasExistentes.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // A classe é guardada pelo id, não pela posição na lista: `classes` vem de um Flow
    // vivo e reordena por nome quando o sync traz uma classe nova. Com índice, a tela
    // trocava de classe sozinha no meio de uma chamada.
    var classeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var data by rememberSaveable { mutableStateOf(hojeMillis()) }
    var licao by rememberSaveable { mutableStateOf("") }
    var oferta by rememberSaveable { mutableStateOf("") }
    val marcas = rememberSaveable(saver = marcasSaver) { mutableStateMapOf<Long, Marca>() }
    var confirmarExcluir by rememberSaveable { mutableStateOf(false) }

    // Alunos cujo chip o usuário tocou nesta sessão de edição. O que ele marcou tem
    // precedência sobre o que chega do banco — ver o efeito de reconciliação abaixo.
    val tocados = rememberSaveable(saver = tocadosSaver) { mutableStateListOf<Long>() }

    // visitantes adicionados nesta chamada (nome, telefone)
    val visitantesNovos = rememberSaveable(saver = visitantesSaver) { mutableStateListOf<Pair<String, String>>() }
    var visNome by rememberSaveable { mutableStateOf("") }
    var visTel by rememberSaveable { mutableStateOf("") }

    val classeIdx = classes.indexOfFirst { it.id == classeId }.coerceAtLeast(0)
    val editando = chamadaExistente != null

    // Escolhe a primeira classe no começo, e se a classe atual for removida remotamente.
    LaunchedEffect(classes) {
        if (classeId == null || classes.none { it.id == classeId }) {
            classeId = classes.firstOrNull()?.id
        }
    }

    // Zerar o formulário é decisão de troca de contexto, não de recomposição. Guardar o
    // contexto já carregado evita que a restauração após rotação caia aqui e apague o
    // que o rememberSaveable acabou de devolver.
    var contextoCarregado by rememberSaveable { mutableStateOf<String?>(null) }
    val contextoAtual = "$classeId|$data"

    LaunchedEffect(contextoAtual) {
        if (contextoCarregado != contextoAtual) {
            marcas.clear()
            tocados.clear()
            contextoCarregado = contextoAtual
        }
        classeId?.let { vm.carregar(it, data) }
    }

    // Reconcilia com o banco preservando o que o usuário já marcou. A versão anterior
    // fazia `marcas.clear()` aqui: qualquer reemissão do Room — um sync a cada 15 min, ou
    // o outro professor salvando a mesma chamada — apagava as marcações em andamento.
    LaunchedEffect(alunos, presencasExistentes) {
        val porAluno = presencasExistentes.associateBy { it.alunoId }
        alunos.forEach { a ->
            if (a.id in tocados) return@forEach
            val p = porAluno[a.id]
            marcas[a.id] = if (p != null) Marca(p.presente, p.biblia, p.revista)
                           else Marca(false, false, false)
        }
        // Aluno que saiu da classe não deve continuar contando como presente.
        marcas.keys.retainAll(alunos.mapTo(HashSet()) { it.id })
    }

    // Pré-preenche lição e oferta a partir da chamada carregada
    LaunchedEffect(chamadaExistente) {
        val ch = chamadaExistente
        licao = if (ch != null && ch.licao > 0) ch.licao.toString() else ""
        oferta = if (ch != null && ch.oferta > 0.0) ofertaParaTexto(ch.oferta) else ""
    }

    val presentes = marcas.values.count { it.presente }
    val total = alunos.size

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { p ->
        if (classes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Crie uma classe e membros primeiro.") }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal = 12.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Dropdown("Classe", classes.map { it.nome }, classeIdx,
                    { idx -> classes.getOrNull(idx)?.let { classeId = it.id } })
                Spacer(Modifier.height(8.dp))
                DateField("Data da aula", data, onPick = { data = it })
                if (editando) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Editando a chamada de ${formatarData(data)} — ajuste as marcações e toque em Atualizar.",
                            Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(licao, { licao = it }, label = { Text("Lição") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(oferta, { oferta = it }, label = { Text("Oferta (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f))
                }

                // ---------- Visitantes ----------
                Spacer(Modifier.height(12.dp))
                Card(shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PersonAdd, null, tint = Azul)
                            Spacer(Modifier.width(8.dp))
                            Text("Visitantes de hoje", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(visNome, { visNome = it }, label = { Text("Nome") },
                                singleLine = true, modifier = Modifier.weight(1.2f))
                            OutlinedTextField(visTel, { visTel = it }, label = { Text("Telefone") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true, modifier = Modifier.weight(1f))
                            FilledIconButton(onClick = {
                                if (visNome.isNotBlank()) {
                                    visitantesNovos.add(visNome.trim() to visTel.trim())
                                    visNome = ""; visTel = ""
                                }
                            }) { Icon(Icons.Filled.Add, "Adicionar visitante") }
                        }
                        visitantesNovos.forEachIndexed { i, v ->
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("• ${v.first}${if (v.second.isNotBlank()) " — ${v.second}" else ""}",
                                    Modifier.weight(1f))
                                IconButton(onClick = { visitantesNovos.removeAt(i) }) {
                                    Icon(Icons.Filled.Close, "Remover")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Resumo de Presenças", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Toque nos indicadores: P (Presente), B (Trouxe Bíblia), R (Trouxe Revista)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }

            items(alunos, key = { it.id }) { a ->
                val m = marcas[a.id] ?: Marca(false, false, false)
                // Registrar o toque é o que protege a marcação de ser sobrescrita pela
                // próxima reemissão do banco.
                val marcar = { nova: Marca ->
                    marcas[a.id] = nova
                    if (a.id !in tocados) tocados.add(a.id)
                }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(a.nome, Modifier.weight(1f))
                        FilterChip(m.presente, onClick = { marcar(m.copy(presente = !m.presente)) }, label = { Text("P") })
                        Spacer(Modifier.width(4.dp))
                        FilterChip(m.biblia, onClick = { marcar(m.copy(biblia = !m.biblia)) }, label = { Text("B") })
                        Spacer(Modifier.width(4.dp))
                        FilterChip(m.revista, onClick = { marcar(m.copy(revista = !m.revista)) }, label = { Text("R") })
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val cid = classeId ?: return@Button
                        // Parte da chamada existente em vez de montar uma nova: o
                        // formulário não tem campo de dízimos, e um objeto novo traria
                        // 0.0 que o sync espalharia para os outros aparelhos.
                        val chamada = (chamadaExistente ?: Chamada(classeId = cid, data = data)).copy(
                            classeId = cid, data = data,
                            licao = licao.toIntOrNull() ?: 0,
                            oferta = oferta.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            visitantes = (chamadaExistente?.visitantes ?: 0) + visitantesNovos.size
                        )
                        val presencas = alunos.map {
                            val mk = marcas[it.id] ?: Marca(false, false, false)
                            Presenca(chamadaId = 0, alunoId = it.id,
                                presente = mk.presente, biblia = mk.biblia, revista = mk.revista)
                        }
                        val visitantes = visitantesNovos.map {
                            Visitante(nome = it.first, telefone = it.second, data = data, classeId = cid)
                        }
                        val eraEdicao = editando
                        vm.salvar(chamada, presencas, visitantes) {
                            scope.launch {
                                snackbar.showSnackbar(if (eraEdicao) "Chamada atualizada!" else "Chamada salva!")
                            }
                            visitantesNovos.clear()
                            // Salvo, o banco volta a ser a verdade: solta a precedência
                            // dos toques para o recarregamento abaixo valer.
                            tocados.clear()
                            vm.carregar(cid, data) // recarrega já com o que foi salvo
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp))
                    Text(if (editando) "Atualizar Chamada" else "Finalizar Chamada", fontWeight = FontWeight.Bold)
                }

                if (editando) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { confirmarExcluir = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null); Spacer(Modifier.width(8.dp))
                        Text("Excluir chamada")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (confirmarExcluir) {
            AlertDialog(
                onDismissRequest = { confirmarExcluir = false },
                icon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Excluir esta chamada?") },
                text = {
                    Text(
                        "A chamada de ${formatarData(data)} e as presenças registradas nela serão " +
                            "removidas (a oferta lançada também). Não dá pra desfazer."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmarExcluir = false
                        vm.excluirChamada {
                            scope.launch { snackbar.showSnackbar("Chamada excluída.") }
                        }
                    }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmarExcluir = false }) { Text("Cancelar") } }
            )
        }
    }
}

/** Mostra a oferta sem ".0" quando for valor inteiro. */
private fun ofertaParaTexto(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
