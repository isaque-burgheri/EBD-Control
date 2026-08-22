package com.ebd.controle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Classe::class, Aluno::class, Chamada::class, Presenca::class,
        RevistaAluno::class, CriterioPontuacao::class, PontoLancamento::class,
        Visitante::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classeDao(): ClasseDao
    abstract fun alunoDao(): AlunoDao
    abstract fun chamadaDao(): ChamadaDao
    abstract fun presencaDao(): PresencaDao
    abstract fun revistaAlunoDao(): RevistaAlunoDao
    abstract fun criterioPontuacaoDao(): CriterioPontuacaoDao
    abstract fun pontoLancamentoDao(): PontoLancamentoDao
    abstract fun visitanteDao(): VisitanteDao

    companion object {
        /**
         * Espelha o `version` da anotação acima — anotação não aceita referência ao próprio
         * companion. Serve para carimbar a origem do arquivo de backup; mantenha os dois
         * em sincronia ao subir o esquema.
         */
        const val VERSAO_ESQUEMA = 9

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

                semearCriterios(db)
            }
        }

        /**
         * Migração 8 -> 9: enxuga o app para o que a EBD realmente usa.
         *
         *  - `financeiro` sai: a tela de Finanças era ilustrativa e nunca entrou em uso.
         *    A oferta continua onde sempre esteve de verdade, em `chamadas.oferta`; a
         *    tabela só guardava uma cópia derivada dela.
         *  - `chamadas.dizimos` sai: o app não gerencia dízimo. Não havia nem campo na
         *    interface que escrevesse nele.
         *  - `revistas_precos` + `revistas_entregas` viram `revistas_alunos`, com apenas
         *    "tem revista" e "pagou" por trimestre. Categoria e preço existiam para
         *    alimentar o financeiro, que se foi.
         *
         * As entregas já registradas são preservadas como `temRevista = 1`. Não há como
         * inferir `pago` do que existia (o preço registrado era o de tabela, não um
         * pagamento), então entram como não pago — confira o trimestre corrente no app.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `revistas_alunos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`alunoId` INTEGER NOT NULL, `ano` INTEGER NOT NULL, " +
                        "`trimestre` INTEGER NOT NULL, " +
                        "`temRevista` INTEGER NOT NULL DEFAULT 0, " +
                        "`pago` INTEGER NOT NULL DEFAULT 0, " +
                        "`uid` TEXT, `updatedAt` INTEGER, `deleted` INTEGER)"
                )
                // GROUP BY porque a tabela antiga não tinha índice único: um aluno podia
                // ter mais de uma entrega no mesmo trimestre.
                db.execSQL(
                    "INSERT INTO revistas_alunos " +
                        "(alunoId, ano, trimestre, temRevista, pago, uid, updatedAt, deleted) " +
                        "SELECT alunoId, ano, trimestre, 1, 0, MAX(uid), MAX(updatedAt), " +
                        "MIN(IFNULL(deleted,0)) FROM revistas_entregas " +
                        "GROUP BY alunoId, ano, trimestre"
                )
                db.execSQL("DROP TABLE IF EXISTS revistas_entregas")
                db.execSQL("DROP TABLE IF EXISTS revistas_precos")
                db.execSQL("DROP TABLE IF EXISTS financeiro")

                // O SQLite do minSdk 26 não tem ALTER TABLE DROP COLUMN (só a partir do
                // 3.35 / API 34), então tirar `dizimos` exige recriar a tabela.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chamadas_nova` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`classeId` INTEGER NOT NULL, `data` INTEGER NOT NULL, " +
                        "`licao` INTEGER NOT NULL DEFAULT 0, " +
                        "`oferta` REAL NOT NULL DEFAULT 0.0, " +
                        "`visitantes` INTEGER NOT NULL DEFAULT 0, " +
                        "`uid` TEXT, `updatedAt` INTEGER, `deleted` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO chamadas_nova " +
                        "(id, classeId, data, licao, oferta, visitantes, uid, updatedAt, deleted) " +
                        "SELECT id, classeId, data, licao, oferta, visitantes, uid, updatedAt, deleted " +
                        "FROM chamadas"
                )
                db.execSQL("DROP TABLE chamadas")
                db.execSQL("ALTER TABLE chamadas_nova RENAME TO chamadas")
            }
        }

        /**
         * Critérios padrão, para o app abrir usável sem depender da nuvem.
         *
         * O uid é fixo e legível para não duplicar ao sincronizar entre celulares.
         * E o `updatedAt` fica em 0 de propósito: com o carimbo da instalação, um
         * celular recém-instalado entrava na nuvem "mais recente" que a planilha e
         * devolvia os valores de fábrica por cima dos critérios já ajustados. Em 0,
         * qualquer edição real vence e a semente só preenche o vazio.
         */
        private fun semearCriterios(db: SupportSQLiteDatabase) {
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
                // INSERT simples: não há índice único em `uid`, então um "OR IGNORE"
                // daria falsa sensação de proteção. Os dois chamadores são exclusivos
                // entre si — onCreate roda em banco novo, a migração 7->8 roda em banco
                // que vinha da 7 — então nenhum critério é inserido duas vezes.
                db.execSQL(
                    "INSERT INTO criterios_pontuacao " +
                        "(nome, pontos, grupo, porQuantidade, ordem, ativo, uid, updatedAt, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, 1, ?, 0, 0)",
                    arrayOf(c[0], c[1], c[2], c[3], c[4], "seed:criterio:$i")
                )
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebd-controle.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
                )
                    // Numa instalação limpa o Room cria o esquema direto na versão atual
                    // e NÃO roda migração nenhuma. Sem este callback, os critérios (que
                    // só existiam dentro da MIGRATION_7_8) nunca eram criados: o app
                    // nascia com a tela de Pontuação vazia até alguém configurar a nuvem.
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            semearCriterios(db)
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
