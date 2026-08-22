package com.ebd.controle.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.Aluno
import com.ebd.controle.data.formatarData
import com.ebd.controle.ui.AlunosViewModel
import com.ebd.controle.ui.components.DateField
import com.ebd.controle.ui.components.Dropdown

private val CARGOS = listOf("Membro", "Professor", "Líder", "Visitante")

@Composable
fun MembrosScreen() {
    val vm: AlunosViewModel = viewModel()
    val classes by vm.classes.collectAsStateWithLifecycle()
    val alunos by vm.alunos.collectAsStateWithLifecycle()
    val trimestre by vm.trimestre.collectAsStateWithLifecycle()
    val revistas by vm.revistas.collectAsStateWithLifecycle()

    var filtroIdx by remember { mutableStateOf(0) } // 0 = todas
    var mostrarForm by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Aluno?>(null) }

    val opcoesFiltro = listOf("Todas as classes") + classes.map { it.nome }
    val classeFiltroId = if (filtroIdx == 0) null else classes.getOrNull(filtroIdx - 1)?.id
    val lista = alunos.filter { classeFiltroId == null || it.classeId == classeFiltroId }
    val nomeClasse = classes.associate { it.id to it.nome }

    Scaffold(
        floatingActionButton = {
            if (classes.isNotEmpty()) {
                FloatingActionButton(onClick = { editando = null; mostrarForm = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Novo membro")
                }
            }
        }
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(horizontal = 12.dp)) {
            if (classes.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Crie uma classe primeiro (aba Início → Classes).")
                return@Column
            }
            Spacer(Modifier.height(8.dp))
            Dropdown("Filtrar por classe", opcoesFiltro, filtroIdx, { filtroIdx = it })
            Spacer(Modifier.height(8.dp))
            SeletorTrimestreRevista(
                rotulo = "${trimestre.numero}º trimestre de ${trimestre.ano}",
                comRevista = lista.count { revistas[it.id]?.temRevista == true },
                pagos = lista.count { revistas[it.id]?.pago == true },
                total = lista.size,
                onAnterior = { vm.trimestreAnterior() },
                onProximo = { vm.trimestreProximo() }
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(lista) { a ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .clickable { editando = a; mostrarForm = true }
                    ) {
                        ListItem(
                            headlineContent = { Text(a.nome) },
                            supportingContent = {
                                val nasc = if (a.dataNascimento != null) "🎂 ${formatarData(a.dataNascimento)}" else ""
                                Text(listOf(nomeClasse[a.classeId] ?: "", a.cargo, nasc)
                                    .filter { it.isNotBlank() }.joinToString(" • "))
                            },
                            trailingContent = {
                                val r = revistas[a.id]
                                val tem = r?.temRevista == true
                                val pago = r?.pago == true
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilterChip(
                                        selected = tem,
                                        onClick = { vm.marcarRevista(a.id, !tem, pago && tem) },
                                        label = { Text("Revista") }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    FilterChip(
                                        selected = pago,
                                        onClick = { vm.marcarRevista(a.id, tem, !pago) },
                                        label = { Text("Pago") }
                                    )
                                    IconButton(onClick = { vm.deletar(a) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // `classes` vem de um Flow vivo: uma exclusão remota pode esvaziá-la com o
    // diálogo aberto, e aí `first()` estoura.
    if (mostrarForm && classes.isNotEmpty()) {
        AlunoDialog(
            inicial = editando,
            classesNomes = classes.map { it.nome },
            classeIdInicial = editando?.classeId ?: classeFiltroId ?: classes.first().id,
            classeIds = classes.map { it.id },
            onConfirmar = { vm.salvar(it); mostrarForm = false },
            onCancelar = { mostrarForm = false }
        )
    }
}

@Composable
private fun AlunoDialog(
    inicial: Aluno?,
    classesNomes: List<String>,
    classeIdInicial: Long,
    classeIds: List<Long>,
    onConfirmar: (Aluno) -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var classeIdx by remember { mutableStateOf(classeIds.indexOf(inicial?.classeId ?: classeIdInicial).coerceAtLeast(0)) }
    var nasc by remember { mutableStateOf(inicial?.dataNascimento) }
    var tel by remember { mutableStateOf(inicial?.telefone ?: "") }
    var cargoIdx by remember { mutableStateOf(CARGOS.indexOf(inicial?.cargo ?: "Membro").coerceAtLeast(0)) }
    var ativo by remember { mutableStateOf(inicial?.ativo ?: true) }
    var especial by remember { mutableStateOf(inicial?.especial ?: false) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (inicial == null) "Novo membro" else "Editar membro") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, singleLine = true)
                Dropdown("Classe", classesNomes, classeIdx, { classeIdx = it })
                DateField("Data de nascimento", nasc, onPick = { nasc = it })
                Dropdown("Cargo", CARGOS, cargoIdx, { cargoIdx = it })
                OutlinedTextField(tel, { tel = it }, label = { Text("Telefone (opcional)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = ativo,
                        onCheckedChange = { ativo = it },
                        colors = com.ebd.controle.ui.components.realceSwitchColors()
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (ativo) "Ativo" else "Inativo")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = especial,
                        onCheckedChange = { especial = it },
                        colors = com.ebd.controle.ui.components.realceSwitchColors()
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Aluno especial (inclusão)")
                        Text("Usa metas adaptadas na tela de Pontuação",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    onConfirmar(
                        (inicial ?: Aluno(classeId = 0, nome = "")).copy(
                            nome = nome.trim(),
                            classeId = classeIds.getOrNull(classeIdx)
                                ?: classeIds.firstOrNull() ?: return@TextButton,
                            dataNascimento = nasc,
                            telefone = tel.trim(),
                            cargo = CARGOS[cargoIdx],
                            ativo = ativo,
                            especial = especial
                        )
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/**
 * Cabeçalho do controle de revistas: escolhe o trimestre e mostra o placar da classe
 * filtrada. Substitui a tela de Revistas, que existia para alimentar o financeiro.
 */
@Composable
private fun SeletorTrimestreRevista(
    rotulo: String,
    comRevista: Int,
    pagos: Int,
    total: Int,
    onAnterior: () -> Unit,
    onProximo: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAnterior) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Trimestre anterior")
            }
            Column(Modifier.weight(1f)) {
                Text("Revistas — $rotulo", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "$comRevista de $total com revista • $pagos pagas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onProximo) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Trimestre seguinte")
            }
        }
    }
}
