package com.ebd.controle.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ebd.controle.data.formatarData
import com.ebd.controle.data.formatarMoeda
import com.ebd.controle.ui.DashboardViewModel
import com.ebd.controle.ui.components.Kicker
import com.ebd.controle.ui.components.StatCard
import com.ebd.controle.ui.theme.Azul
import com.ebd.controle.ui.theme.Verde
import com.ebd.controle.ui.theme.Vermelho
import java.util.Calendar

@Composable
fun DashboardScreen(nav: NavController) {
    val vm: DashboardViewModel = viewModel()
    val s by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
    ) {
        // ----- Cabeçalho editorial: kicker + manchete Playfair -----
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                Kicker("Escola Bíblica Dominical")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Sua ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Escola") }
                        append(" Bíblica")
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    saudacao(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ----- Cards de números -----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Classes", s.totalClasses.toString(), accent = Azul, modifier = Modifier.weight(1f))
                StatCard("Membros", s.totalAlunos.toString(), accent = Azul, modifier = Modifier.weight(1f))
            }
        }
        item {
            // Saldo do mês em destaque, ocupando a largura toda
            StatCard(
                "Saldo do Mês", formatarMoeda(s.saldoMes),
                subtitle = if (s.saldoMes >= 0) "No azul este mês" else "No vermelho este mês",
                accent = if (s.saldoMes >= 0) Verde else Vermelho,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ----- Destaque de visitantes -----
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { nav.navigate("visitantes") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PersonAdd, null, tint = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Novos Visitantes", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (s.visitantesPendentes == 0) "Nenhum contato pendente"
                            else "${s.visitantesPendentes} aguardando acompanhamento",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ----- Aniversariantes -----
        item { SecaoTitulo("Aniversariantes", "próximos 30 dias") }
        if (s.aniversariantes.isEmpty()) {
            item {
                TextoVazio("Nenhum aniversário próximo. Cadastre a data de nascimento em Membros.")
            }
        } else {
            items(s.aniversariantes.take(5)) { a ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text(a.nome, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            Text(
                                "${a.classe} • ${a.diaSemana}, ${formatarData(a.dataNascimento)} • fará ${a.idadeQueFara}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Cake, null, tint = MaterialTheme.colorScheme.tertiary) }
                        },
                        trailingContent = {
                            Text(a.quando, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    )
                }
            }
        }

        // ----- Atalhos -----
        item { SecaoTitulo("Atalhos") }
        item { AtalhoCard("Relatórios e gráficos", Icons.Filled.BarChart) { nav.navigate("relatorios") } }
        item { AtalhoCard("Classes e professores", Icons.Filled.School) { nav.navigate("classes") } }
        item { AtalhoCard("Ver todos os aniversariantes", Icons.Filled.Cake) { nav.navigate("aniversarios") } }
        item { AtalhoCard("Backup e dados (exportar/importar)", Icons.Filled.CloudSync) { nav.navigate("backup") } }
    }
}

@Composable
private fun SecaoTitulo(titulo: String, sufixo: String = "") {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        if (sufixo.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                sufixo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

@Composable
private fun TextoVazio(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AtalhoCard(titulo: String, icone: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(icone, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
            Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun saudacao(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Bom dia 🌅"
        h < 18 -> "Boa tarde ☀️"
        else -> "Boa noite 🌙"
    }
}
