# Análise técnica — EBD Controle

Levantamento feito em 21/08/2026 sobre o estado atual do código (Room v8, módulo único,
~5.400 linhas Kotlin/Compose, sync com Google Sheets via Apps Script).

> **Revisão:** a primeira versão deste documento atribuía o sumiço de datas a um erro de
> parsing de ISO-8601 no app. Com o Apps Script em mãos isso se mostrou **errado** — o
> backend já faz `if (v instanceof Date) v = v.getTime()` na leitura. A causa real está
> descrita em §2 e é bem pior. O trecho errado foi removido.

## Veredicto: melhorar, não refazer

O app **não** tem problema de arquitetura. Tem defeitos pontuais, e o mais grave deles não
está no Kotlin — está no contrato entre o app e a planilha. Reescrever do zero jogaria fora
12 telas funcionais sem tocar na causa real.

---

## Bugs de perda de dados

### 1. Dízimos eram zerados a cada sincronização — CRÍTICO

`Chamada.dizimos` existe na entidade (`Entities.kt:48`) e no backup local
(`Backup.kt:36`), mas **não existia em nenhuma ponta do caminho de nuvem**:

- `Repository.montarPayloadSync()` montava a chamada sem `dizimos`.
- `Repository.aplicarSync()` reconstruía `Chamada(...)` sem `dizimos` → padrão `0.0`.
- `SCHEMA.chamadas.campos` no Apps Script também não tinha o campo.

Como `aplicarSync` faz **substituição de linha inteira**, todo sync que trouxesse uma
chamada mais nova reescrevia o dízimo local como zero. Silenciosamente, em todos os
aparelhos.

Corrigir só o app não bastava: o script precisava da coluna. Os dois lados foram alterados.

### 0. O que a planilha real mostrou (21/08)

Inspeção de uma cópia `.xlsx` da planilha em produção — 63 alunos, 30 chamadas, 398
presenças, 505 pontos. Confirmou parte do diagnóstico, refutou outra parte e revelou um
defeito novo.

**Confirmado:** `updatedAt` é célula de **data** em 100% das 1.085 linhas, em todas as 10
abas. A coluna de dízimos não existe. Os 15 critérios de fábrica têm **5 carimbos
distintos** — cada instalação sobrescreveu os anteriores, exatamente o bug 3.

**Refutado:** a hipótese de perda de precisão em milissegundos. Os carimbos preservam os
milissegundos (395 valores distintos em 505 linhas de `pontos`). O `updatedAt` como data
continua sendo o defeito que causou o item 0-b abaixo, mas não pela via que eu supunha.

**Sobre os aniversários:** 33 dos 60 alunos ativos estão sem data de nascimento, mas os
carimbos deles se concentram em **23/06 — o dia mais antigo da base**, ou seja, a data de
criação. Se tivessem sido apagados por sincronização, o carimbo seria posterior. Nesta
cópia **não há assinatura de apagamento**; parece campo nunca preenchido. Duas das 27 datas
presentes têm componente de hora 21:00 em vez de 00:00, o que indica deslocamento de fuso
no ida-e-volta — essas duas provavelmente exibem um dia a menos.

### 0-b. A coluna `especial` está corrompida — CRÍTICO, achado novo

A coluna **Especial** da aba `alunos` está **formatada como data**. Os valores 0 e 1 viraram
serial: 57 células lidas como `00:00:00` e 4 como `1900-01-01`.

O estrago é de tipo, não de aparência. O `getValues()` devolve um objeto `Date` para célula
formatada como data mesmo quando ela guarda 0 ou 1; o `lerAba_` faz `v.getTime()`; o app
recebe algo como `-2209161600000` e o `jBool` (`v.toInt() == 1`) nunca bate. **A marcação
"Aluno especial (inclusão)" não volta da nuvem para ninguém** — os alunos marcados veem o
grupo de critérios errado na tela de pontuação.

É a cicatriz de um deslocamento de colunas antigo: quando `especial` entrou no esquema, ela
caiu na posição que era do `updatedAt`, herdando a formatação de data. Os valores foram
consertados depois; o formato ficou. O contorno `if (d instanceof Date) return true` dentro
de `nao_excluido_` existe por causa desse mesmo episódio.

**Corrigido no script:** `aplicarFormatoData_` agora repõe o formato padrão em **todas** as
colunas a cada gravação antes de aplicar data onde é devido — formatação errada aqui não é
cosmética, é corrupção de tipo. E `MIGRAR_PARA_V2` limpa o formato das colunas não-data
antes de ler, o que faz os 0/1 voltarem a ser lidos como números, sem conversão.

> **Depois da migração, remarque os 5 alunos especiais no app.** A planilha fica correta,
> mas o app só aceita registro remoto com carimbo estritamente mais novo — e a migração não
> mexe nos carimbos. Remarcar no app gera carimbo novo e propaga.

### 2. `updatedAt` era gravado como célula de DATA — CRÍTICO, e é a raiz do resto

No esquema antigo, `updatedAt` estava listado em `datas`. O `hasOwnProperty` em
`upsertAba_` não olhava o valor (`false`), só a presença da chave — então o carimbo era
convertido para `new Date(n)` e gravado como **célula de data**.

> **Ressalva, verificada na planilha real:** eu supunha que isso custava a precisão de
> milissegundos do carimbo. **Não custa** — os milissegundos sobrevivem. O defeito de
> gravar `updatedAt` como data é outro, e apareceu inteiro no item 0-b: célula formatada
> como data faz `getValues()` devolver `Date` em vez do número guardado, e é assim que uma
> coluna vizinha herdando essa formatação vira corrupção de tipo.

Resta a assimetria de critério entre as duas pontas, que é real:

| | Critério para aceitar o registro que chega |
|---|---|
| App (`Repository.aplicarSync`) | `remoto > local` — estritamente mais novo |
| Script (`upsertAba_`) | `incoming >= atual` — empate também passa |

Com carimbos empatados, o servidor aceita e o app recusa. Toda sincronização envia a base
inteira (`todosIncl()`), então um aparelho parado com uma versão mais pobre de um registro
reescreve a planilha no empate, enquanto recusa a versão boa que vem de volta. Dois
critérios diferentes para a mesma regra não convergem.

Empate acontece sempre que o carimbo não é bumpado entre duas escritas — o caso da semente
de critérios do item 3 é exatamente esse.

**Corrigido:** `updatedAt` virou número puro no script, e o `>=` virou `>`, igualando o
critério dos dois lados.

### 3. Critérios de fábrica sobrescreviam os personalizados

A migração 7→8 (`AppDatabase.kt`) insere 15 critérios padrão com **uid fixo**
(`seed:criterio:0..14`) e `updatedAt = System.currentTimeMillis()`.

Uid fixo é a decisão certa — evita duplicata. O carimbo é que estava errado: um celular
instalado hoje entra na nuvem "mais recente" que a planilha e devolve os **valores de
fábrica** por cima dos critérios que você ajustou. Todo aparelho novo resetava a pontuação
de todo mundo.

**Corrigido:** a semente entra com `updatedAt = 0`. Qualquer edição real vence; a semente
só preenche o vazio.

> Isso vale para instalações futuras. Nos aparelhos onde a migração já rodou o carimbo
> antigo permanece — se os critérios estiverem errados, corrija-os uma vez pelo app depois
> de aplicar o script novo.

### 4. A tela de Chamada também zerava os dízimos

O bug 1 tinha uma segunda instância, na UI. `ChamadaScreen` montava uma `Chamada` **nova**
em vez de partir da existente:

```kotlin
val chamada = Chamada(classeId = cid, data = data, licao = …, oferta = …, visitantes = …)
```

A entidade tem 7 campos de negócio; o formulário preenche 4. `dizimos` não está no
formulário, então nascia `0.0`. Corrigir só a sincronização não bastava: bastava alguém
abrir a chamada para ajustar a lição, e o dízimo ia a zero com carimbo novo — que o sync
então espalhava, corretamente, para todos os aparelhos.

Todos os outros diálogos do app (membro, classe, critério, preço) já usavam
`(inicial ?: Entidade(…)).copy(…)`. A tela de Chamada era a única exceção.

**Corrigido.**

### 5. O backup era uma rede de segurança furada — GRAVE

Auditoria campo a campo: o backup cobre **6 de 10 entidades** e **60% dos campos se
perdem** numa restauração.

**Entidades inteiramente ausentes** do backup — e todas apagadas por `limparTudo()`:
`revistas_precos`, `revistas_entregas`, `criterios_pontuacao`, `pontos_lancamentos`.
Restaurar um backup apaga toda a pontuação e todo o histórico de revistas, sem volta. Pior:
os critérios padrão só nascem dentro da migração 7→8, então o app fica **sem nenhum
critério** depois de uma restauração.

**Campos de sincronização perdidos em 100% das tabelas:** `uid`, `updatedAt` e `deleted`
não são exportados. Na volta, `repo.salvar*` gera um `uid` novo para cada registro. Com o
auto-sync ligado, o efeito é devastador: o push manda a base inteira com uids inéditos, a
planilha **duplica tudo**, o pull traz de volta os registros antigos (cujos uids sumiram) e
o banco local termina com **duas cópias completas**. Quem restaurar um backup num aparelho
sincronizado destrói a planilha e a base de todos os outros celulares.

**`Financeiro.chamadaId` não é exportado**, então o vínculo oferta ↔ chamada morre. Ao
reeditar a chamada o app não encontra o lançamento e cria um segundo — a mesma oferta passa
a ser contada duas vezes.

**E o pior de todos:** não havia validação nenhuma antes de `limparTudo()`. Escolher um
arquivo contendo apenas `{}` apagava o banco inteiro, importava zero registros e exibia
**"Backup restaurado."** Perda total com mensagem de sucesso.

**Corrigido — o backup foi reescrito (formato versão 3):**

- As **10 tabelas** entram no arquivo, campo a campo, incluindo `uid`, `updatedAt`,
  `deleted` e o `Financeiro.chamadaId`.
- **Os ids são preservados em vez de remapeados.** Como a restauração esvazia o banco
  antes de gravar, reusar o id original é seguro e mantém todos os vínculos exatos. Some
  junto o descarte silencioso de registros órfãos.
- Linhas com exclusão lógica também são exportadas, para que a exclusão continue se
  propagando depois da restauração.
- **Validação antes de qualquer destruição:** rejeita arquivo sem `versao`, sem classes,
  de versão futura, ou que seja um despejo da sincronização escolhido por engano.
- **Restauração numa transação única** — falhou no meio, nada é aplicado.
- Fim do falso "sucesso" na exportação e do silêncio quando o arquivo não pode ser lido.
  A mensagem agora diz quantos registros entraram.

### 5. O Apps Script não estava versionado

O README mandava copiar o backend de `appscript/Code.gs` — o arquivo não existia no
repositório. Metade da lógica de dados vivia só dentro da planilha, sem histórico e sem
como reproduzir um bug.

**Corrigido:** o script está em [`appscript/Code.gs`](../appscript/Code.gs), com as
correções acima e uma função de migração.

---

---

## Defeitos na camada de UI

### Perda da chamada em andamento — corrigido

`ChamadaScreen` guardava a classe selecionada por **posição na lista** (`classeIdx`), e a
lista vem ordenada por nome de um Flow vivo. O `SyncManager` roda sozinho a cada 15 minutos
e 2,5 s após qualquer alteração.

Cenário: o professor está lançando "SENHORAS" e já marcou 15 alunos. Outro celular cria a
classe "ADOLESCENTES". O sync chega, a lista reordena, o índice passa a apontar para outra
classe, o `LaunchedEffect` recarrega e executa `marcas.clear()`. As 15 marcações somem e a
tela trocou de classe sem aviso. Se ele tocar em "Finalizar", grava na classe errada.

Eram dois defeitos no mesmo lugar:

1. **Identidade por posição.** Agora a tela guarda `classeId: Long?` e deriva o índice do
   Dropdown a partir dele. Reordenar a lista deixou de mover a seleção. Se a classe atual
   for excluída remotamente, cai para a primeira em vez de apontar para outra em silêncio.
2. **`marcas.clear()` incondicional.** O efeito reconciliava zerando tudo, então qualquer
   reemissão do Room apagava o trabalho em andamento — inclusive quando o outro professor
   salvava a mesma chamada. Agora existe uma lista `tocados` com os alunos cujo chip o
   usuário mexeu: a reconciliação atualiza todos os outros a partir do banco e **respeita o
   que foi tocado**. Limpar de verdade só acontece quando classe ou data mudam, e `tocados`
   é liberado depois de salvar, quando o banco volta a ser a verdade.

### Formulário de chamada sobrevivendo à rotação — corrigido

`classeId`, `data`, `licao`, `oferta`, `marcas`, `tocados`, `visitantesNovos`, `visNome`,
`visTel` e `confirmarExcluir` passaram a `rememberSaveable`. O `AndroidManifest.xml` não
declara `android:configChanges`, então a Activity é recriada em rotação, troca de tema do
sistema e mudança de tamanho de fonte — antes, girar o celular no meio de uma chamada de 25
alunos perdia tudo e voltava a classe para a primeira.

`marcas`, `visitantesNovos` e `tocados` precisaram de `Saver` próprio (mapa e lista de
`Pair` não são Bundle-áveis). No dos visitantes, nome e telefone viram **duas entradas
seguidas** da lista em vez de um campo com separador — qualquer separador escolhido pode
aparecer num nome ou num telefone.

### Reconciliação por chave na lista

`items(alunos)` ganhou `key = { it.id }`. Sem chave, o Compose reaproveita as linhas por
posição: uma reordenação da lista de alunos podia deixar o estado visual de um chip na
linha de outro aluno por um frame.

### Crashes por indexação sem verificação — corrigidos

Três pontos indexavam a lista de classes direto, e ela pode encolher por sync com o diálogo
aberto: `MembrosScreen` (`classes.first()` e `classeIds[classeIdx]`) e `VisitantesScreen`
(`classes[idx]`). **Corrigidos** com `getOrNull` / guarda de lista vazia.

### `collectAsState()` em vez de `collectAsStateWithLifecycle()` — corrigido

A dependência `androidx.lifecycle:lifecycle-runtime-compose` nem estava no Gradle. Como
`collectAsState()` não pausa no `onStop`, os Flows do Room seguiam ativos com o app em
segundo plano e as telas recompunham a cada sync, refazendo todas as agregações fora da
tela — o `SharingStarted.WhileSubscribed(5_000)` dos ViewModels ficava sem efeito prático.

Dependência adicionada e as **38 chamadas** trocadas nas 12 telas mais a `MainActivity`.

### Trabalho pesado na thread principal — corrigido

`RelatoriosViewModel.recarregar()`, `abrirRelatorioDia()` e
`PontuacaoViewModel.recarregarRanking()` rodavam em `viewModelScope` (Main) e faziam um
**N+1 de queries em série** — uma ida ao banco por chamada. Com 4 classes × 13 domingos são
52 consultas sequenciais, mais `groupBy` e `sortedWith`, tudo na UI. Abrir Relatórios
congelava a tela.

- `PresencaDao.listarPorChamadas(ids)` novo, com `Repository.presencasDasChamadas()`
  devolvendo o resultado já agrupado: **uma consulta** no lugar de 52.
- As três funções passaram a `viewModelScope.launch(Dispatchers.Default)`.

> Ainda em aberto: várias telas ordenam e agregam no corpo do composable sem `remember`.
> `FinancasScreen` reagrupa todo o histórico financeiro a cada recomposição e
> `PontuacaoScreen` faz ~100 varreduras de lista a cada toque em um chip. Com a coleta
> agora ciente do ciclo de vida, isso deixou de acontecer com a tela em segundo plano —
> mas continua valendo envolver em `remember`.

### Contagem de visitantes divergente — corrigido

O relatório do dia usava o contador `Chamada.visitantes`, acumulado na tela de Chamada,
enquanto o do trimestre contava linhas da tabela `visitantes`. As duas telas mostravam
números diferentes para o mesmo domingo. Agora as duas leem a tabela.

### Menores — corrigidos

- `SettingsScreen` gravava em disco **a cada tecla** digitada na URL do Apps Script (~90
  escritas ao colar uma URL). O texto passou a viver em estado local, persistido ao sair do
  campo ou no "Done" do teclado.
- `VisitantesScreen` mutava estado durante a composição (`converter = null` no corpo) —
  virou `LaunchedEffect`.
- `RevistasScreen`: `remember` sem chave no diálogo de entrega. Se ele abrisse antes de o
  Room emitir os preços, o índice ficava preso em 0 e salvava a categoria errada. Chaveado
  por `linha` e `categorias`.
- Escopo de corrotina órfão em `EBDApp` lançando uma corrotina vazia. **Removido.**

---

## Problemas menores

- **`SyncApi` / `SyncPayload` (`data/network/SyncService.kt`) é código morto.** Nada
  referencia essas declarações — `SyncEngine` fala com o Apps Script por OkHttp puro. As
  dependências `retrofit` e `converter-gson` podem sair junto.
- **Auto-sync não observava as tabelas de pontuação.** Corrigido em `SyncManager.TABELAS`.
- **`uid` é anulável.** `montarPayloadSync` faz `.put("uid", it.uid)` e, em `JSONObject`,
  `put` com `null` **remove a chave**. Uma linha sem `uid` nunca faz round-trip.
- **`remember` sem chave no diálogo de membro** (`MembrosScreen.kt:105-111`): se o mesmo
  diálogo for reaproveitado para outro aluno sem sair da composição, os campos mantêm o
  valor anterior.
- **`nao_excluido_` trata "Date na coluna Excluído" como não-excluído.** É um contorno para
  um deslocamento de coluna que já aconteceu — sintoma, não causa.
- **Zero testes.** Os bugs 1 e 2 morreriam num teste de round-trip.
- **Toolchain defasada** — AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10, Room 2.6.1.

---

## Direção proposta

### Dados

Com as correções acima, o merge por `uid` + `updatedAt` passa a convergir de verdade. Mas a
planilha continua sendo um banco sem tipo, e o modelo "substitui a linha inteira" continua
frágil: qualquer campo novo esquecido em uma das pontas vira perda de dado silenciosa
(foi exatamente o caso dos dízimos).

Duas melhorias que valem, em ordem:

1. **Completar o backup local** — todas as tabelas, esquema versionado, reimportação sem
   perda, e um backup automático com rotação além do export manual. É a rede de segurança
   que hoje não existe.
2. **Teste de round-trip** — serializar → aplicar → comparar. Três linhas por tabela, e
   nenhum campo esquecido passa de novo.

Se um dia mais de um celular precisar compartilhar dados com garantia, a resposta é um
backend com tipo (Firestore ou Supabase, ambos com camada gratuita). Não é urgente.

### Ranking público

Boa notícia: o endpoint `?ranking=1&ano=&trimestre=` **já existe** e devolve o ranking
calculado em JSON. Não precisa mexer no app.

O caminho enxuto é publicar `ranking-impressao.html` no **GitHub Pages** e fazer a página
ler esse endpoint. Dois detalhes a resolver:

- Hoje a URL `/exec` é colada à mão e guardada em `localStorage` — precisa vir embutida ou
  de um arquivo de configuração ao lado da página.
- Publicar a URL `/exec` num site público expõe **todos** os dados da planilha, inclusive
  telefones e datas de nascimento, a quem descobrir o endereço. O endpoint de ranking devolve
  só nome, classe, pontos e faltas — mas `doGet` sem parâmetro devolve tudo.

Por isso a recomendação é a inversa: **um workflow do GitHub Actions busca o ranking em
horário agendado, grava um `ranking.json` no repositório e o Pages serve os dois arquivos
estáticos.** A URL `/exec` fica só no segredo do Actions, o público nunca a vê, e o F5 do
visitante pega a última versão publicada. Sem backend, sem custo, sem exposição.

GitHub Actions não "roda" HTML — mas o Pages **serve** HTML estático de graça, e é disso
que a página precisa.

---

---

## Enxugamento do escopo — feito (Room v9, app 3.2)

Decisão do responsável em 21/08, executada em seguida. Todas as três mudanças são
**quebra de contrato de sincronização**: app e Apps Script precisam subir juntos.

- **Finanças removida.** A tela era ilustrativa e nunca entrou em uso. Saíram a tela, a
  entidade `Financeiro`, o DAO, o `FinancasViewModel` e a aba da planilha. A oferta
  continua onde sempre esteve de verdade, em `chamadas.oferta` — a tabela `financeiro` só
  guardava uma cópia derivada dela, e era essa cópia que duplicava a receita quando o
  vínculo chamada→lançamento se perdia (item 5).
- **`chamadas.dizimos` removido.** O app não gerencia dízimo, e não havia nem campo na
  interface que escrevesse nele. Tirar a coluna exigiu **recriar a tabela**: o SQLite do
  minSdk 26 não tem `ALTER TABLE DROP COLUMN` (só a partir do 3.35 / API 34).
- **Revistas virou situação por trimestre.** `revistas_precos` e `revistas_entregas` deram
  lugar a `revistas_alunos`, com apenas `temRevista` e `pago`. Categoria e preço existiam
  para alimentar o financeiro, que se foi. As entregas já registradas viram
  `temRevista = 1`; não há como inferir `pago` do que existia, porque o preço gravado era o
  de tabela e não um pagamento.

**Onde a marcação vive agora:** na tela de Membros, dois chips por aluno ("Revista" e
"Pago") mais um seletor de trimestre no topo com o placar da classe filtrada. Marcar "Pago"
implica "tem revista" — pagar pelo que não recebeu não é um estado válido.

**Barra inferior:** "Finanças" era um dos cinco itens. **Pontuação** entrou no lugar — é a
tela usada toda semana e antes só se chegava nela por atalho no Dashboard.

**Na planilha:** rode `MIGRAR_PARA_V3`. As abas `financeiro`, `revistasPrecos` e
`revistasEntregas` são **renomeadas** para `... (arquivada)`, não apagadas — o histórico
continua consultável, o script só para de tocar nelas.

> O formato de backup subiu para a **versão 4**. Arquivos v3 e anteriores continuam
> restaurando: os campos que saíram são simplesmente ignorados.

### Instalação limpa não semeava os critérios — corrigido

O builder do Room não tinha `addCallback`, e os 15 critérios padrão eram inseridos apenas
dentro da `MIGRATION_7_8`. Numa instalação nova o Room cria o esquema direto na versão
atual e **não roda migração nenhuma** — então o app nascia com a tela de Pontuação vazia
até alguém configurar a nuvem e baixar. Importa muito porque o fluxo de atualização nos
aparelhos secundários é desinstalar e reinstalar.

A semente virou a função `semearCriterios(db)`, chamada tanto pela migração quanto por um
`onCreate` novo no builder.

Ambas exigem uma migração de esquema (Room v9) tocando entidades, DAOs, telas, navegação,
o `SCHEMA` do Apps Script e o payload de sync. Não foi feito nesta sessão — é trabalho que
precisa de compilador à mão.

---

## Estado do build

**Gradle não roda nesta máquina pelo terminal.** Toda invocação falha com
`java.io.IOException: Unable to establish loopback connection` — o daemon do Gradle precisa
de uma conexão de loopback e o ambiente a bloqueia, dentro ou fora do sandbox. O JDK
(`Android Studio/jbr`) e o SDK (compileSdk 35) estão instalados e o `local.properties` foi
criado.

**Consequência: nenhuma alteração desta sessão passou por compilador.** Antes de gerar APK,
abra o projeto no Android Studio e faça Build → Make Project.

---

## Ordem de ataque

1. **Compilar no Android Studio** e corrigir o que aparecer. Nada abaixo faz sentido antes.
2. **Testar a migração v9 e o backup na mão:** instalar por cima com dados reais e
   conferir que a chamada, a pontuação e as revistas do trimestre sobreviveram; exportar o
   `.json` e confirmar `"versao": 4` com `uid` em cada registro; restaurar e comparar. São
   as duas mudanças mais extensas, e as que mais precisam de olho humano.
3. Testes de round-trip de sincronização e de backup — teriam pegado os dízimos sozinhos.
4. Publicar o ranking via GitHub Pages + Actions (já montado em
   [`.github/workflows/publicar-ranking.yml`](../.github/workflows/publicar-ranking.yml) e
   [`site/index.html`](../site/index.html); falta configurar o segredo e ligar o Pages).
5. Envolver em `remember` as agregações que ainda rodam no corpo do composable
   (`PontuacaoScreen`), e atualizar a toolchain (AGP 8.7.3, Kotlin
   2.0.21, Compose BOM 2024.10 — todos defasados).
