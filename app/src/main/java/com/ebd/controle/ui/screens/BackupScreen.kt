package com.ebd.controle.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ebd.controle.EBDApp
import com.ebd.controle.data.exportarBackup
import com.ebd.controle.data.importarBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen() {
    val ctx = LocalContext.current
    val repo = remember { (ctx.applicationContext as EBDApp).repository }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmar by remember { mutableStateOf(false) }

    val exportar = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = withContext(Dispatchers.IO) { exportarBackup(repo) }
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                snackbar.showSnackbar("Backup exportado com sucesso.")
            } catch (e: Exception) {
                snackbar.showSnackbar("Erro ao exportar: ${e.message}")
            }
        }
    }

    val importar = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (json != null) {
                    withContext(Dispatchers.IO) { importarBackup(repo, json) }
                    snackbar.showSnackbar("Backup restaurado.")
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Erro ao restaurar: ${e.message}")
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Como funcionam os dados", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tudo fica salvo no próprio celular (funciona sem internet). " +
                        "O backup gera um arquivo com TODOS os dados (classes, membros, chamadas, " +
                        "visitantes e finanças). Use-o para guardar uma cópia de segurança ou para " +
                        "passar os dados a outro celular.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button(onClick = { exportar.launch("ebd-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Exportar backup (.json)")
            }
            OutlinedButton(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restaurar backup (.json)")
            }

            Text(
                "A restauração SUBSTITUI todos os dados atuais pelos do arquivo. " +
                "Faça um backup antes, se tiver dados que ainda não salvou.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Restaurar backup?") },
            text = {
                Text("Isto vai APAGAR todos os dados que estão hoje no aparelho e colocar no lugar " +
                    "os dados do arquivo escolhido. Deseja continuar?")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    importar.launch(arrayOf("application/json"))
                }) { Text("Sim, restaurar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}
