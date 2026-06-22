package com.ebd.controle.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val zona: ZoneId = ZoneId.systemDefault()
private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val fmtMesAno = DateTimeFormatter.ofPattern("MM/yyyy")
private val fmtDiaCurto = DateTimeFormatter.ofPattern("dd/MM")

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(zona).toLocalDate()

fun LocalDate.toMillis(): Long =
    this.atStartOfDay(zona).toInstant().toEpochMilli()

fun formatarData(millis: Long?): String =
    if (millis == null) "—" else millis.toLocalDate().format(fmtData)

fun formatarDataCurta(millis: Long): String =
    millis.toLocalDate().format(fmtDiaCurto)

fun formatarMesAno(millis: Long): String = millis.toLocalDate().format(fmtMesAno)

fun hojeMillis(): Long = LocalDate.now().toMillis()

fun formatarMoeda(valor: Double): String =
    "R$ " + String.format(Locale("pt", "BR"), "%,.2f", valor)

/* ===================== TRIMESTRE (calendário CPAD/EBD) =====================
 * 1º: jan-fev-mar | 2º: abr-mai-jun | 3º: jul-ago-set | 4º: out-nov-dez
 * Identificamos cada trimestre pelo par (ano, numero=1..4).
 * ========================================================================== */
data class Trimestre(val ano: Int, val numero: Int) {
    val rotulo: String get() = "${numero}º Trim. / $ano"
    val rotuloCurto: String get() = "${numero}º/${ano}"

    /** Primeiro dia do trimestre (00:00 do 1º do mês inicial). */
    fun inicio(): LocalDate {
        val mes = (numero - 1) * 3 + 1   // 1, 4, 7, 10
        return LocalDate.of(ano, mes, 1)
    }
    /** Último dia do trimestre (último dia do 3º mês). */
    fun fim(): LocalDate {
        val mesFim = numero * 3          // 3, 6, 9, 12
        val ini = LocalDate.of(ano, mesFim, 1)
        return ini.withDayOfMonth(ini.lengthOfMonth())
    }
    fun inicioMillis(): Long = inicio().toMillis()
    /** Próximo instante DEPOIS do trimestre (use comparações `< fimExclusivoMillis`). */
    fun fimExclusivoMillis(): Long = fim().plusDays(1).toMillis()

    fun anterior(): Trimestre = if (numero == 1) Trimestre(ano - 1, 4) else Trimestre(ano, numero - 1)
    fun proximo(): Trimestre = if (numero == 4) Trimestre(ano + 1, 1) else Trimestre(ano, numero + 1)

    /** Os meses do trimestre como rótulos curtos pt-BR ("Jan", "Fev", "Mar"). */
    val mesesAbreviados: List<String>
        get() {
            val base = (numero - 1) * 3
            val nomes = listOf("Jan","Fev","Mar","Abr","Mai","Jun","Jul","Ago","Set","Out","Nov","Dez")
            return listOf(nomes[base], nomes[base + 1], nomes[base + 2])
        }

    companion object {
        fun de(data: LocalDate): Trimestre {
            val n = ((data.monthValue - 1) / 3) + 1
            return Trimestre(data.year, n)
        }
        fun de(millis: Long): Trimestre = de(millis.toLocalDate())
        fun atual(hoje: LocalDate = LocalDate.now()): Trimestre = de(hoje)
    }
}

/** Resultado de cálculo de aniversário. */
data class InfoAniversario(val proxima: LocalDate, val diasAte: Long, val idadeQueFara: Int) {
    val diaSemana: String
        get() = when (proxima.dayOfWeek.value) {
            1 -> "Segunda"; 2 -> "Terça"; 3 -> "Quarta"; 4 -> "Quinta"
            5 -> "Sexta"; 6 -> "Sábado"; else -> "Domingo"
        }
}

fun calcularAniversario(nascimentoMillis: Long, hoje: LocalDate = LocalDate.now()): InfoAniversario {
    val nasc = nascimentoMillis.toLocalDate()
    // Ajuste para 29 de fev em anos não bissextos
    val dia = if (nasc.monthValue == 2 && nasc.dayOfMonth == 29 && !hoje.isLeapYear) 28 else nasc.dayOfMonth
    var prox = try {
        nasc.withYear(hoje.year)
    } catch (e: Exception) {
        nasc.withMonth(2).withDayOfMonth(28).withYear(hoje.year)
    }
    if (prox.isBefore(hoje)) prox = try {
        nasc.withYear(hoje.year + 1)
    } catch (e: Exception) {
        nasc.withMonth(2).withDayOfMonth(28).withYear(hoje.year + 1)
    }
    val dias = ChronoUnit.DAYS.between(hoje, prox)
    val idade = prox.year - nasc.year
    return InfoAniversario(prox, dias, idade)
}
