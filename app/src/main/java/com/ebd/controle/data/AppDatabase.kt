package com.ebd.controle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Classe::class, Aluno::class, Chamada::class, Presenca::class, Financeiro::class,
        RevistaPreco::class, RevistaEntrega::class, Visitante::class],
    version = 7,
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

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebd-controle.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
