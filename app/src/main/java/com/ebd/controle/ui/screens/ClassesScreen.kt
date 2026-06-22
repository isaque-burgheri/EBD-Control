package com.ebd.controle.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.Classe
import com.ebd.controle.ui.ClassesViewModel

@Composable
fun ClassesScreen() {
    val vm: ClassesViewModel = viewModel()
    val classes by vm.classes.collectAsState()
    var editando by remember { mutableStateOf<Classe?>(null) }
    var mostrarForm by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editando = null; mostrarForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nova classe")
            }
        }
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal = 12.dp)) {
            items(classes) { c ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .clickable { editando = c; mostrarForm = true }
                ) {
                    ListItem(
                        headlineContent = { Text(c.nome) },
                        supportingContent = {
                            Text(listOf(c.faixaEtaria, c.professores).filter { it.isNotBlank() }.joinToString(" • "))
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.deletar(c) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                            }
                        }
                    )
                }
            }
        }
    }

    if (mostrarForm) {
        ClasseDialog(
            inicial = editando,
            onConfirmar = { vm.salvar(it); mostrarForm = false },
            onCancelar = { mostrarForm = false }
        )
    }
}

@Composable
private fun ClasseDialog(inicial: Classe?, onConfirmar: (Classe) -> Unit, onCancelar: () -> Unit) {
    var nome by remember { mutableStateOf(inicial?.nome ?: "") }
    var faixa by remember { mutableStateOf(inicial?.faixaEtaria ?: "") }
    var profs by remember { mutableStateOf(inicial?.professores ?: "") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (inicial == null) "Nova classe" else "Editar classe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome da classe") }, singleLine = true)
                OutlinedTextField(faixa, { faixa = it }, label = { Text("Faixa etária") }, singleLine = true)
                OutlinedTextField(profs, { profs = it }, label = { Text("Professor(es)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    onConfirmar(
                        (inicial ?: Classe(nome = "")).copy(
                            nome = nome.trim(), faixaEtaria = faixa.trim(), professores = profs.trim()
                        )
                    )
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
