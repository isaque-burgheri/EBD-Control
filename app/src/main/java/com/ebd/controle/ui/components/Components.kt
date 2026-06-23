package com.ebd.controle.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebd.controle.data.formatarData
import com.ebd.controle.data.hojeMillis
import com.ebd.controle.ui.theme.Realce
import com.ebd.controle.ui.theme.RealceConteudo

/* Raio padrão — editorial, contido (manual de marca usa ~16px). */
private val RaioCard = 16.dp

/**
 * Cores de Switch da marca: quando ligado, trilho verde-limão com polegar
 * preto — o "toggle ON" verde-limão é um recurso visual recorrente da marca.
 * Vale nos dois temas.
 */
@Composable
fun realceSwitchColors(): SwitchColors {
    val escuro = com.ebd.controle.ui.theme.LocalAppBrushes.current.escuro
    // No escuro a "cápsula" verde-limão destoaria da paleta violeta — então o
    // ON usa o índigo da marca noturna (trilho periwinkle + polegar tinta).
    val trilho = if (escuro) MaterialTheme.colorScheme.primary else Realce
    val polegar = if (escuro) MaterialTheme.colorScheme.onPrimary else RealceConteudo
    return SwitchDefaults.colors(
        checkedThumbColor = polegar,
        checkedTrackColor = trilho,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = trilho
    )
}

/**
 * Kicker / eyebrow editorial: rótulo curto em CAIXA ALTA com tracking largo,
 * no canto superior do bloco. É uma das assinaturas da marca — sempre 1 a 2
 * palavras, cor de apoio (grafite no claro / cinza quente no escuro).
 */
@Composable
fun Kicker(texto: String, modifier: Modifier = Modifier, cor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = cor,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val text = options.getOrNull(selectedIndex) ?: ""
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            shape = RoundedCornerShape(12.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelected(i); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, millis: Long?, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = formatarData(millis),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        trailingIcon = {
            IconButton(onClick = { show = true }) { Icon(Icons.Default.DateRange, contentDescription = "Escolher data") }
        },
        modifier = modifier.fillMaxWidth()
    )
    if (show) {
        val st = rememberDatePickerState(initialSelectedDateMillis = millis ?: hojeMillis())
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { st.selectedDateMillis?.let(onPick); show = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancelar") } }
        ) { DatePicker(state = st) }
    }
}

/**
 * Card de número, no estilo "relatório editorial": fundo chapado, borda fina
 * de 1pt (em vez de sombra pesada), uma faixa de acento à esquerda e o número
 * grande em Geist. Leitura rápida, muito respiro. Respeita claro/escuro.
 */
@Composable
fun StatCard(title: String, value: String, subtitle: String = "", accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(RaioCard)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // faixa de cor à esquerda (acento contido)
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(accent)
            )
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Kicker(title)
                Spacer(Modifier.height(8.dp))
                Text(
                    value,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Gráfico de barras editorial: barras chapadas com cantos levemente
 * arredondados e um leve degradê no acento. Combina com os dois temas.
 */
@Composable
fun BarChart(data: List<Pair<String, Float>>, accent: Color, modifier: Modifier = Modifier) {
    if (data.isEmpty()) {
        Text("Sem dados para exibir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maxV = data.maxOf { it.second }.coerceAtLeast(1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val n = data.size
            val gap = 12.dp.toPx()
            val bottomPad = 4.dp.toPx()
            val bw = ((size.width - gap * (n + 1)) / n).coerceAtLeast(2f)
            val raio = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            data.forEachIndexed { i, pair ->
                val h = (pair.second / maxV) * (size.height - bottomPad)
                val x = gap + i * (bw + gap)
                val top = size.height - bottomPad - h
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to accent,
                        1f to accent.copy(alpha = 0.6f),
                        startY = top,
                        endY = size.height
                    ),
                    topLeft = Offset(x, top),
                    size = Size(bw, h),
                    cornerRadius = raio
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            data.forEach { pair ->
                Text(
                    pair.first,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
