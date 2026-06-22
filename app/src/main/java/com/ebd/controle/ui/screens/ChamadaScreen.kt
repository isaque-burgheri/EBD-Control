package com.ebd.controle.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.ebd.controle.data.hojeMillis
import com.ebd.controle.ui.ChamadaViewModel
import com.ebd.controle.ui.components.DateField
import com.ebd.controle.ui.components.Dropdown
import com.ebd.controle.ui.theme.Azul
import kotlinx.coroutines.launch

private data class Marca(val presente: Boolean, val biblia: Boolean, val revista: Boolean)

@Composable
fun ChamadaScreen() {
    val vm: ChamadaViewModel = viewModel()
    val classes by vm.classes.collectAsState()
    val alunos by vm.alunos.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var classeIdx by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf(hojeMillis()) }
    var licao by remember { mutableStateOf("") }
    var oferta by remember { mutableStateOf("") }
    val marcas = remember { mutableStateMapOf<Long, Marca>() }

    // visitantes adicionados nesta chamada (nome, telefone)
    val visitantesNovos = remember { mutableStateListOf<Pair<String, String>>() }
    var visNome by remember { mutableStateOf("") }
    var visTel by remember { mutableStateOf("") }

    val classeId = classes.getOrNull(classeIdx)?.id

    LaunchedEffect(classeId) { classeId?.let { vm.carregarAlunos(it) } }
    LaunchedEffect(alunos) {
        marcas.clear()
        alunos.forEach { marcas[it.id] = Marca(false, false, false) }
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
                Dropdown("Classe", classes.map { it.nome }, classeIdx, { classeIdx = it })
                Spacer(Modifier.height(8.dp))
                DateField("Data da aula", data, onPick = { data = it })
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

            items(alunos) { a ->
                val m = marcas[a.id] ?: Marca(false, false, false)
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(a.nome, Modifier.weight(1f))
                        FilterChip(m.presente, onClick = { marcas[a.id] = m.copy(presente = !m.presente) }, label = { Text("P") })
                        Spacer(Modifier.width(4.dp))
                        FilterChip(m.biblia, onClick = { marcas[a.id] = m.copy(biblia = !m.biblia) }, label = { Text("B") })
                        Spacer(Modifier.width(4.dp))
                        FilterChip(m.revista, onClick = { marcas[a.id] = m.copy(revista = !m.revista) }, label = { Text("R") })
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val cid = classeId ?: return@Button
                        val chamada = Chamada(
                            classeId = cid, data = data,
                            licao = licao.toIntOrNull() ?: 0,
                            oferta = oferta.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            visitantes = visitantesNovos.size
                        )
                        val presencas = alunos.map {
                            val mk = marcas[it.id] ?: Marca(false, false, false)
                            Presenca(chamadaId = 0, alunoId = it.id,
                                presente = mk.presente, biblia = mk.biblia, revista = mk.revista)
                        }
                        val visitantes = visitantesNovos.map {
                            Visitante(nome = it.first, telefone = it.second, data = data, classeId = cid)
                        }
                        vm.salvar(chamada, presencas, visitantes) {
                            scope.launch { snackbar.showSnackbar("Chamada salva!") }
                            licao = ""; oferta = ""
                            visitantesNovos.clear()
                            alunos.forEach { marcas[it.id] = Marca(false, false, false) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Finalizar Chamada", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
