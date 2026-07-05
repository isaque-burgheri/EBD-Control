# Sistema de Pontuação — EBD Control

Resumo do que foi implementado e do que você precisa conferir antes de publicar.

## Como aplicar o patch

Na raiz do repositório (`EBD-Control/`):

```bash
git checkout -b feature/pontuacao
git apply pontuacao-ebd.patch    # ou: git am se preferir commit pronto
```

Depois abra no Android Studio e rode `./gradlew assembleDebug`.

## Arquitetura (segue os padrões que já existiam)

**Decisão central:** em vez de campos fixos, um **catálogo de critérios editável**.
Você cria/edita "Presença", "Alimento café", "Arroz/óleo" e os valores pela tela —
sem nunca mexer no código.

Duas tabelas novas + uma flag:

- `criterios_pontuacao` — o que vale ponto (nome, pontos, grupo, porQuantidade).
  Grupos: `REGULAR`, `ESPECIAL` (inclusão), `CAFE` (fixo), `CESTA` (tabelado por unidade).
- `pontos_lancamentos` — cada ponto marcado (aluno + critério + data + pontos + qtd).
  `uid` determinístico `alunoUid:criterioUid:data` — nunca duplica no sync; marcar de
  novo o mesmo critério no mesmo dia vira toggle/atualização.
- `alunos.especial` — marca o aluno de inclusão (toggle no cadastro de Membros).

Ranking e relatório são **calculados** a partir dos lançamentos (não viram tabela),
igual ao relatório de trimestre que já existe.

## Telas

- **Pontuação → aba "Marcar pontos":** classe + data, cada aluno com chips de toque
  rápido. Aluno especial vê automaticamente o grupo adaptado. Alimentos usam stepper
  −/+ (por unidade). Total do aluno atualiza na hora.
- **Pontuação → aba "Ranking":** pódio do trimestre (ouro/prata/bronze), filtro por
  classe, **desempate por menos faltas** (contadas das chamadas).
- **Critérios** (botão flutuante): cria/edita/exclui critérios e valores.
- Atalho 🏆 no Dashboard leva para a tela.

## Migração de banco

Versão 7 → 8, migração `MIGRATION_7_8` incremental (não apaga dados). Já pré-carrega
um conjunto de critérios padrão (presença, pontualidade, café 15 pts, cesta tabelada
5/15/20). Instalações com sync recebem os critérios da nuvem e mesclam por `uid`.

## ⚠️ Ação necessária no Google Apps Script (sync)

O app agora envia dois blocos novos no payload: `criterios` e `pontos`, e o aluno
ganhou o campo `especial`. O backend do Apps Script precisa:

1. Ter abas/colunas para `criterios` e `pontos` (mesmo padrão das outras: uma coluna
   por campo do JSON, incluindo `uid`, `updatedAt`, `deleted`).
2. Adicionar a coluna `especial` na aba de alunos.

Sem isso, o sync continua funcionando para o resto, mas os pontos ficam só no
celular (não sobem para a planilha nem se espalham entre aparelhos).

Campos enviados:
- `criterios`: uid, nome, pontos, grupo, porQuantidade, ordem, ativo, updatedAt, deleted
- `pontos`: uid, alunoUid, criterioUid, data, pontos, quantidade, updatedAt, deleted

## Pontos que NÃO pude validar aqui

Não tenho o Android SDK neste ambiente, então **não rodei o build**. Fiz:
- checagem de que todos os métodos de Repository/DAO chamados existem;
- checagem de simetria payload↔apply do sync;
- balanceamento de chaves/parênteses.

Confira no Android Studio, em especial:
- `FlowRow` (usado na tela) — no Compose BOM 2024.10.01 é estável; o import vem de
  `androidx.compose.foundation.layout.*` (já incluído). O `@OptIn(ExperimentalLayoutApi)`
  é inofensivo se sobrar.

## Ideias suas que ficaram como configuração (não hard-coded), de propósito

Desempate avançado (quiz, desafio da espada, sorteio cego), premiação e pontuação de
professores não viraram código fixo — o modelo de critérios editáveis já cobre a
pontuação; o resto é decisão operacional da secretaria/diretoria. Se quiser, dá para
adicionar depois: um grupo `PROFESSOR` de critérios e uma aba de ranking de professores
sairiam quase de graça sobre o que já existe.
