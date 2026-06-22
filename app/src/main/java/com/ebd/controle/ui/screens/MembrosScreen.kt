package com.ebd.controle.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val classes by vm.classes.collectAsState()
    val alunos by vm.alunos.collectAsState()

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
                                IconButton(onClick = { vm.deletar(a) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (mostrarForm) {
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    onConfirmar(
                        (inicial ?: Aluno(classeId = 0, nome = "")).copy(
                            nome = nome.trim(),
                            classeId = classeIds[classeIdx],
                            dataNascimento = nasc,
                            telefone = tel.trim(),
                            cargo = CARGOS[cargoIdx],
                            ativo = ativo
                        )
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
