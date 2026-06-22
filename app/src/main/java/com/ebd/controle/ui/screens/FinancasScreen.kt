package com.ebd.controle.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.Financeiro
import com.ebd.controle.data.formatarData
import com.ebd.controle.data.formatarMoeda
import com.ebd.controle.data.hojeMillis
import com.ebd.controle.data.toLocalDate
import com.ebd.controle.ui.FinancasViewModel
import com.ebd.controle.ui.components.BarChart
import com.ebd.controle.ui.components.DateField
import com.ebd.controle.ui.components.StatCard
import com.ebd.controle.ui.theme.Verde
import com.ebd.controle.ui.theme.Vermelho
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun FinancasScreen() {
    val vm: FinancasViewModel = viewModel()
    val lancamentos by vm.lancamentos.collectAsState()
    val resumo by vm.resumoMes.collectAsState()
    var mostrarForm by remember { mutableStateOf(false) }

    // gráfico: saldo dos últimos 6 meses
    val fmtMes = DateTimeFormatter.ofPattern("MM/yy")
    val porMes = lancamentos.groupBy { YearMonth.from(it.data.toLocalDate()) }
    val ultimos = (0..5).map { YearMonth.now().minusMonths((5 - it).toLong()) }
    val chart = ultimos.map { ym ->
        val l = porMes[ym] ?: emptyList()
        val saldo = l.filter { it.tipo == "ENTRADA" }.sumOf { it.valor } -
            l.filter { it.tipo == "SAIDA" }.sumOf { it.valor }
        ym.format(fmtMes) to saldo.toFloat().coerceAtLeast(0f)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Novo lançamento")
            }
        }
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Entradas (mês)", formatarMoeda(resumo.entradas), accent = Verde, modifier = Modifier.weight(1f))
                    StatCard("Saídas (mês)", formatarMoeda(resumo.saidas), accent = Vermelho, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                StatCard(
                    "Balanço mensal", 
                    formatarMoeda(resumo.saldo),
                    accent = if (resumo.saldo >= 0) Verde else Vermelho,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Text("Fluxo dos últimos 6 meses", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) { 
                    Box(Modifier.padding(16.dp)) { BarChart(chart, accent = Verde) } 
                }
                Spacer(Modifier.height(24.dp))
                Text("Histórico de Lançamentos", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            items(lancamentos) { f ->
                val entrada = f.tipo == "ENTRADA"
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(f.categoria, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { 
                            Text("${formatarData(f.data)}${if (f.descricao.isNotBlank()) " • " + f.descricao else ""}") 
                        },
                        trailingContent = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    (if (entrada) "+ " else "- ") + formatarMoeda(f.valor),
                                    color = if (entrada) Verde else Vermelho, 
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { vm.deletar(f) }, modifier = Modifier.size(32.dp)) { 
                                    Icon(Icons.Filled.Delete, "Excluir", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) 
                                }
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (mostrarForm) {
        FinanceiroDialog(
            onConfirmar = { vm.salvar(it); mostrarForm = false },
            onCancelar = { mostrarForm = false }
        )
    }
}

@Composable
private fun FinanceiroDialog(onConfirmar: (Financeiro) -> Unit, onCancelar: () -> Unit) {
    var entrada by remember { mutableStateOf(true) }
    var categoria by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(hojeMillis()) }
    var descricao by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Novo lançamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(entrada, onClick = { entrada = true }, label = { Text("Entrada") })
                    FilterChip(!entrada, onClick = { entrada = false }, label = { Text("Saída") })
                }
                OutlinedTextField(categoria, { categoria = it },
                    label = { Text("Categoria (ex.: Oferta, Material, Energia)") }, singleLine = true)
                OutlinedTextField(valor, { valor = it }, label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                DateField("Data", data, onPick = { data = it })
                OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição (opcional)") })
            }
        },
        confirmButton = {
            val v = valor.replace(",", ".").toDoubleOrNull() ?: 0.0
            TextButton(
                enabled = categoria.isNotBlank() && v > 0,
                onClick = {
                    onConfirmar(Financeiro(
                        data = data, tipo = if (entrada) "ENTRADA" else "SAIDA",
                        categoria = categoria.trim(), valor = v, descricao = descricao.trim()
                    ))
                }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
