/**
 * EBD Controle — Backend de sincronização (Google Apps Script)
 * --------------------------------------------------------------
 * Uma aba por "tabela". Sincronização por 'uid' com regra
 * "última alteração vence" (updatedAt). Exclusões viram deleted = 1.
 *
 * Abas: classes, alunos, chamadas, presencas, revistasAlunos,
 *       criterios, pontos, visitantes.
 *
 * doGet:
 *   - sem parâmetros            -> devolve TODAS as tabelas (usado pelo app)
 *   - ?ranking=1&ano=&trimestre -> devolve o ranking já calculado
 *     (todos os alunos ativos, mesmo com 0 pontos) para a folha de impressão.
 *
 * PARA APLICAR MUDANÇAS (siga nesta ordem):
 * 0) Faça uma cópia da planilha (Arquivo -> Fazer uma cópia). Rede de segurança.
 *    Feche o app nos celulares para nenhuma sincronização correr junto.
 * 1) Cole este código no editor e salve.
 * 2) Implantar -> Gerenciar implantações -> Editar (lápis) -> Versão "Nova versão"
 *    -> Implantar. A URL /exec continua a mesma; não mexer no app.
 * 3) SÓ ENTÃO rode MIGRAR_PARA_V3, uma vez. Não apaga dado das abas que ficam.
 *
 *    A ordem importa: a migração converte updatedAt de data para número, mas
 *    quem atende o /exec é a versão IMPLANTADA. Se a implantação ainda for a v1,
 *    a primeira sincronização depois da migração grava tudo de volta como data e
 *    desfaz o trabalho.
 *
 * ---------------------------------------------------------------------------
 * MUDANÇAS DA V2 (correções de perda de dados) — ver docs/ANALISE.md
 *
 * 1. `updatedAt` deixou de ser célula de DATA e passou a ser NÚMERO puro.
 *    Como célula de data, o valor virava um serial decimal do Sheets e perdia
 *    precisão de milissegundos no ida-e-volta. Como o app só aceita registro
 *    remoto quando `remoto > local` (estritamente), um timestamp que voltava
 *    arredondado para baixo fazia o app **descartar a atualização** — e em
 *    seguida reenviar a própria versão antiga por cima da planilha.
 *    Esse é o motivo de dado editado num celular "sumir" ou voltar atrás.
 *
 * 2. `incoming >= atual` virou `incoming > atual`, igualando o critério do app.
 *    Com `>=`, empate de timestamp era resolvido a favor de quem sincronizou
 *    por último — mesmo que essa versão fosse a mais pobre. O app usa `>`.
 *    Dois critérios diferentes para a mesma regra nunca convergem.
 *
 * 3. `financeiro`, `revistasPrecos` e `revistasEntregas` saíram do esquema, e
 *    `revistasAlunos` entrou no lugar — só "tem revista" e "pagou" por trimestre.
 *    A coluna `dizimos` de `chamadas` também saiu: o app não gerencia dízimo.
 *    As abas que saíram são renomeadas, não apagadas.
 * ---------------------------------------------------------------------------
 */

/* ===================== ESQUEMA DAS ABAS ===================== */
/**
 * `datas` lista SOMENTE campos que representam uma data para o usuário ler.
 * `updatedAt` ficou deliberadamente de fora: é um carimbo de precisão em
 * milissegundos e precisa continuar número. Ver MUDANÇAS DA V2, item 1.
 */
var SCHEMA = {
  classes: {
    campos:  ['uid','nome','faixaEtaria','professores','updatedAt','deleted'],
    titulos: ['ID','Nome da Classe','Faixa Etária','Professores','Atualizado em','Excluído'],
    datas:   {},
    apoio:   []
  },
  alunos: {
    campos:  ['uid','classeUid','nome','dataNascimento','telefone','cargo','ativo','especial','updatedAt','deleted'],
    titulos: ['ID','ID da Classe','Nome','Data de Nascimento','Telefone','Cargo','Ativo','Especial','Atualizado em','Excluído'],
    datas:   { dataNascimento: true },
    apoio:   [
      { titulo: 'Classe', lookupCol: 'classeUid', lookupAba: 'classes', lookupChave: 'uid', lookupValor: 'nome' }
    ]
  },
  chamadas: {
    campos:  ['uid','classeUid','data','licao','oferta','visitantes','updatedAt','deleted'],
    titulos: ['ID','ID da Classe','Data da Aula','Lição','Oferta (R$)','Visitantes','Atualizado em','Excluído'],
    datas:   { data: true },
    apoio:   [
      { titulo: 'Classe', lookupCol: 'classeUid', lookupAba: 'classes', lookupChave: 'uid', lookupValor: 'nome' }
    ]
  },
  presencas: {
    campos:  ['uid','chamadaUid','alunoUid','presente','biblia','revista','updatedAt','deleted'],
    titulos: ['ID','ID da Chamada','ID do Aluno','Presente','Bíblia','Revista','Atualizado em','Excluído'],
    datas:   {},
    apoio:   [
      { titulo: 'Aluno', lookupCol: 'alunoUid', lookupAba: 'alunos', lookupChave: 'uid', lookupValor: 'nome' }
    ]
  },
  revistasAlunos: {
    campos:  ['uid','alunoUid','ano','trimestre','temRevista','pago','updatedAt','deleted'],
    titulos: ['ID','ID do Aluno','Ano','Trimestre','Tem Revista','Pago','Atualizado em','Excluído'],
    datas:   {},
    apoio:   [
      { titulo: 'Aluno', lookupCol: 'alunoUid', lookupAba: 'alunos', lookupChave: 'uid', lookupValor: 'nome' }
    ]
  },
  criterios: {
    campos:  ['uid','nome','pontos','grupo','porQuantidade','ordem','ativo','updatedAt','deleted'],
    titulos: ['ID','Nome','Pontos','Grupo','Por Quantidade','Ordem','Ativo','Atualizado em','Excluído'],
    datas:   {},
    apoio:   []
  },
  pontos: {
    campos:  ['uid','alunoUid','criterioUid','data','pontos','quantidade','updatedAt','deleted'],
    titulos: ['ID','ID do Aluno','ID do Critério','Data','Pontos','Quantidade','Atualizado em','Excluído'],
    datas:   { data: true },
    apoio:   [
      { titulo: 'Aluno',    lookupCol: 'alunoUid',    lookupAba: 'alunos',    lookupChave: 'uid', lookupValor: 'nome' },
      { titulo: 'Critério', lookupCol: 'criterioUid', lookupAba: 'criterios', lookupChave: 'uid', lookupValor: 'nome' }
    ]
  },
  visitantes: {
    campos:  ['uid','nome','telefone','data','classeUid','observacao','convertido','updatedAt','deleted'],
    titulos: ['ID','Nome','Telefone','Data da Visita','ID da Classe','Observação','Convertido','Atualizado em','Excluído'],
    datas:   { data: true },
    apoio:   [
      { titulo: 'Classe', lookupCol: 'classeUid', lookupAba: 'classes', lookupChave: 'uid', lookupValor: 'nome' }
    ]
  }
};

var FORMATO_SO_DATA = 'dd/mm/yyyy';
var FORMATO_CARIMBO = '0';         // updatedAt: inteiro cru, sem separador de milhar
var FORMATO_TEXTO   = 'General';   // o resto: sem formatação, para 0/1 continuar 0/1

/* ===================== ENDPOINTS ===================== */

function doGet(e) {
  // Modo ranking (folha de impressão): ?ranking=1&ano=2026&trimestre=3
  if (e && e.parameter && e.parameter.ranking) {
    var ano = Number(e.parameter.ano) || (new Date()).getFullYear();
    var tri = Number(e.parameter.trimestre) || trimestreAtual_();
    return json_(calcularRanking_(ano, tri));
  }
  // Modo padrão (app): devolve tudo.
  return json_(lerTudo_());
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    CACHE_LOOKUP_ = {}; // zera cache de lookups para esta execução
    var body = JSON.parse(e.postData.contents);
    Object.keys(SCHEMA).forEach(function (name) {
      if (body && Array.isArray(body[name])) upsertAba_(name, body[name]);
    });
    return json_({ ok: true, dados: lerTudo_() });
  } catch (err) {
    return json_({ ok: false, erro: String(err) });
  } finally {
    lock.releaseLock();
  }
}

/* ===================== LEITURA ===================== */

function lerTudo_() {
  var out = {};
  Object.keys(SCHEMA).forEach(function (name) { out[name] = lerAba_(name); });
  return out;
}

function lerAba_(name) {
  var sh = aba_(name);
  var cols = SCHEMA[name].campos;
  var lastRow = sh.getLastRow();
  if (lastRow < 2) return [];

  var valores = sh.getRange(2, 1, lastRow - 1, cols.length).getValues();
  var out = [];
  valores.forEach(function (r) {
    if (r[0] === '' || r[0] == null) return;
    var o = {};
    cols.forEach(function (c, i) {
      var v = r[i];
      // Linhas gravadas pela v1 ainda trazem updatedAt como Date; converte na leitura
      // para que a migração possa ser feita sem parar a sincronização.
      if (v instanceof Date) v = v.getTime();
      o[c] = v;
    });
    out.push(o);
  });
  return out;
}

/* ===================== GRAVAÇÃO ===================== */

function upsertAba_(name, registros) {
  var sh = aba_(name);
  var def = SCHEMA[name];
  var cols = def.campos;
  var idxUpd = cols.indexOf('updatedAt');
  var lastRow = sh.getLastRow();

  // 1) Lê a aba inteira UMA vez para a memória.
  var matriz = [];
  var mapa = {}; // uid -> índice na matriz
  if (lastRow >= 2) {
    matriz = sh.getRange(2, 1, lastRow - 1, cols.length).getValues();
    matriz.forEach(function (r, i) {
      if (r[0]) mapa[r[0]] = i;
    });
  }

  // 2) Aplica todos os registros recebidos em memória (sem tocar na planilha).
  var mudou = false;
  registros.forEach(function (reg) {
    if (!reg.uid) return;
    var linhaArr = cols.map(function (c) {
      var v = reg[c];
      if (v === undefined || v === null) return '';
      if (typeof v === 'boolean') return v ? 1 : 0;
      if (def.datas.hasOwnProperty(c)) {
        var n = Number(v);
        return n > 0 ? new Date(n) : '';
      }
      return v;
    });
    var incoming = Number(reg.updatedAt) || 0;
    if (mapa.hasOwnProperty(reg.uid)) {
      var idx = mapa[reg.uid];
      var atualU = matriz[idx][idxUpd];
      if (atualU instanceof Date) atualU = atualU.getTime();
      // Estritamente maior, igual ao critério do app. Ver MUDANÇAS DA V2, item 2.
      if (incoming > (Number(atualU) || 0)) { matriz[idx] = linhaArr; mudou = true; }
    } else {
      mapa[reg.uid] = matriz.length;
      matriz.push(linhaArr);
      mudou = true;
    }
  });

  // 3) Se nada mudou, não escreve nem reprocessa apoio/formatos (economia enorme).
  if (!mudou) return;

  // 4) Grava a matriz inteira de uma só vez.
  if (matriz.length) {
    sh.getRange(2, 1, matriz.length, cols.length).setValues(matriz);
  }

  // Invalida o cache de lookups desta aba (os nomes podem ter mudado).
  if (CACHE_LOOKUP_) {
    Object.keys(CACHE_LOOKUP_).forEach(function (k) {
      if (k.indexOf(name + '|') === 0) delete CACHE_LOOKUP_[k];
    });
  }

  aplicarFormatoData_(sh, name);
  preencherApoio_(sh, name);
}

/* ===================== ABA + CABEÇALHO ===================== */

function aba_(name) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(name);
  if (!sh) sh = ss.insertSheet(name);
  if (sh.getLastRow() === 0) escreverCabecalho_(sh, name);
  return sh;
}

function escreverCabecalho_(sh, name) {
  var def = SCHEMA[name];
  var titulos = def.titulos.slice();
  (def.apoio || []).forEach(function (a) { titulos.push(a.titulo); });

  sh.getRange(1, 1, 1, titulos.length).setValues([titulos]);
  sh.getRange(1, 1, 1, titulos.length)
    .setFontWeight('bold')
    .setBackground('#1e6b41')
    .setFontColor('#ffffff');
  sh.setFrozenRows(1);
}

/* ===================== FORMATO DE DATA ===================== */

/**
 * Formata as colunas — e, tão importante quanto, DESformata as que não são data.
 *
 * Uma coluna formatada como data faz o `getValues()` devolver um objeto Date mesmo
 * quando a célula guarda 0 ou 1. Foi o que aconteceu com `especial`: ela herdou a
 * formatação de data de quando o esquema mudou e as colunas se deslocaram, e desde
 * então o app recebia um carimbo de 1899 no lugar do booleano — a marcação de aluno
 * especial simplesmente não voltava da nuvem.
 *
 * Por isso o padrão é reposto em TODA coluna a cada gravação: formatação errada aqui
 * não é cosmética, é corrupção de tipo.
 */
function aplicarFormatoData_(sh, name) {
  var def = SCHEMA[name];
  var lastRow = sh.getLastRow();
  if (lastRow < 2) return;

  var nLinhas = lastRow - 1;
  sh.getRange(2, 1, nLinhas, def.campos.length).setNumberFormat(FORMATO_TEXTO);

  Object.keys(def.datas).forEach(function (campo) {
    var col = def.campos.indexOf(campo) + 1;
    if (col < 1) return;
    sh.getRange(2, col, nLinhas, 1).setNumberFormat(FORMATO_SO_DATA);
  });

  var colUpd = def.campos.indexOf('updatedAt') + 1;
  if (colUpd > 0) sh.getRange(2, colUpd, nLinhas, 1).setNumberFormat(FORMATO_CARIMBO);
}

/* ===================== COLUNAS DE APOIO (lookup por VALOR) ===================== */

/**
 * Monta um mapa { chave -> valor } lendo a aba de destino.
 * Ex.: mapaLookup_('classes', 'uid', 'nome') => { "<uid-da-classe>": "JOVENS", ... }
 */
function mapaLookup_(abaNome, campoChave, campoValor) {
  // Cache por execução: evita reler a mesma aba de lookup várias vezes
  // (ex.: 'alunos' é consultada por presencas, revistasEntregas e pontos).
  var chaveCache = abaNome + '|' + campoChave + '|' + campoValor;
  if (!CACHE_LOOKUP_) CACHE_LOOKUP_ = {};
  if (CACHE_LOOKUP_.hasOwnProperty(chaveCache)) return CACHE_LOOKUP_[chaveCache];

  var sh = aba_(abaNome);
  var cols = SCHEMA[abaNome].campos;
  var iChave = cols.indexOf(campoChave);
  var iValor = cols.indexOf(campoValor);
  var mapa = {};
  if (iChave < 0 || iValor < 0) { CACHE_LOOKUP_[chaveCache] = mapa; return mapa; }

  var lastRow = sh.getLastRow();
  if (lastRow < 2) { CACHE_LOOKUP_[chaveCache] = mapa; return mapa; }

  var valores = sh.getRange(2, 1, lastRow - 1, cols.length).getValues();
  valores.forEach(function (r) {
    var k = r[iChave];
    if (k === '' || k == null) return;
    mapa[k] = r[iValor];
  });
  CACHE_LOOKUP_[chaveCache] = mapa;
  return mapa;
}
var CACHE_LOOKUP_ = null;

/**
 * Preenche cada coluna de apoio ("Classe" / "Aluno" / "Critério") com TEXTO
 * resolvido em JavaScript — nada de fórmula. Assim o problema de vírgula x
 * ponto-e-vírgula (#ERROR!) deixa de existir, e a correção não "volta" na
 * próxima sincronização. Os nomes são recalculados a cada gravação, então
 * continuam sempre atualizados.
 */
function preencherApoio_(sh, name) {
  var def = SCHEMA[name];
  if (!def.apoio || !def.apoio.length) return;

  var base = def.campos.length; // nº de colunas que o app usa nativamente
  var lastRow = sh.getLastRow();

  def.apoio.forEach(function (a, i) {
    var colApoio = base + i + 1;
    var iChaveLocal = def.campos.indexOf(a.lookupCol);

    // Sempre limpa a coluna de apoio: remove fórmulas antigas (inclusive as que
    // davam #ERROR!) e qualquer valor velho.
    if (lastRow >= 2) {
      sh.getRange(2, colApoio, lastRow - 1, 1).clearContent();
    }
    if (lastRow < 2 || iChaveLocal < 0) return;

    // Mapa chave -> nome, lido da aba de destino (ex.: uid da classe -> nome).
    var mapa = mapaLookup_(a.lookupAba, a.lookupChave, a.lookupValor);

    // Lê a coluna-chave local e resolve cada nome.
    var chaves = sh.getRange(2, iChaveLocal + 1, lastRow - 1, 1).getValues();
    var saida = chaves.map(function (r) {
      var k = r[0];
      if (k === '' || k == null) return [''];
      var v = mapa[k];
      return [v == null ? '' : v];
    });

    sh.getRange(2, colApoio, saida.length, 1).setValues(saida);
  });
}

/* ===================== UTIL ===================== */

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/* ===================== SETUP / RESET / MIGRAÇÃO ===================== */

function RESETAR_E_CONFIGURAR() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  Object.keys(SCHEMA).forEach(function (name) {
    var sh = ss.getSheetByName(name);
    if (sh) ss.deleteSheet(sh);
    sh = ss.insertSheet(name);
    escreverCabecalho_(sh, name);
  });
  ss.toast('Abas recriadas. Publique e sincronize o app.', 'EBD Sync', 6);
}

/**
 * Migração para o esquema atual. Rodar UMA vez após colar este código.
 *
 * - Arquiva as abas que saíram do esquema (finanças e revistas antigas).
 * - Reescreve os cabeçalhos.
 * - Converte `updatedAt` de célula de data para número puro.
 * - Limpa a formatação de data das colunas que não são data, e reaplica as
 *   colunas de apoio.
 */
function MIGRAR_PARA_V3() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  // Abas que saíram do app. Renomeadas em vez de apagadas: se você ainda quiser
  // consultar o histórico de finanças ou de entregas de revista, ele continua aí —
  // o script simplesmente para de tocar nelas.
  ['financeiro', 'revistasPrecos', 'revistasEntregas'].forEach(function (nome) {
    var sh = ss.getSheetByName(nome);
    if (sh) sh.setName(nome + ' (arquivada)');
  });

  Object.keys(SCHEMA).forEach(function (name) {
    var sh = aba_(name);
    escreverCabecalho_(sh, name);

    var def = SCHEMA[name];
    var lastRow = sh.getLastRow();
    if (lastRow < 2) { preencherApoio_(sh, name); return; }

    var colUpd = def.campos.indexOf('updatedAt') + 1;

    // Tira a formatação de data das colunas que NÃO são data, antes de qualquer
    // leitura. Enquanto o formato estiver lá, getValues() devolve Date no lugar do
    // 0/1 guardado — foi o que quebrou a coluna `especial` em `alunos`. Sem o
    // formato, o valor volta a ser lido como número, sem precisar converter nada.
    //
    // As colunas que de fato guardam data (e o updatedAt, ainda em formato de data
    // neste ponto) ficam de fora: limpar o formato delas faria getValues() devolver
    // o serial de dias do Sheets, e a conversão logo abaixo interpretaria esse
    // número como milissegundos — jogando toda data para 1970.
    for (var c = 1; c <= def.campos.length; c++) {
      var campo = def.campos[c - 1];
      if (def.datas.hasOwnProperty(campo) || c === colUpd) continue;
      sh.getRange(2, c, lastRow - 1, 1).setNumberFormat(FORMATO_TEXTO);
    }
    SpreadsheetApp.flush();
    if (colUpd > 0) {
      var rng = sh.getRange(2, colUpd, lastRow - 1, 1);
      var novos = rng.getValues().map(function (row) {
        var v = row[0];
        if (v instanceof Date) return [v.getTime()];
        var n = Number(v);
        return [n > 0 ? n : ''];
      });
      rng.setValues(novos);
    }

    // Garante que os campos que SÃO data continuem como Date de verdade.
    Object.keys(def.datas).forEach(function (campo) {
      var col = def.campos.indexOf(campo) + 1;
      if (col < 1) return;
      var r = sh.getRange(2, col, lastRow - 1, 1);
      var vals = r.getValues().map(function (row) {
        var v = row[0];
        if (v instanceof Date) return [v];
        var n = Number(v);
        return n > 0 ? [new Date(n)] : [''];
      });
      r.setValues(vals);
    });

    aplicarFormatoData_(sh, name);
    preencherApoio_(sh, name);
  });

  ss.toast('Migração concluída. Abas de finanças e revistas antigas foram arquivadas.', 'EBD Sync', 6);
}

/* ===================== RANKING PARA IMPRESSÃO ===================== */

/** Trimestre atual (1..4) a partir do mês de hoje. */
function trimestreAtual_() {
  var m = (new Date()).getMonth(); // 0..11
  return Math.floor(m / 3) + 1;
}

/** Início (inclusivo) e fim (exclusivo) do trimestre, em ms. */
function faixaTrimestre_(ano, tri) {
  var mesIni = (tri - 1) * 3;          // 0,3,6,9
  var ini = new Date(ano, mesIni, 1).getTime();
  var fim = new Date(ano, mesIni + 3, 1).getTime();
  return { ini: ini, fim: fim };
}

/**
 * Calcula o ranking do trimestre.
 * Retorna: { ano, trimestre, geradoEm, total, linhas:[{pos,nome,classe,pontos,faltas,especial}] }
 * Inclui TODOS os alunos ativos, mesmo com 0 pontos.
 *
 * Faltas são contadas POR DOMINGO, não por linha: quem consta na lista de
 * presença de mais de uma classe no mesmo dia (ex.: professor que também
 * aparece na classe que leciona) conta como presente se esteve presente em
 * QUALQUER uma delas.
 */
function calcularRanking_(ano, tri) {
  var faixa = faixaTrimestre_(ano, tri);

  var classes   = lerAba_('classes');
  var alunos    = lerAba_('alunos');
  var pontos    = lerAba_('pontos');
  var chamadas  = lerAba_('chamadas');
  var presencas = lerAba_('presencas');

  var nomeClasse = {};
  classes.forEach(function (c) { if (nao_excluido_(c)) nomeClasse[c.uid] = c.nome; });

  // Soma de pontos por aluno no período.
  var pontosPorAluno = {};
  pontos.forEach(function (p) {
    if (!nao_excluido_(p)) return;
    var d = Number(p.data) || 0;
    if (d < faixa.ini || d >= faixa.fim) return;
    pontosPorAluno[p.alunoUid] = (pontosPorAluno[p.alunoUid] || 0) + (Number(p.pontos) || 0);
  });

  // Mapa chamadaUid -> data (só chamadas do período).
  var dataChamada = {};
  chamadas.forEach(function (c) {
    if (!nao_excluido_(c)) return;
    var d = Number(c.data) || 0;
    if (d >= faixa.ini && d < faixa.fim) dataChamada[c.uid] = d;
  });

  // Presença POR DIA: presente em qualquer chamada do dia = presente no dia.
  var presencaPorDia = {}; // alunoUid -> { data(ms) -> true/false }
  presencas.forEach(function (pr) {
    if (!nao_excluido_(pr)) return;
    var d = dataChamada[pr.chamadaUid];
    if (!d) return;
    var m = presencaPorDia[pr.alunoUid] || (presencaPorDia[pr.alunoUid] = {});
    var presente = Number(pr.presente) === 1;
    m[d] = m[d] === true ? true : presente; // qualquer presença no dia vence
  });

  // Faltas = dias em que a pessoa constava em alguma chamada e não veio a nenhuma.
  var faltasPorAluno = {};
  Object.keys(presencaPorDia).forEach(function (uid) {
    var m = presencaPorDia[uid], f = 0;
    Object.keys(m).forEach(function (d) { if (m[d] !== true) f++; });
    faltasPorAluno[uid] = f;
  });

  // Monta linhas para TODOS os alunos ativos (mesmo com 0 pontos).
  var linhas = [];
  alunos.forEach(function (a) {
    if (!nao_excluido_(a)) return;
    if (Number(a.ativo) !== 1) return;
    linhas.push({
      nome: a.nome,
      classe: nomeClasse[a.classeUid] || '',
      pontos: pontosPorAluno[a.uid] || 0,
      faltas: faltasPorAluno[a.uid] || 0,
      especial: Number(a.especial) === 1
    });
  });

  // Ordena: mais pontos primeiro; empate -> menos faltas; depois nome.
  linhas.sort(function (x, y) {
    if (y.pontos !== x.pontos) return y.pontos - x.pontos;
    if (x.faltas !== y.faltas) return x.faltas - y.faltas;
    return x.nome.localeCompare(y.nome);
  });
  linhas.forEach(function (l, i) { l.pos = i + 1; });

  return {
    ano: ano,
    trimestre: tri,
    geradoEm: (new Date()).getTime(),
    total: linhas.length,
    linhas: linhas
  };
}

/** true se o registro NÃO está excluído (deleted != 1 e não é data). */
function nao_excluido_(o) {
  var d = o.deleted;
  if (d instanceof Date) return true;   // data indevida => trata como não excluído
  return Number(d) !== 1;
}
