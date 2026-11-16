# pac-mAnt e pac-mAnt adaptativo

Trabalho final de Pesquisa Operacional (UFF). Replicação do pac-mAnt de
Recio, Martín, Estébanez e Sáez, *AntBot: Ant Colonies for Video Games* (IEEE TCIAIG, v. 4, n. 4, 2012),
uma versão adaptativa (A1 e A2), a calibração dos parâmetros por busca local iterada (ILS) e um
teste de replicação com o preset manual do artigo.

- **Autoria:** Jean Marins do Lago Cunha
- **Docente:** Luiz Satoru Ochi
- **Disciplina:** Pesquisa Operacional, Universidade Federal Fluminense (UFF)
- **Relatório:** <https://docs.google.com/document/d/1UHgNxvgwv10AV-z8E-uvmsnrHT6Qr2Brm5DNLXflE3Y/edit?usp=sharing>

## Resultados principais

Pontuação contra `Legacy`, 100 jogos por configuração.

| Configuração | Agente | Sementes | Pontuação (média ± DP) |
|---|---|---|---|
| Artigo (Tabela III, preset manual) | — | — | 36.031 ± 11.819 |
| Preset manual, reinício do feromônio a cada tick | `PacmAnt-manual` | 2000–2099 | 31.598 ± 13.907 |
| Preset manual, Algoritmo 1 fiel | `PacmAnt-manual+alg1` | 2000–2099 | 50.316 ± 15.673 |
| Preset evoluído (Fixo) | `PacmAnt-evoluido` | 2000–2099 | 35.459 ± 14.760 |
| A1 | `PacmAnt-A1` | 2000–2099 | 34.165 ± 14.686 |
| A2 | `PacmAnt-A2` | 2000–2099 | 35.074 ± 12.847 |
| Fixo, Algoritmo 1 fiel | `PacmAnt-evoluido+alg1` | 2000–2099 | 53.016 ± 17.488 |
| A2 com feromônio persistente | `PacmAnt-A2+persist` | 2000–2099 | 18.965 ± 7.538 |
| Etapa 4: Fixo+alg1, preset evoluído do artigo | `PacmAnt-evoluido+alg1` | 3000–3099 | 55.013 ± 18.563 |
| Etapa 4: Fixo+alg1, preset do ILS | `PacmAnt-ils+alg1` | 3000–3099 | 53.876 ± 18.287 |
| Etapa 4: A2 normalizado | `PacmAnt-A2+alg1+norm` | 3000–3099 | 55.221 ± 18.175 |

- Nenhuma das propostas (A1, A2, preset do ILS, A2 normalizado) pontuou significativamente mais que o
  agente fixo correspondente em sementes novas (Mann-Whitney com Holm, p ajustado = 1,00 nos quatro pares).
- O maior efeito medido foi o do Algoritmo 1 fiel sobre a configuração padrão: 53.016 contra 35.459 (p < 0,001).
- A comparação com o artigo é só descritiva, porque os jogos dele não estão disponíveis.

Detalhes nas seções 4.4 a 4.9 de `DECISOES.md`; a rodada do preset manual está registrada em
`HIPOTESES.md` e resumida em `resultados/analise_replicacao_manual/resumo.md`.

## Estrutura

| Caminho | Conteúdo |
|---|---|
| `simulador/` | Simulador Ms. Pac-Man vs Ghosts: submódulo git de `kefik/MsPacMan-vs-Ghosts-AI`, sem alterações |
| `src/pacmant/PacmAnt.java` | pac-mAnt (Etapa 1) |
| `src/pacmant/ParametrosAnt.java` | Parâmetros e presets |
| `src/pacmant/PacmAntAdaptive.java` | pac-mAnt adaptativo A1 e A2 (Etapa 2) |
| `src/pacmant/Experimento.java` | Executor sem interface: roda os jogos e grava o CSV |
| `src/pacmant/Cronometrado.java` | Mede o tempo de decisão por tick |
| `src/pacmant/Agentes.java` | Nomes dos agentes e fantasmas |
| `src/pacmant/Diagnostico.java`, `Testes.java` | Ferramentas de diagnóstico e verificação |
| `scripts/rodar.py` | Dispara um processo Java por configuração, em paralelo |
| `scripts/analise.py` | Tabela-resumo, Mann-Whitney (com Holm) e boxplots |
| `scripts/resumo_traco.py` | Resumo dos traços por jogada (teste post hoc e `+norm`) |
| `scripts/ils.py` | Calibração dos parâmetros do `+alg1` por ILS (Etapa 4) |
| `resultados/etapa0`, `etapa1`, `etapa2` | Rodada exploratória: CSVs, uma linha por jogo |
| `resultados/analise/` | Rodada exploratória: tabela-resumo, testes e gráficos PNG |
| `resultados/confirmatoria/` | Rodada confirmatória (sementes 2000–2099), `+alg1` e `+chi05` |
| `resultados/analise_confirmatoria/` | Rodada confirmatória: tabela-resumo, testes e gráficos PNG |
| `resultados/posthoc_chi/` | Traços por jogada (eq. 16) e seu resumo |
| `resultados/ils/` | ILS: `avaliacoes.csv` (uma linha por avaliação), `jogos.csv`, `melhor.json`, `ils.log` |
| `resultados/etapa4/` | Teste final da Etapa 4 (sementes 3000–3099) |
| `resultados/analise_etapa4/` | Teste final da Etapa 4: tabela-resumo, testes e gráficos PNG |
| `resultados/etapa4_traco/` | Traços por jogada do A2+alg1+norm e seu resumo |
| `resultados/replicacao_manual/` | Teste de replicação com o preset manual (sementes 2000–2099) |
| `resultados/analise_replicacao_manual/` | Teste de replicação: tabela-resumo e gráficos PNG |
| `DECISOES.md` | Decisões de implementação e resultados |
| `HIPOTESES.md` | Hipóteses registradas antes de rodar |

## Como reproduzir (Windows)

Clonar com o simulador: `git clone --recurse-submodules https://github.com/jeanlago/pac-mant.git` (ou `git submodule update --init` depois de clonar).

Requisitos: JDK 21 e Python 3.
Se existir um JDK em `tools/jdk-*`, os scripts o usam; senão, usam o `java`/`javac` do PATH.

Ambiente Python (uma vez, na raiz do projeto):

```
python -m venv .venv
.venv\Scripts\python -m pip install numpy scipy pandas matplotlib
```

`rodar.py` e `ils.py` usam só a biblioteca padrão; `analise.py` e `resumo_traco.py` precisam das quatro
bibliotecas. Os resultados foram gerados com Python 3.15.1, numpy 2.5.4, scipy 1.18.1, pandas 3.1.1 e
matplotlib 3.11.2.

Etapas 1 e 2 e rodada confirmatória:

```
powershell -ExecutionPolicy Bypass -File compilar.ps1
.venv\Scripts\python scripts\rodar.py --etapa 1
.venv\Scripts\python scripts\rodar.py --etapa 2
.venv\Scripts\python scripts\rodar.py --etapa confirmatoria
.venv\Scripts\python scripts\rodar.py --agentes PacmAnt-evoluido+alg1 --sementes 2000-2099 --saida resultados/confirmatoria
.venv\Scripts\python scripts\analise.py
```

Etapa 4 (calibração e teste final):

```
.venv\Scripts\python scripts\ils.py --paralelo 8
.venv\Scripts\python scripts\rodar.py --etapa 4
.venv\Scripts\python scripts\analise.py
```

O `ils.py` para em 150 avaliações ou 3 horas e pode ser retomado (as avaliações gravadas em
`resultados/ils/avaliacoes.csv` não são refeitas). O `rodar.py --etapa 4` lê o preset do ILS de
`resultados/ils/melhor.json`.

Teste de replicação com o preset manual:

```
.venv\Scripts\python scripts\rodar.py --agentes PacmAnt-manual PacmAnt-manual+alg1 --sementes 2000-2099 --saida resultados/replicacao_manual
.venv\Scripts\python -c "import sys; sys.path.insert(0, 'scripts'); import analise; analise.analisar('replicação (preset manual)', 'replicacao_manual', 'analise_replicacao_manual', [], ['PacmAnt-manual', 'PacmAnt-manual+alg1'], variantes=False, jogos_esperados={'': 100})"
```

O segundo comando usa as funções de `scripts/analise.py` para gravar a tabela-resumo e os boxplots.
Ele também grava um `mann_whitney.csv` vazio, porque essa rodada não tem teste de hipótese.

Um jogo isolado:

```
java -cp build pacmant.Experimento --agente PacmAnt-evoluido --fantasma Legacy --sementes 1000-1000 --saida teste.csv
```

Agentes: `Random`, `RandomNonRev`, `NearestPill`, `PacmAnt-evoluido`, `PacmAnt-manual`, `PacmAnt-A1`, `PacmAnt-A2`.
Variantes do pac-mAnt por sufixo: `+acs`, `+persist`, `+minimo`, `+margem0`, `+alg1`, `+chi05`, `+norm` (ver `DECISOES.md`).
A opção `--params nome=valor,...` do `Experimento` troca parâmetros do preset (por exemplo `--params dMin=15,q0E=0.74`).
A opção `--traco PASTA` do `Experimento` grava um CSV por jogo com uma linha por tick (termos da eq. 16 e máximos do feromônio). Jogos rodados com `--traco` não servem para medir pontuação: a gravação atrasa a Ms. Pac-Man (seção 4.7 de `DECISOES.md`).
Fantasmas: `Legacy`, `RandomGhosts`, `Legacy2`, `AttractRepel`.

## Colunas dos CSVs

`agente, fantasma, semente, pontuacao, fase, vidas_perdidas, ticks, ms_medio, ms_max, estouros, erros, segundos, pct_fuga`

- `fase`: valor de `getCurLevel()` ao fim do jogo (começa em 0; é o número de fases concluídas).
- `vidas_perdidas`: 3 vidas iniciais, mais 1 se a pontuação chegou a 10.000, menos as vidas restantes.
- `ms_medio`, `ms_max`: tempo de decisão por tick; `estouros`: ticks acima de 40 ms.
- `erros`: exceções lançadas pelo controlador (deve ser 0).
- `segundos`: duração do jogo em tempo de relógio.
- `pct_fuga`: % dos ticks em que o pac-mAnt seguiu a exploradora (só para o pac-mAnt).
