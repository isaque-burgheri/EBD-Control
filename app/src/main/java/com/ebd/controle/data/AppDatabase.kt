package com.ebd.controle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Classe::class, Aluno::class, Chamada::class, Presenca::class, Financeiro::class,
        RevistaPreco::class, RevistaEntrega::class, CriterioPontuacao::class, PontoLancamento::class,
        Visitante::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classeDao(): ClasseDao
    abstract fun alunoDao(): AlunoDao
    abstract fun chamadaDao(): ChamadaDao
    abstract fun presencaDao(): PresencaDao
    abstract fun financeiroDao(): FinanceiroDao
    abstract fun revistaPrecoDao(): RevistaPrecoDao
    abstract fun revistaEntregaDao(): RevistaEntregaDao
    abstract fun criterioPontuacaoDao(): CriterioPontuacaoDao
    abstract fun pontoLancamentoDao(): PontoLancamentoDao
    abstract fun visitanteDao(): VisitanteDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `visitantes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`nome` TEXT NOT NULL, `telefone` TEXT NOT NULL, " +
                        "`data` INTEGER NOT NULL, `classeId` INTEGER, " +
                        "`observacao` TEXT NOT NULL, `convertido` INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE financeiro ADD COLUMN chamadaId INTEGER")
            }
        }

        // Migração 5 -> 6: adiciona os campos de sincronização sem apagar dados.
        // As colunas são anuláveis (ALTER ADD COLUMN simples) e os registros antigos
        // recebem um uid permanente gerado na hora.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val agora = System.currentTimeMillis()
                val tabelas = listOf("classes", "alunos", "chamadas", "presencas", "financeiro", "visitantes")
                for (t in tabelas) {
                    db.execSQL("ALTER TABLE $t ADD COLUMN uid TEXT")
                    db.execSQL("ALTER TABLE $t ADD COLUMN updatedAt INTEGER")
                    db.execSQL("ALTER TABLE $t ADD COLUMN deleted INTEGER")
                    db.execSQL("UPDATE $t SET uid = lower(hex(randomblob(16))) WHERE uid IS NULL")
                    db.execSQL("UPDATE $t SET updatedAt = $agora WHERE updatedAt IS NULL")
                    db.execSQL("UPDATE $t SET deleted = 0 WHERE deleted IS NULL")
                }
            }
        }

        // Migração 6 -> 7: cria as tabelas de revistas (preços por categoria e
        // entregas por aluno/trimestre) com os campos de sincronização.
        // Não pré-popula dados: categorias e preços vêm do sync.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `revistas_precos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`categoria` TEXT NOT NULL, `preco` REAL NOT NULL DEFAULT 0, " +
                        "`uid` TEXT, `updatedAt` INTEGER, `deleted` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `revistas_entregas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`alunoId` INTEGER NOT NULL, `ano` INTEGER NOT NULL, `trimestre` INTEGER NOT NULL, " +
                        "`tipo` TEXT NOT NULL DEFAULT 'FISICA', `categoria` TEXT NOT NULL DEFAULT '', " +
                        "`preco` REAL NOT NULL DEFAULT 0, " +
                        "`uid` TEXT, `updatedAt` INTEGER, `deleted` INTEGER)"
                )

                // Não pré-popula categorias: os preços vêm da sincronização
                // (mesmo mecanismo do resto do app). Numa instalação sem sync,
                // o usuário cadastra as categorias uma vez pela tela "Preços".
            }
        }

        // Migração 7 -> 8: adiciona o sistema de PONTUAÇÃO.
        //  - coluna `especial` em alunos (aluno de inclusão)
        //  - tabela `criterios_pontuacao` (catálogo do que vale ponto)
        //  - tabela `pontos_lancamentos` (cada ponto marcado)
        // Pré-popula um conjunto de critérios padrão para o app já abrir usável;
        // numa instalação com sync, os critérios da nuvem se mesclam por uid.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val agora = System.currentTimeMillis()

                db.execSQL("ALTER TABLE alunos ADD COLUMN especial INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `criterios_pontuacao` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`nome` TEXT NOT NULL, `pontos` INTEGER NOT NULL DEFAULT 0, " +
                        "`grupo` TEXT NOT NULL DEFAULT 'REGULAR', " +
                        "`porQuantidade` INTEGER NOT NULL DEFAULT 0, " +
                        "`ordem` INTEGER NOT NULL DEFAULT 0, `ativo` INTEGER NOT NULL DEFAULT 1, " +
                        "`uid` TEXT, `updatedAt` INTEGER, `deleted` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pontos_lancamentos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`alunoId` INTEGER NOT NULL, `criterioId` INTEGER NOT NULL, " +
                        "`data` INTEGER NOT NULL, `pontos` INTEGER NOT NULL DEFAULT 0, " +
                        "`quantidade` INTEGER NOT NULL DEFAULT 1, " +
                        "`uid` TEXT, `updatedAt` INTEGER, `deleted` INTEGER)"
                )

                // Critérios padrão (uid fixo e legível -> não duplica ao sincronizar
                // entre celulares que rodaram a mesma migração).
                // (nome, pontos, grupo, porQuantidade, ordem)
                val padrao = listOf(
                    listOf("Presença", 10, "REGULAR", 0, 1),
                    listOf("Pontualidade", 5, "REGULAR", 0, 2),
                    listOf("Trouxe Bíblia", 5, "REGULAR", 0, 3),
                    listOf("Trouxe Revista", 5, "REGULAR", 0, 4),
                    listOf("Participação", 5, "REGULAR", 0, 5),
                    listOf("Trouxe visitante", 15, "REGULAR", 0, 6),
                    listOf("Presença", 10, "ESPECIAL", 0, 1),
                    listOf("Permaneceu na sala", 10, "ESPECIAL", 0, 2),
                    listOf("Atitude / gentileza", 5, "ESPECIAL", 0, 3),
                    listOf("Reverência na oração", 5, "ESPECIAL", 0, 4),
                    listOf("Ajudou a organizar", 5, "ESPECIAL", 0, 5),
                    listOf("Alimento p/ o café", 15, "CAFE", 0, 1),
                    listOf("Sal / Fubá", 5, "CESTA", 1, 1),
                    listOf("Arroz / Óleo", 15, "CESTA", 1, 2),
                    listOf("Leite em pó", 20, "CESTA", 1, 3)
                )
                padrao.forEachIndexed { i, c ->
                    val uid = "seed:criterio:$i"
                    db.execSQL(
                        "INSERT INTO criterios_pontuacao " +
                            "(nome, pontos, grupo, porQuantidade, ordem, ativo, uid, updatedAt, deleted) " +
                            "VALUES (?, ?, ?, ?, ?, 1, ?, ?, 0)",
                        arrayOf(c[0], c[1], c[2], c[3], c[4], uid, agora)
                    )
                }
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebd-controle.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
