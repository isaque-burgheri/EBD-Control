package com.ebd.controle.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ebd.controle.ui.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    // Usamos o ComponentActivity como dono do ViewModel para ser o MESMO do MainActivity
    val activity = LocalContext.current as ComponentActivity
    val settingsVm: SettingsViewModel = viewModel(activity)
    val isDark by settingsVm.isDarkMode.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Configurações",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Seção Aparência
        SettingsSection(title = "Aparência", icon = Icons.Filled.Palette) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Tema Escuro", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Alternar entre cores claras e escuras",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isDark,
                    onCheckedChange = { settingsVm.setDarkMode(it) },
                    colors = com.ebd.controle.ui.components.realceSwitchColors()
                )
            }
        }

        // Seção Dados
        SettingsSection(title = "Nuvem e Backup", icon = Icons.Filled.Storage) {
            val syncUrl by settingsVm.syncUrl.collectAsState()
            val isSyncing by settingsVm.isSyncing.collectAsState()
            val autoSync by settingsVm.autoSync.collectAsState()
            val lastSync by settingsVm.lastSync.collectAsState()
            val scope = rememberCoroutineScope()
            val snackbar = remember { SnackbarHostState() }
            var confirmarBaixar by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = syncUrl,
                onValueChange = { settingsVm.setSyncUrl(it) },
                label = { Text("URL do Google Apps Script") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://script.google.com/macros/s/...") }
            )

            Spacer(Modifier.height(12.dp))

            // Sincronização automática (mão dupla): ao abrir o app, a cada 15 min
            // e logo após cada alteração. O botão abaixo é só um "forçar agora".
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sincronização automática", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Atualiza sozinho ao abrir, a cada 15 min e após cada alteração.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = autoSync,
                    onCheckedChange = { settingsVm.setAutoSync(it) },
                    colors = com.ebd.controle.ui.components.realceSwitchColors()
                )
            }

            Text(
                text = "Última sincronização: " + formatarUltimoSync(lastSync),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    settingsVm.sincronizar { _, msg ->
                        scope.launch { snackbar.showSnackbar(msg) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing && syncUrl.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Sincronizando...")
                } else {
                    Text("Sincronizar agora")
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { confirmarBaixar = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing && syncUrl.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Baixar da nuvem (substituir tudo)") }

            Text(
                "Use só num celular novo: apaga o que está neste aparelho e baixa os dados da planilha.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (confirmarBaixar) {
                AlertDialog(
                    onDismissRequest = { confirmarBaixar = false },
                    title = { Text("Baixar da nuvem?") },
                    text = { Text("Isto APAGA os dados deste celular e coloca no lugar o que está na planilha. Continuar?") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmarBaixar = false
                            settingsVm.baixarDaNuvem { _, msg -> scope.launch { snackbar.showSnackbar(msg) } }
                        }) { Text("Sim, baixar") }
                    },
                    dismissButton = { TextButton(onClick = { confirmarBaixar = false }) { Text("Cancelar") } }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

            SettingsItem(
                title = "Gerenciar Backup Local",
                subtitle = "Exportar ou restaurar arquivo .json",
                onClick = { nav.navigate("backup") }
            )
            
            SnackbarHost(hostState = snackbar)
        }

        // Seção Zona de perigo (resetar app)
        SecaoZonaDePerigo(settingsVm)

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Versão 1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecaoZonaDePerigo(settingsVm: SettingsViewModel) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var etapa by remember { mutableStateOf(0) } // 0=nada, 1=primeira confirmação, 2=confirmação final

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Zona de perigo",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))

            Text(
                "Apaga todos os dados deste celular (classes, membros, chamadas, " +
                    "finanças e visitantes) e começa do zero. A URL da planilha e o " +
                    "tema escolhido são mantidos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { etapa = 1 },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("Resetar o app (apagar tudo)")
            }

            SnackbarHost(hostState = snackbar)
        }
    }

    // 1ª confirmação
    if (etapa == 1) {
        AlertDialog(
            onDismissRequest = { etapa = 0 },
            icon = { Icon(Icons.Filled.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Apagar todos os dados?") },
            text = {
                Text(
                    "Isto vai apagar TODOS os dados deste celular e não tem como desfazer.\n\n" +
                        "Se quiser guardar uma cópia antes, cancele e use Backup → Exportar, " +
                        "ou sincronize com a planilha."
                )
            },
            confirmButton = {
                TextButton(onClick = { etapa = 2 }) {
                    Text("Continuar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { etapa = 0 }) { Text("Cancelar") } }
        )
    }

    // 2ª confirmação (final)
    if (etapa == 2) {
        AlertDialog(
            onDismissRequest = { etapa = 0 },
            icon = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Tem certeza absoluta?") },
            text = { Text("Última confirmação. Os dados serão apagados imediatamente.") },
            confirmButton = {
                TextButton(onClick = {
                    etapa = 0
                    settingsVm.resetarApp { _, msg ->
                        scope.launch { snackbar.showSnackbar(msg) }
                    }
                }) {
                    Text("Apagar tudo agora", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { etapa = 0 }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Texto amigável do horário da última sincronização. */
private fun formatarUltimoSync(millis: Long): String {
    if (millis <= 0L) return "ainda não sincronizado"
    val agora = System.currentTimeMillis()
    val diff = agora - millis
    return when {
        diff < 60_000L -> "agora mesmo"
        diff < 3_600_000L -> "há ${diff / 60_000L} min"
        else -> java.text.SimpleDateFormat("dd/MM 'às' HH:mm", java.util.Locale("pt", "BR"))
            .format(java.util.Date(millis))
    }
}
