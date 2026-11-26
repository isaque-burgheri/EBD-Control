package com.ebd.controle.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.withFrameNanos
import com.ebd.controle.R
import kotlin.math.floor
import kotlin.math.min

/**
 * Efeito em pixel art (biblioteca pixelfx, `pickups/collect-burst`, recolorido
 * em verde-limão da marca) tocado uma vez por disparo.
 *
 * A folha de sprites é uma grade de [COLUNAS] x linhas com quadros de
 * [LARGURA_QUADRO] x [ALTURA_QUADRO] px, a [FPS] quadros por segundo. Fica em
 * `drawable-nodpi` para o Android não redimensionar o PNG, e é desenhada sem
 * filtro (FilterQuality.None) e em escala INTEIRA — pixel art borrada perde a graça.
 *
 * Só desenha; não toca em banco nem em sync. Incremente [disparo] para tocar de novo
 * (0 = parado). Não consome toques: pode ficar por cima da tela.
 */
@Composable
fun EfeitoPixel(disparo: Int, modifier: Modifier = Modifier) {
    val folha: ImageBitmap = ImageBitmap.imageResource(R.drawable.fx_chamada_salva)
    var quadro by remember { mutableIntStateOf(-1) }

    LaunchedEffect(disparo) {
        if (disparo <= 0) return@LaunchedEffect
        val inicio = withFrameNanos { it }
        while (true) {
            val q = ((withFrameNanos { it } - inicio) / 1_000_000_000.0 * FPS).toInt()
            if (q >= TOTAL_QUADROS) break
            quadro = q
        }
        quadro = -1 // terminou: some e não desenha mais nada
    }

    if (quadro < 0) return
    Canvas(modifier.fillMaxSize()) {
        // Escala inteira que cabe na tela: quadro nítido, sem meio-pixel.
        val escala = floor(min(size.width / LARGURA_QUADRO, size.height / ALTURA_QUADRO)).toInt().coerceAtLeast(1)
        val destino = IntSize(LARGURA_QUADRO * escala, ALTURA_QUADRO * escala)
        val topoEsq = Offset((size.width - destino.width) / 2f, (size.height - destino.height) / 2f)
        drawImage(
            image = folha,
            srcOffset = IntOffset((quadro % COLUNAS) * LARGURA_QUADRO, (quadro / COLUNAS) * ALTURA_QUADRO),
            srcSize = IntSize(LARGURA_QUADRO, ALTURA_QUADRO),
            dstOffset = IntOffset(topoEsq.x.toInt(), topoEsq.y.toInt()),
            dstSize = destino,
            filterQuality = FilterQuality.None
        )
    }
}

private const val COLUNAS = 7
private const val TOTAL_QUADROS = 42
private const val LARGURA_QUADRO = 112
private const val ALTURA_QUADRO = 108
private const val FPS = 30
