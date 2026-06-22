package com.ebd.controle.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.Visitante
import com.ebd.controle.data.formatarData
import com.ebd.controle.data.hojeMillis
import com.ebd.controle.ui.VisitanteUi
import com.ebd.controle.ui.VisitantesViewModel
import com.ebd.controle.ui.components.DateField
import com.ebd.controle.ui.components.Dropdown
import com.ebd.controle.ui.theme.Azul
import com.ebd.controle.ui.theme.Verde

@Composable
fun VisitantesScreen() {
    val vm: VisitantesViewModel = viewModel()
    val lista by vm.lista.collectAsState()
    val classes by vm.classes.collectAsState()
    val ctx = LocalContext.current

    var mostrarAdd by remember { mutableStateOf(false) }
    var converter by remember { mutableStateOf<Visitante?>(null) }

    val pendentes = lista.filter { !it.visitante.convertido }
    val convertidos = lista.filter { it.visitante.convertido }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarAdd = true },
                icon = { Icon(Icons.Filled.PersonAddAlt, null) },
                text = { Text("Novo Visitante") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { p ->
        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(p).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhum visitante registrado ainda.\n\nVocê pode adicionar novos visitantes aqui ou durante o registro da chamada.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal = 16.dp)) {
            if (pendentes.isNotEmpty()) {
                item {
                    Text(
                        "Visitantes para acompanhar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(pendentes) { VisitanteCard(it, ctx, onConverter = { converter = it.visitante }, onExcluir = { vm.deletar(it.visitante) }) }
            }
            if (convertidos.isNotEmpty()) {
                item {
                    Text(
                        "Novos membros convertidos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }
                items(convertidos) { VisitanteCard(it, ctx, onConverter = null, onExcluir = { vm.deletar(it.visitante) }) }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (mostrarAdd) {
        VisitanteDialog(
            classesNomes = classes.map { it.nome },
            classeIds = classes.map { it.id },
            onConfirmar = { vm.salvar(it); mostrarAdd = false },
            onCancelar = { mostrarAdd = false }
        )
    }

    converter?.let { v ->
        if (classes.isEmpty()) { converter = null }
        else ConverterDialog(
            nome = v.nome,
            classesNomes = classes.map { it.nome },
            onConfirmar = { idx -> vm.converter(v, classes[idx].id); converter = null },
            onCancelar = { converter = null }
        )
    }
}

@Composable
private fun VisitanteCard(ui: VisitanteUi, ctx: android.content.Context, onConverter: (() -> Unit)?, onExcluir: () -> Unit) {
    val v = ui.visitante
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = if (v.convertido) 
            CardDefaults.cardColors(containerColor = Verde.copy(alpha = 0.05f))
        else 
            CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.nome, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (v.convertido) {
                    Surface(color = Verde.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                        Text("Membro", color = Verde, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(4.dp))
            val info = buildList {
                if (v.telefone.isNotBlank()) add(v.telefone)
                add("Visitou em ${formatarData(v.data)}")
                if (ui.classeNome.isNotBlank()) add(ui.classeNome)
            }.joinToString(" • ")
            Text(info, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (v.observacao.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(v.observacao, fontSize = 12.sp, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (v.telefone.isNotBlank()) {
                    FilledTonalButton(
                        onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${v.telefone}"))) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp)
                    ) { 
                        Icon(Icons.Filled.Call, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ligar", fontSize = 13.sp) 
                    }
                }
                if (onConverter != null) {
                    Button(
                        onClick = onConverter,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Efetivar matrícula", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onExcluir, modifier = Modifier.size(36.dp)) { 
                    Icon(Icons.Filled.Delete, "Excluir", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) 
                }
            }
        }
    }
}

@Composable
private fun VisitanteDialog(
    classesNomes: List<String>,
    classeIds: List<Long>,
    onConfirmar: (Visitante) -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var tel by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(hojeMillis()) }
    val opcoes = listOf("Nenhuma classe") + classesNomes
    var classeIdx by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Novo visitante") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(tel, { tel = it }, label = { Text("Telefone") }, singleLine = true)
                DateField("Data da visita", data, onPick = { data = it })
                Dropdown("Classe que visitou (opcional)", opcoes, classeIdx, { classeIdx = it })
                OutlinedTextField(obs, { obs = it }, label = { Text("Observação (opcional)") })
            }
        },
        confirmButton = {
            TextButton(enabled = nome.isNotBlank(), onClick = {
                val classeId = if (classeIdx == 0) null else classeIds[classeIdx - 1]
                onConfirmar(Visitante(nome = nome.trim(), telefone = tel.trim(), data = data,
                    classeId = classeId, observacao = obs.trim()))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun ConverterDialog(
    nome: String,
    classesNomes: List<String>,
    onConfirmar: (Int) -> Unit,
    onCancelar: () -> Unit
) {
    var idx by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Tornar membro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Em qual classe \"$nome\" será matriculado(a)?")
                Dropdown("Classe", classesNomes, idx, { idx = it })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirmar(idx) }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
