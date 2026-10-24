# pac-mAnt e pac-mAnt adaptativo

Trabalho final de Pesquisa Operacional (UFF). Replicação do pac-mAnt de
Recio, Martín, Estébanez e Sáez, *AntBot: Ant Colonies for Video Games* (IEEE TCIAIG, v. 4, n. 4, 2012),
e uma versão adaptativa (A1 e A2).

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
| `scripts/resumo_traco.py` | Resumo dos traços por jogada do teste post hoc |
| `resultados/etapa0`, `etapa1`, `etapa2` | Rodada exploratória: CSVs, uma linha por jogo |
| `resultados/analise/` | Rodada exploratória: tabela-resumo, testes e gráficos PNG |
| `resultados/confirmatoria/` | Rodada confirmatória (sementes 2000–2099), `+alg1` e `+chi05` |
| `resultados/analise_confirmatoria/` | Rodada confirmatória: tabela-resumo, testes e gráficos PNG |
| `resultados/posthoc_chi/` | Traços por jogada (eq. 16) e seu resumo |
| `DECISOES.md` | Decisões de implementação e resultados |
| `HIPOTESES.md` | Hipóteses registradas antes de rodar |

## Como reproduzir (Windows)

Clonar com o simulador: `git clone --recurse-submodules <url>` (ou `git submodule update --init` depois de clonar).

Requisitos: JDK 21 e Python 3 com `numpy`, `scipy`, `pandas` e `matplotlib`.
Se existir um JDK em `tools/jdk-*`, os scripts o usam; senão, usam o `java`/`javac` do PATH.

```
powershell -ExecutionPolicy Bypass -File compilar.ps1
python scripts/rodar.py --etapa 1
python scripts/rodar.py --etapa 2
python scripts/rodar.py --etapa confirmatoria
python scripts/rodar.py --agentes PacmAnt-evoluido+alg1 --sementes 2000-2099 --saida resultados/confirmatoria
.venv\Scripts\python scripts\analise.py
```

Um jogo isolado:

```
java -cp build pacmant.Experimento --agente PacmAnt-evoluido --fantasma Legacy --sementes 1000-1000 --saida teste.csv
```

Agentes: `Random`, `RandomNonRev`, `NearestPill`, `PacmAnt-evoluido`, `PacmAnt-A1`, `PacmAnt-A2`.
Variantes do pac-mAnt por sufixo: `+acs`, `+persist`, `+minimo`, `+margem0`, `+alg1`, `+chi05` (ver `DECISOES.md`).
A opção `--traco PASTA` do `Experimento` grava um CSV por jogo com uma linha por tick (termos da eq. 16 e máximos do feromônio).
Fantasmas: `Legacy`, `RandomGhosts`, `Legacy2`, `AttractRepel`.

## Colunas dos CSVs

`agente, fantasma, semente, pontuacao, fase, vidas_perdidas, ticks, ms_medio, ms_max, estouros, erros, segundos, pct_fuga`

- `fase`: valor de `getCurLevel()` ao fim do jogo (começa em 0; é o número de fases concluídas).
- `vidas_perdidas`: 3 vidas iniciais, mais 1 se a pontuação chegou a 10.000, menos as vidas restantes.
- `ms_medio`, `ms_max`: tempo de decisão por tick; `estouros`: ticks acima de 40 ms.
- `erros`: exceções lançadas pelo controlador (deve ser 0).
- `segundos`: duração do jogo em tempo de relógio.
- `pct_fuga`: % dos ticks em que o pac-mAnt seguiu a exploradora (só para o pac-mAnt).
