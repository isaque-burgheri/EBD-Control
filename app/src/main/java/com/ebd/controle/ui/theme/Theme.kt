package com.ebd.controle.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ebd.controle.R

/* =====================================================================
 * FONTES — pareamento editorial da marca
 *  - Playfair Display: serifa didone, voz das manchetes (com itálico para
 *    destacar UMA palavra-chave). É a fonte "revista premium".
 *  - Geist: sans geométrica moderna, voz do corpo, dados, labels e kickers.
 *  Ambas são fontes VARIÁVEIS (eixo wght): um único .ttf cobre os pesos.
 *  Registramos cada peso via FontVariation(weight) — jeito correto de
 *  interpolar o peso numa fonte variável. O itálico da Playfair vem de um
 *  arquivo próprio, registrado com FontStyle.Italic.
 * ===================================================================== */
@OptIn(ExperimentalTextApi::class)
private fun geist(peso: Int, w: FontWeight) = Font(
    R.font.geist,
    weight = w,
    variationSettings = FontVariation.Settings(FontVariation.weight(peso))
)

@OptIn(ExperimentalTextApi::class)
private fun playfair(peso: Int, w: FontWeight, italic: Boolean = false) = Font(
    if (italic) R.font.playfair_display_italic else R.font.playfair_display,
    weight = w,
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(peso))
)

/** Sans de corpo, dados e kickers. */
val GeistFamily = FontFamily(
    geist(300, FontWeight.Light),
    geist(400, FontWeight.Normal),
    geist(500, FontWeight.Medium),
    geist(600, FontWeight.SemiBold),
    geist(700, FontWeight.Bold)
)

/** Serifa editorial das manchetes (Playfair mínimo é 400 — sem light). */
val PlayfairFamily = FontFamily(
    playfair(400, FontWeight.Normal),
    playfair(500, FontWeight.Medium),
    playfair(600, FontWeight.SemiBold),
    playfair(700, FontWeight.Bold),
    playfair(400, FontWeight.Normal, italic = true),
    playfair(500, FontWeight.Medium, italic = true),
    playfair(600, FontWeight.SemiBold, italic = true)
)

/* =====================================================================
 * PALETA DA MARCA  (extraída do manual — preto quente + verde-limão)
 *  Regra de ouro: o verde-limão é A assinatura, usado SEMPRE com parcimônia.
 *  No tema claro ele aparece só como PREENCHIMENTO (atrás de texto preto:
 *  pílula do nav, FAB, toggle ligado) — nunca como texto/ícone sobre branco,
 *  onde teria contraste fraco. No tema escuro o limão brilha e vira o acento
 *  principal (texto, ícones, números de destaque).
 *  Nada de azul. Nada de preto frio/digital — o preto é quente (#1A1615).
 * ===================================================================== */
private val PretoCS      = Color(0xFF1A1615) // preto quente — fundo dominante
private val Lima         = Color(0xFFDBFFA5) // verde-limão — A cor da marca
private val LimaClara    = Color(0xFFDFFFAE) // variação do acento
private val Branco        = Color(0xFFFFFFFF)
private val Papel        = Color(0xFFFCF8EC) // off-white "papel"
private val Floresta     = Color(0xFF0F352B) // verde escuríssimo institucional
private val Petroleo     = Color(0xFF243833) // verde-petróleo editorial
private val Musgo        = Color(0xFF274E13) // verde-musgo: destaque legível no claro
private val Grafite      = Color(0xFF666666) // texto de apoio / metadados
private val CinzaClaro   = Color(0xFFEFEFEF)

// neutros quentes derivados (para o escuro não puxar para o azul/frio)
private val SuperficieEsc      = Color(0xFF221E1C)
private val SuperficieEscVar   = Color(0xFF2B2623)
private val LinhaEsc           = Color(0xFF3C3531)
private val BrancoQuente       = Color(0xFFF6F2EA)
private val CinzaQuenteEsc     = Color(0xFFB7ADA3)
private val PapelClaroSurfVar  = Color(0xFFF4F0E7)
private val LinhaClaro         = Color(0xFFE4DFD4)

// estados (a marca evita vermelho, então usamos um tijolo quente e discreto)
private val TijoloClaro  = Color(0xFFB5472F)
private val TerracotaEsc = Color(0xFFE0A08C)

/* ---- Tema CLARO: branco editorial, preto quente como ação, limão só fill ---- */
private val LightColors = lightColorScheme(
    primary = PretoCS,
    onPrimary = Branco,
    primaryContainer = Lima,                 // FAB = "cápsula" verde-limão...
    onPrimaryContainer = PretoCS,            // ...com ícone preto (CTA da marca)
    secondary = Petroleo,
    onSecondary = Branco,
    secondaryContainer = Color(0xFFEDE7D8),  // papel quente
    onSecondaryContainer = PretoCS,
    tertiary = Musgo,
    onTertiary = Branco,
    tertiaryContainer = LimaClara,
    onTertiaryContainer = PretoCS,
    background = Branco,
    onBackground = PretoCS,
    surface = Branco,
    onSurface = PretoCS,
    surfaceVariant = PapelClaroSurfVar,
    onSurfaceVariant = Grafite,
    surfaceTint = PretoCS,
    outline = LinhaClaro,
    outlineVariant = CinzaClaro,
    inversePrimary = Lima,
    scrim = PretoCS,
    error = TijoloClaro,
    onError = Branco,
    errorContainer = Color(0xFFF6DDD6),
    onErrorContainer = Color(0xFF5A1B12)
)

/* ---- Tema ESCURO: preto quente, limão como acento principal ---- */
private val DarkColors = darkColorScheme(
    primary = Lima,                          // botões/seleção verde-limão...
    onPrimary = PretoCS,                      // ...com conteúdo preto
    primaryContainer = Musgo,
    onPrimaryContainer = LimaClara,
    secondary = Color(0xFFCFC8BC),
    onSecondary = PretoCS,
    secondaryContainer = Petroleo,
    onSecondaryContainer = LimaClara,
    tertiary = LimaClara,
    onTertiary = PretoCS,
    tertiaryContainer = Floresta,
    onTertiaryContainer = LimaClara,
    background = PretoCS,
    onBackground = BrancoQuente,
    surface = SuperficieEsc,
    onSurface = BrancoQuente,
    surfaceVariant = SuperficieEscVar,
    onSurfaceVariant = CinzaQuenteEsc,
    surfaceTint = Lima,
    outline = LinhaEsc,
    outlineVariant = SuperficieEscVar,
    inversePrimary = Musgo,
    scrim = Color.Black,
    error = TerracotaEsc,
    onError = PretoCS,
    errorContainer = Color(0xFF5A2A1E),
    onErrorContainer = Color(0xFFF6DDD6)
)

/* =====================================================================
 * ACENTOS SEMÂNTICOS QUE RESPEITAM O TEMA
 *  As telas usam Azul/Verde/Vermelho como cor de faixa dos cards e barras
 *  de gráfico. Mantemos os MESMOS nomes (nada nas telas quebra), mas agora
 *  apontando para a paleta da marca — SEM azul real (a marca proíbe azul).
 *  No claro usamos verdes escuros legíveis sobre branco; no escuro o limão
 *  pode aparecer como número/heroi porque tem ótimo contraste.
 * ===================================================================== */
val Azul: Color   // acento neutro/informativo (verde-petróleo no claro; sálvia no escuro)
    @Composable @ReadOnlyComposable get() =
        if (LocalAppBrushes.current.escuro) Color(0xFF9DB8A8) else Petroleo
val Verde: Color  // positivo (musgo legível no claro; limão-assinatura no escuro)
    @Composable @ReadOnlyComposable get() =
        if (LocalAppBrushes.current.escuro) Lima else Musgo
val Vermelho: Color // negativo/erro, em tom quente alinhado à paleta
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

/** Verde-limão puro da marca — preenchimento de destaque (pílula, toggle,
 *  "marca-texto" atrás de uma palavra). Use sempre com conteúdo PRETO em cima. */
val Realce: Color get() = Lima
val RealceConteudo: Color get() = PretoCS

/* Gradiente de fundo — quase chapado, no espírito editorial da marca
 * (a marca prefere fundos planos; o leve degradê só dá profundidade sutil). */
data class AppBrushes(val fundo: Brush, val escuro: Boolean)

val LocalAppBrushes = staticCompositionLocalOf {
    AppBrushes(Brush.verticalGradient(listOf(Branco, Branco)), false)
}

private fun brushesClaro() = AppBrushes(
    fundo = Brush.verticalGradient(
        0f to Branco,
        1f to Color(0xFFFBF9F3) // levíssimo "papel" no rodapé
    ),
    escuro = false
)

private fun brushesEscuro() = AppBrushes(
    fundo = Brush.verticalGradient(
        0f to Color(0xFF1C1817),
        1f to Color(0xFF161210) // preto quente, mais fechado no rodapé
    ),
    escuro = true
)

/* =====================================================================
 * TIPOGRAFIA
 *  Manchetes/títulos em Playfair Display (sempre — claro e escuro), com
 *  kerning levemente apertado nos tamanhos grandes. Corpo, dados, labels e
 *  kickers em Geist. Kickers (labelSmall) em tracking largo, caixa alta
 *  aplicada no texto pela própria tela/Componente.
 * ===================================================================== */
private fun tipografia(): Typography {
    val titulo = PlayfairFamily
    val corpo = GeistFamily
    return Typography(
        displaySmall = TextStyle(
            fontFamily = titulo,
            fontWeight = FontWeight.Medium,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = titulo,
            fontWeight = FontWeight.Medium,
            fontSize = 27.sp,
            lineHeight = 33.sp,
            letterSpacing = (-0.3).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = titulo,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = (-0.2).sp
        ),
        titleLarge = TextStyle(
            fontFamily = titulo,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        ),
        titleMedium = TextStyle(
            fontFamily = corpo,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = corpo,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = corpo,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = corpo,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp
        ),
        labelLarge = TextStyle(
            fontFamily = corpo,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.2.sp
        ),
        labelMedium = TextStyle(
            fontFamily = corpo,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp
        ),
        // Kicker / eyebrow: caixa alta + tracking largo (aplicado no uso)
        labelSmall = TextStyle(
            fontFamily = corpo,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.6.sp
        )
    )
}

@Composable
fun EBDTheme(useDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (useDark) DarkColors else LightColors
    val brushes = if (useDark) brushesEscuro() else brushesClaro()

    CompositionLocalProvider(LocalAppBrushes provides brushes) {
        MaterialTheme(
            colorScheme = colors,
            typography = tipografia(),
            content = content
        )
    }
}
