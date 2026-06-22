package com.ebd.controle.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.formatarData
import com.ebd.controle.ui.AniversarianteUi
import com.ebd.controle.ui.AniversariantesViewModel
import com.ebd.controle.ui.theme.Vermelho

@Composable
fun AniversariantesScreen() {
    val vm: AniversariantesViewModel = viewModel()
    val lista by vm.lista.collectAsState()

    if (lista.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nenhuma data de nascimento encontrada.\n\nVocê pode adicionar as datas editando os perfis na aba 'Membros'.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val hoje = lista.filter { it.diasAte == 0L }
    val semana = lista.filter { it.diasAte in 1..7 }
    val mes = lista.filter { it.diasAte in 8..31 }
    val depois = lista.filter { it.diasAte > 31 }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        secao("🎂 Hoje", hoje)
        secao("Esta semana", semana)
        secao("Este mês", mes)
        secao("Próximas datas", depois)
        item { Spacer(Modifier.height(32.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.secao(titulo: String, itens: List<AniversarianteUi>) {
    if (itens.isEmpty()) return
    item {
        Text(
            titulo,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
    items(itens) { a ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(a.nome, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("${a.classe} • ${a.diaSemana}, ${formatarData(a.dataNascimento)}") },
                leadingContent = { Icon(Icons.Filled.Cake, null, tint = Vermelho) },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (a.diasAte == 0L) "Hoje!" else "em ${a.diasAte} dias", 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold,
                            color = if (a.diasAte == 0L) Vermelho else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("faz ${a.idadeQueFara} anos", fontSize = 11.sp)
                    }
                }
            )
        }
    }
}
