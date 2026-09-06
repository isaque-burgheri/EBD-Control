package com.ebd.controle.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebd.controle.data.FORMA_DINHEIRO
import com.ebd.controle.data.FORMA_PIX
import com.ebd.controle.data.formatarMoeda
import com.ebd.controle.data.hojeMillis
import com.ebd.controle.ui.ContribuicoesViewModel
import com.ebd.controle.ui.LinhaContribuicaoUi
import com.ebd.controle.ui.MesContribuicaoUi
import com.ebd.controle.ui.ResumoContribuicoesUi
import com.ebd.controle.ui.components.DateField
import com.ebd.controle.ui.components.Kicker
import com.ebd.controle.ui.theme.Azul
import com.ebd.controle.ui.theme.Verde

/** Valor combinado entre os professores. É só a sugestão do formulário — cada um dá o que quiser. */
private const val VALOR_SUGERIDO = "25"

private fun rotuloForma(forma: String) = if (forma == FORMA_PIX) "Pix" else "Dinheiro"

/**
 * Ajuda de custo mensal dos professores.
 *
 * A oferta de domingo raramente cobre o café, e a diferença saía do bolso de quem
 * coordena. Esta tela existe para dar transparência ao que os professores contribuem —
 * é voluntário, então ela mostra o que entrou, nunca o que "falta" alguém pagar.
 */
@Composable
fun ContribuicoesScreen() {
    val vm: ContribuicoesViewModel = viewModel()
    val trimestre by vm.trimestre.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()

    /** Célula aberta no formulário: quem e de que mês. */
    var editando by remember { mutableStateOf<Pair<LinhaContribuicaoUi, MesContribuicaoUi>?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.trimestreAnterior() }) {
                Icon(Icons.Filled.ChevronLeft, "Trimestre anterior")
            }
            Text(
                trimestre.rotulo, Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center
            )
            IconButton(onClick = { vm.trimestreProximo() }) {
                Icon(Icons.Filled.ChevronRight, "Próximo trimestre")
            }
        }
        Spacer(Modifier.height(4.dp))

        if (estado.linhas.isEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Nenhum professor marcado", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Abra Membros, toque na pessoa e ligue \"É professor\". " +
                            "Quem for marcado aparece aqui; quem for desmarcado sai, " +
                            "sem perder o que já contribuiu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            return@Column
        }

        LazyColumn {
            item {
                CartaoResumo(estado.resumo)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Toque num mês para registrar ou alterar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            items(estado.linhas, key = { it.aluno.id }) { linha ->
                CartaoProfessor(linha, onTocarMes = { mes -> editando = linha to mes })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val alvo = editando
    if (alvo != null) {
        ContribuicaoDialog(
            linha = alvo.first,
            mes = alvo.second,
            onConfirmar = { valor, forma, data, obs ->
                vm.registrar(alvo.first.aluno.id, alvo.second.mes, valor, forma, data, obs)
                editando = null
            },
            onRemover = {
                vm.remover(alvo.first.aluno.id, alvo.second.mes)
                editando = null
            },
            onCancelar = { editando = null }
        )
    }
}

/* ============================ RESUMO ============================ */
@Composable
private fun CartaoResumo(r: ResumoContribuicoesUi) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(formatarMoeda(r.total), fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Verde)
            Text(
                "${r.contribuiram} de ${r.professores} professores contribuíram neste trimestre",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(Modifier.height(10.dp))
            Kicker("Por mês")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.porMes.forEach { (rotulo, valor) ->
                    Column(Modifier.weight(1f)) {
                        Text(rotulo, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatarMoeda(valor), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Kicker("Por forma de pagamento")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Dinheiro", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatarMoeda(r.emDinheiro), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Pix", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatarMoeda(r.emPix), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

/* ============================ LINHA DO PROFESSOR ============================ */
@Composable
private fun CartaoProfessor(
    linha: LinhaContribuicaoUi,
    onTocarMes: (MesContribuicaoUi) -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(linha.aluno.nome, fontWeight = FontWeight.SemiBold)
                    if (linha.classeNome.isNotBlank()) {
                        Text(linha.classeNome, fontSize = 11.sp, color = Azul)
                    }
                }
                Surface(
                    color = if (linha.total > 0) Verde.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        formatarMoeda(linha.total),
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        color = if (linha.total > 0) Verde else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                linha.meses.forEach { m ->
                    CelulaMes(m, Modifier.weight(1f)) { onTocarMes(m) }
                }
            }
        }
    }
}

/**
 * Um mês. Sem valor, mostra um traço em vez de "R$ 0,00" — a contribuição é
 * voluntária, e zerar o mês de alguém na tela soaria como cobrança.
 */
@Composable
private fun CelulaMes(m: MesContribuicaoUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ativo = m.registrado
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (ativo) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                m.rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (ativo) formatarMoeda(m.valor) else "—",
                fontSize = 13.sp,
                fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Normal
            )
            if (ativo) {
                Text(
                    rotuloForma(m.contribuicao?.forma ?: FORMA_DINHEIRO),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ============================ FORMULÁRIO ============================ */
@Composable
private fun ContribuicaoDialog(
    linha: LinhaContribuicaoUi,
    mes: MesContribuicaoUi,
    onConfirmar: (valor: Double, forma: String, data: Long, observacao: String) -> Unit,
    onRemover: () -> Unit,
    onCancelar: () -> Unit
) {
    val existente = mes.contribuicao
    var valor by remember {
        mutableStateOf(existente?.let { valorParaTexto(it.valor) } ?: VALOR_SUGERIDO)
    }
    var forma by remember { mutableStateOf(existente?.forma ?: FORMA_DINHEIRO) }
    var data by remember { mutableStateOf(existente?.data?.takeIf { it > 0 } ?: hojeMillis()) }
    var observacao by remember { mutableStateOf(existente?.observacao ?: "") }

    // Vírgula é o separador do teclado decimal em pt-BR; o campo aceita as duas.
    val valorNumero = valor.replace(",", ".").toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("${linha.aluno.nome} — ${mes.rotulo}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    valor, { valor = it },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Column {
                    Kicker("Forma de pagamento")
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = forma == FORMA_DINHEIRO,
                            onClick = { forma = FORMA_DINHEIRO },
                            label = { Text("Dinheiro") }
                        )
                        FilterChip(
                            selected = forma == FORMA_PIX,
                            onClick = { forma = FORMA_PIX },
                            label = { Text("Pix") }
                        )
                    }
                }
                DateField("Data da entrega", data, onPick = { data = it })
                OutlinedTextField(
                    observacao, { observacao = it },
                    label = { Text("Observação (opcional)") },
                    singleLine = true
                )
                if (existente != null) {
                    TextButton(onClick = onRemover) {
                        Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Remover este lançamento", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valorNumero > 0.0,
                onClick = { onConfirmar(valorNumero, forma, data, observacao.trim()) }
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/** Mostra o valor sem ".0" quando for inteiro, para o campo abrir limpo. */
private fun valorParaTexto(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
