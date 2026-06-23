package com.ebd.controle.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * Convenção:
 *  - Consultas "ativas" (usadas pela interface) escondem registros excluídos: IFNULL(deleted,0)=0
 *  - Consultas de sincronização (todosIncl / porUid) trazem TUDO, inclusive excluídos
 */

@Dao
interface ClasseDao {
    @Query("SELECT * FROM classes WHERE IFNULL(deleted,0)=0 ORDER BY nome")
    fun observarTodas(): Flow<List<Classe>>

    @Query("SELECT * FROM classes WHERE IFNULL(deleted,0)=0 ORDER BY nome")
    suspend fun listarTodas(): List<Classe>

    @Query("SELECT COUNT(*) FROM classes WHERE IFNULL(deleted,0)=0")
    suspend fun contar(): Int

    @Query("SELECT * FROM classes")
    suspend fun todosIncl(): List<Classe>

    @Query("SELECT * FROM classes WHERE uid = :uid LIMIT 1")
    suspend fun porUid(uid: String): Classe?

    @Insert suspend fun inserir(c: Classe): Long
    @Update suspend fun atualizar(c: Classe)
    @Query("DELETE FROM classes") suspend fun deletarTudo()
}

@Dao
interface AlunoDao {
    @Query("SELECT * FROM alunos WHERE IFNULL(deleted,0)=0 ORDER BY nome")
    fun observarTodos(): Flow<List<Aluno>>

    @Query("SELECT * FROM alunos WHERE IFNULL(deleted,0)=0 ORDER BY nome")
    suspend fun listarTodos(): List<Aluno>

    @Query("SELECT * FROM alunos WHERE classeId = :classeId AND ativo = 1 AND IFNULL(deleted,0)=0 ORDER BY nome")
    fun observarPorClasse(classeId: Long): Flow<List<Aluno>>

    @Query("SELECT * FROM alunos WHERE classeId = :classeId AND ativo = 1 AND IFNULL(deleted,0)=0 ORDER BY nome")
    suspend fun listarPorClasse(classeId: Long): List<Aluno>

    @Query("SELECT COUNT(*) FROM alunos WHERE ativo = 1 AND IFNULL(deleted,0)=0")
    suspend fun contarAtivos(): Int

    @Query("SELECT * FROM alunos") suspend fun todosIncl(): List<Aluno>
    @Query("SELECT * FROM alunos WHERE uid = :uid LIMIT 1") suspend fun porUid(uid: String): Aluno?
    @Query("SELECT * FROM alunos WHERE id = :id LIMIT 1") suspend fun porId(id: Long): Aluno?

    @Insert suspend fun inserir(a: Aluno): Long
    @Update suspend fun atualizar(a: Aluno)
    @Query("DELETE FROM alunos") suspend fun deletarTudo()
}

@Dao
interface ChamadaDao {
    @Query("SELECT * FROM chamadas WHERE IFNULL(deleted,0)=0 ORDER BY data DESC")
    fun observarTodas(): Flow<List<Chamada>>

    @Query("SELECT * FROM chamadas WHERE IFNULL(deleted,0)=0 ORDER BY data DESC")
    suspend fun listarTodas(): List<Chamada>

    @Query("SELECT * FROM chamadas WHERE classeId = :classeId AND IFNULL(deleted,0)=0 ORDER BY data DESC")
    suspend fun listarPorClasse(classeId: Long): List<Chamada>

    @Query("SELECT * FROM chamadas WHERE classeId = :classeId AND data = :data AND IFNULL(deleted,0)=0 LIMIT 1")
    suspend fun buscar(classeId: Long, data: Long): Chamada?

    @Query("SELECT * FROM chamadas WHERE classeId = :classeId AND data >= :ini AND data < :fim AND IFNULL(deleted,0)=0 ORDER BY data DESC LIMIT 1")
    suspend fun buscarNoDia(classeId: Long, ini: Long, fim: Long): Chamada?

    @Query("SELECT * FROM chamadas WHERE IFNULL(deleted,0)=0 ORDER BY data DESC LIMIT 1")
    suspend fun ultima(): Chamada?

    @Query("SELECT * FROM chamadas") suspend fun todosIncl(): List<Chamada>
    @Query("SELECT * FROM chamadas WHERE uid = :uid LIMIT 1") suspend fun porUid(uid: String): Chamada?

    @Insert suspend fun inserir(c: Chamada): Long
    @Update suspend fun atualizar(c: Chamada)
    @Query("DELETE FROM chamadas") suspend fun deletarTudo()
}

@Dao
interface PresencaDao {
    @Query("SELECT * FROM presencas WHERE chamadaId = :chamadaId AND IFNULL(deleted,0)=0")
    suspend fun listarPorChamada(chamadaId: Long): List<Presenca>

    @Query("SELECT COUNT(*) FROM presencas WHERE chamadaId = :chamadaId AND presente = 1 AND IFNULL(deleted,0)=0")
    suspend fun contarPresentes(chamadaId: Long): Int

    @Query("SELECT COUNT(*) FROM presencas WHERE chamadaId = :chamadaId AND IFNULL(deleted,0)=0")
    suspend fun contarTotal(chamadaId: Long): Int

    @Query("SELECT * FROM presencas") suspend fun todosIncl(): List<Presenca>
    @Query("SELECT * FROM presencas WHERE uid = :uid LIMIT 1") suspend fun porUid(uid: String): Presenca?

    @Insert suspend fun inserir(p: Presenca): Long
    @Update suspend fun atualizar(p: Presenca)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun inserirVarias(lista: List<Presenca>)
    @Query("DELETE FROM presencas WHERE chamadaId = :chamadaId") suspend fun limparChamada(chamadaId: Long)
    @Query("DELETE FROM presencas") suspend fun deletarTudo()
}

@Dao
interface FinanceiroDao {
    @Query("SELECT * FROM financeiro WHERE IFNULL(deleted,0)=0 ORDER BY data DESC")
    fun observarTodos(): Flow<List<Financeiro>>

    @Query("SELECT * FROM financeiro WHERE IFNULL(deleted,0)=0 ORDER BY data DESC")
    suspend fun listarTodos(): List<Financeiro>

    @Query("SELECT * FROM financeiro WHERE chamadaId = :chamadaId AND IFNULL(deleted,0)=0 LIMIT 1")
    suspend fun buscarPorChamada(chamadaId: Long): Financeiro?

    @Query("SELECT * FROM financeiro") suspend fun todosIncl(): List<Financeiro>
    @Query("SELECT * FROM financeiro WHERE uid = :uid LIMIT 1") suspend fun porUid(uid: String): Financeiro?

    @Insert suspend fun inserir(f: Financeiro): Long
    @Update suspend fun atualizar(f: Financeiro)
    @Delete suspend fun deletar(f: Financeiro)
    @Query("DELETE FROM financeiro") suspend fun deletarTudo()
}

@Dao
interface VisitanteDao {
    @Query("SELECT * FROM visitantes WHERE IFNULL(deleted,0)=0 ORDER BY data DESC")
    fun observarTodos(): Flow<List<Visitante>>

    @Query("SELECT * FROM visitantes WHERE IFNULL(deleted,0)=0 ORDER BY data DESC")
    suspend fun listarTodos(): List<Visitante>

    @Query("SELECT COUNT(*) FROM visitantes WHERE convertido = 0 AND IFNULL(deleted,0)=0")
    suspend fun contarPendentes(): Int

    @Query("SELECT * FROM visitantes") suspend fun todosIncl(): List<Visitante>
    @Query("SELECT * FROM visitantes WHERE uid = :uid LIMIT 1") suspend fun porUid(uid: String): Visitante?

    @Insert suspend fun inserir(v: Visitante): Long
    @Update suspend fun atualizar(v: Visitante)
    @Query("DELETE FROM visitantes") suspend fun deletarTudo()
}
