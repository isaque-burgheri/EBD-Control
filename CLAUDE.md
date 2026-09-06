# EBD Controle — notas para quem for mexer

App Android (Kotlin + Compose) de gestão da Escola Bíblica Dominical: classes,
membros, chamada, pontuação/ranking, relatórios e a ajuda de custo dos professores.

Este arquivo guarda o que **não dá para deduzir lendo o código** — as decisões que já
foram tomadas e os erros que já custaram caro. O resto está nos comentários do código,
que são detalhados de propósito.

## O contexto que explica quase tudo

O app é usado por **três pessoas** (coordenador, diretor e o autor), numa igreja, com
os dados de ~63 membros. Isso define as prioridades:

- **Perder dado é o pior defeito possível.** Não há suporte, não há QA, e quem usa não
  vai saber diagnosticar. Vale escrever mais código para não perder nada.
- Funciona **offline**. A internet da igreja é ruim e a chamada acontece no domingo de
  manhã, com ou sem sinal.
- Ninguém vai "configurar" nada. O que precisar de configuração precisa de um padrão
  que já funcione sozinho.

## O "banco de dados" é uma planilha do Google

Room local + sincronização de mão dupla com uma planilha, via Apps Script
(`appscript/Code.gs`). A planilha é onde o coordenador olha os dados fora do app.

**Regras da sincronização** (mexer aqui sem entender já quebrou o app antes):

- Merge por `uid`, **última alteração vence** (`updatedAt`, comparação estritamente
  maior). Todas as tabelas têm `uid` / `updatedAt` / `deleted`.
- **Exclusão é lógica.** A linha nunca some, fica com `deleted = 1` — é assim que a
  exclusão se propaga para os outros celulares.
- **`uid` determinístico onde a linha é única por natureza**: presença é
  `"chamadaUid:alunoUid"`, ponto é `"alunoUid:criterioUid:data"`, contribuição é
  `"contrib:alunoUid:ano:mes"`. É o que impede dois celulares de criarem linhas
  duplicadas para o mesmo fato.
- **Campo ausente é falta de informação, não exclusão.** Um aparelho numa versão
  anterior não envia os campos novos; sem guarda, o primeiro sync dele apaga o campo
  em todos os outros. Ver `temValor()` em `Repository.kt` — vale para célula vazia na
  planilha também.
- As abas da planilha são lidas **por posição de coluna**. Inserir uma coluna no lugar
  errado desalinha `updatedAt`/`deleted` silenciosamente. Toda mudança de schema
  precisa de uma função `MIGRAR_PARA_Vn` no `Code.gs`, que o usuário roda uma vez.
- A URL `/exec` é **secreta**: `doGet` sem parâmetro devolve a planilha inteira, com
  telefones e datas de nascimento. Só o recorte `?ranking=1` é público (é o que
  alimenta o mural no GitHub Pages).

Nada chama a sincronização diretamente. O `SyncManager` observa o `InvalidationTracker`
do Room e dispara ~2,5 s depois de qualquer gravação, no foreground e a cada 15 min.
**Consequência prática:** uma tela que grava a cada toque gera uma rajada de sincronizações,
e cada uma envia o banco inteiro. Por isso Chamada e Pontuação acumulam em memória e
gravam num botão só.

## Telas que acumulam antes de gravar

`ChamadaScreen` e a aba "Marcar pontos" de `PontuacaoScreen` seguem o mesmo padrão:
marca-se à vontade, nada toca o banco até o botão no fim da lista. Ao mexer nelas:

- O rascunho precisa **sobreviver à navegação**. O `ViewModel` de uma aba morre quando
  se troca de item na barra inferior (`popUpTo` com `saveState`). Chamada usa
  `rememberSaveable` com savers; Pontuação usa `SavedStateHandle`.
- **Classe e data andam junto do rascunho.** Um rascunho restaurado sobre outra classe
  ou outro domingo lança os pontos no lugar errado.
- **Grave só as diferenças.** Linha não tocada não deve ter o `updatedAt` mexido, senão
  volta a viajar para a planilha sem necessidade.
- Trocar de classe/data com marcações pendentes **pede confirmação**. Antes do
  salvamento em lote não havia nada a perder; agora há.

## Quem é "professor"

`Aluno.professor`, um booleano próprio — **não** o `cargo` e **não** a classe. Os dois
foram testados contra os dados reais e falharam: há professor lotado na classe que
ensina (Crianças), e `cargo` é texto livre onde só 3 de 10 professores tinham
"Professor". A tela de Contribuições lista quem tem essa marca e está ativo;
desmarcar tira a pessoa da lista sem apagar o histórico dela.

## Versão

**Uma linha só**, em `app/build.gradle.kts`:

```kotlin
val versao = "3.6"
```

O `versionCode` é calculado (`3*100 + 6 = 306`). Nunca edite o `versionCode` à mão — foi
exatamente assim que ele ficou em `3` enquanto o nome já estava em `3.2`.

## Assinatura

Os APKs distribuídos são **release, assinados com a chave do autor**
(`keystore.properties` na raiz, fora do git — veja `keystore.properties.exemplo`).

Nunca volte a distribuir APK de debug: a chave de debug é descartável e muda de máquina
para máquina. Foi isso que impediu a 3.3 de instalar por cima da 3.2 e obrigou a
desinstalar o app — com perda dos dados locais de quem não tinha sincronizado.

## Build

Precisa de JDK 17+ (o do Android Studio serve: `Android Studio/jbr`).

```bash
./gradlew :app:assembleRelease
```

Em algumas sessões o Gradle falha com `Unable to establish loopback connection`: é o
`TEMP` longo demais para os sockets AF_UNIX do daemon. Aponte `TMP`/`TEMP` para um
caminho curto (`C:\gtmp`) antes de buildar.

## Migrações do Room

Uma migração por versão, em `AppDatabase.kt`, e **atualize `VERSAO_ESQUEMA` junto** da
anotação `@Database` (a anotação não aceita referência ao companion, então os dois
espelham o mesmo número na mão).

O `minSdk` é 26 e o SQLite dele **não tem `ALTER TABLE DROP COLUMN`** — remover coluna
exige recriar a tabela (ver `MIGRATION_8_9`).

Instalação limpa **não roda migração nenhuma**: o que precisa existir num banco novo vai
no `addCallback(onCreate)`, não só dentro da migração. Foi assim que os critérios de
pontuação sumiram numa instalação do zero.

## Ao adicionar uma tabela ou um campo

Sete lugares, e esquecer um só aparece semanas depois:

1. `Entities.kt`
2. `Daos.kt`
3. `AppDatabase.kt` — entidade, `version`, `VERSAO_ESQUEMA` e a migração
4. `Repository.kt` — CRUD, `coletarParaBackup`, `restaurarDeBackup`, `limparTudo`,
   `montarPayloadSync`, `aplicarSync`
5. `Backup.kt` — `DadosBackup`, escrita e leitura do `.json`
6. `appscript/Code.gs` — `SCHEMA` e uma função `MIGRAR_PARA_Vn`
7. A UI

## Idioma

Código, comentários e commits em **português**. Nomes de identificadores também
(`salvarChamada`, `marcasSalvas`, `recarregarMarcacao`). Mantenha.
