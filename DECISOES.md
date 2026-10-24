# Decisões de implementação

Registro das escolhas feitas onde o artigo (Recio et al., 2012) ou a especificação do trabalho
deixam margem. As Decisões 1 a 8 vieram da especificação; as Decisões 9 a 15 foram tomadas durante a
implementação. Os resultados que justificam cada escolha estão na seção 4.

> **Sobre a fonte.** O pac-mAnt foi implementado a partir da especificação do trabalho, sem o texto
> completo do artigo. Depois da primeira rodada, três pontos foram conferidos no PDF:
> - **Algoritmo 1:** uma construção por chamada e uma atualização global da melhor formiga ao fim.
>   Isso só faz sentido com feromônio persistente entre ticks. A configuração padrão (Decisões 3 e 9)
>   **não** é isso; a versão fiel é a flag `+alg1` (Decisão 15).
> - **Regra de morte da exploradora:** "at least one ghost that can arrive at that node before the
>   agent, unless there is a power pill on the way". As Decisões 12 e 13 foram mantidas.
> - **Parada da exploradora:** ao atingir o limite de nós, continua até um nó com 3 ou mais vizinhos.
>   Está implementado assim.

## 1. Escopo executado

- Preset **evoluído** do pac-mAnt (o preset manual está em `ParametrosAnt.manual()`, mas não foi usado).
- Rodada exploratória: **30 jogos, sementes 1000–1029, contra `Legacy`** (seções 4.2 e 4.3).
- Rodada confirmatória da Etapa 2: **100 jogos, sementes 2000–2099, contra `Legacy`** (seções 4.4 a 4.6).
- Etapa 0 (validação do simulador): rodada antes da redução de escopo, com 100 jogos (sementes 1000–1099)
  contra `Legacy` e `RandomGhosts`.
- Etapa 3 (RHEA): **não implementada** (fora do escopo final).

## 2. Decisões da especificação (1 a 8)

| # | Ponto | Escolha |
|---|---|---|
| — | Regra de transição (eq. 4–5) | Como no artigo: **q > q0 escolhe o melhor** (argmax de τ·η); senão sorteio proporcional. É o inverso do ACS clássico. Flag `+acs` inverte. |
| 1 | ηᶜ em nó sem pílula nem fantasma comestível | ε = 0,01 |
| 2 | τ0 | 0,1 para os dois tipos; todas as matrizes começam em τ0 |
| 3 | Persistência do feromônio | Reinício a cada tick (padrão). Flag `+persist`: persiste e só reinicia ao perder vida ou mudar de fase. |
| 4 | Qᶜ (eq. 16) | Denominador δ + 1. Cada nó (e sua pílula) conta uma vez por formiga, porque a formiga não revisita nós. |
| 5 | Qᵉ (eq. 19) | Numerador: δ(nⱼ, fantasma não comestível mais próximo). Denominador: (δ(pacman, nⱼ) + 1)². |
| 6 | χ e ω | Seguem as equações: χ entra na qualidade das coletoras (peso de τᵉ) e ω na das exploradoras (peso de τᶜ). |
| 7 | Quantidade de formigas | maxAnt de cada tipo por tick, em rodízio entre os ninhos. |
| 8 | Φ e μ | Φ é peso direto (79); μ é porcentagem de 0 a 100 (60). |

## 3. Decisões tomadas na implementação (9 a 15)

### Decisão 9 — iterações e atualização global dentro do tick
Com reinício do feromônio a cada tick (Decisão 3) e um único lote de formigas, o feromônio seria
inerte: a atualização local leva τ0 para τ0 e a atualização global seria descartada logo depois.
Os termos χ·τᵉ e ω·τᶜ também seriam constantes, e a variante A2 não teria efeito.

Escolha: dentro do tick, **uma iteração é uma passada pelos ninhos** (uma exploradora e uma coletora
por ninho). Ao fim de cada iteração, a melhor formiga de cada tipo **até aquele momento** deposita
(τ ← (1 − α)·τ + α·Q) nos arcos que percorreu. Com maxAnt = 19 isso dá de 5 a 10 iterações por tick.
Em cada ninho a exploradora é lançada antes da coletora. A qualidade é calculada quando a formiga
termina, com o feromônio do outro tipo naquele instante.

### Decisão 10 — distância quando não há fantasma perigoso
Fantasmas na toca (`getLairTime > 0`) e fantasmas comestíveis não contam como perigosos. Se não há
nenhum fantasma perigoso, δ(nó, fantasma mais próximo) = 200 (constante `SEM_FANTASMA`) e nenhuma
exploradora morre.

### Decisão 11 — nó da Ms. Pac-Man
A formiga parte do ninho e o nó atual da Ms. Pac-Man conta como já visitado (a formiga não volta por
ele). O arco Ms. Pac-Man → ninho faz parte do caminho (recebe atualização local e depósito).
O feromônio fica nos arcos dirigidos (nó, direção).

### Decisão 12 — "distância da Ms. Pac-Man até o nó"
Duas leituras possíveis de δ(pacman, nⱼ) nas eq. 16 e 19 e na regra de morte:
- **trilha (padrão):** número de passos ao longo do caminho da formiga, isto é, o tempo que a
  Ms. Pac-Man levaria para chegar ao nó seguindo aquela formiga;
- **caminho mínimo** (flag `+minimo`): `getPathDistance(pacman, nó)`.

### Decisão 13 — folga na regra de morte da exploradora
Leitura literal: a exploradora morre se `getGhostPathDistance(g, nó) ≤ distância da Ms. Pac-Man`.
No simulador, porém, o fantasma captura a Ms. Pac-Man a até **2 nós de distância**
(`EAT_DISTANCE = 2`), e as duas andam na mesma velocidade. Um nó ao qual a Ms. Pac-Man chega 1 ou 2
ticks antes do fantasma não é seguro.

Escolha: a exploradora morre se `chegada do fantasma ≤ distância da Ms. Pac-Man + 2`
(folga = `EAT_DISTANCE`). Flag `+margem0` volta à leitura literal.

O tempo de chegada do fantasma usa a mesma conta de `getGhostPathDistance` (caminho guloso sem
reversão), reimplementada sem alocação de memória e com limite de passos. `Testes.java` confere que
os dois valores são iguais (mais de 1,3 milhão de comparações, nenhuma diferença).

### Decisão 14 — detalhes menores
- **Melhor exploradora:** uma viva vence uma morta; entre vivas, maior Qᵉ; se todas morreram, a que
  visitou mais nós (empate: maior Qᵉ). A morta não inclui o nó em que morreu.
- **Pílula de poder no caminho:** depois que a exploradora passa por uma pílula de poder ativa, o
  teste de morte deixa de valer para os nós seguintes (não para o próprio nó da pílula).
- **ηᶜ com fantasma comestível e pílula no mesmo nó:** vale o caso do fantasma (Φ·pillsLevel/pillsRemaining).
- **pillsRemaining = 0** (só restam pílulas de poder): usa-se 1 no denominador.
- **% de exploradoras mortas:** mortas / maxAnt, no tick atual.
- **A1, condições simultâneas:** última vida e fase alta se acumulam (d_min × 1,5 × 1,2).
- **A2, perigo:** fração de exploradoras mortas no tick anterior; zerada no início do jogo e a cada vida perdida.
- **Gerador aleatório:** cada agente tem o seu (`java.util.Random`), com semente derivada da semente
  do jogo (7919·semente + 13). O simulador usa um gerador estático (`G.rnd`) e duas threads, então
  cada configuração roda em um processo separado.
- **Vidas perdidas:** 3 + (1 se pontuação ≥ 10.000) − vidas restantes.
- **`fase` no CSV:** `getCurLevel()` ao fim do jogo (0 = nenhuma fase concluída; máximo 15 no fim da 16ª fase).

### Decisão 15 — Algoritmo 1 fiel ao artigo (flag `+alg1`)
A Decisão 9 (várias iterações com depósito dentro do tick) foi tomada sem o texto do artigo. O
Algoritmo 1 do artigo faz **uma única construção por chamada** e **um depósito ao fim**.

Flag `+alg1`:
- em cada tick, as maxAnt formigas de cada tipo são lançadas em rodízio pelos ninhos, sem nenhum
  depósito no meio;
- ao fim, a melhor coletora e a melhor exploradora do tick depositam uma vez (eq. 14–15 e 17–18);
- o feromônio **persiste entre ticks** (reinicia só ao perder vida ou mudar de fase), porque sem
  isso o depósito seria descartado antes de influenciar qualquer formiga.

Diferença para `+persist`: `+persist` mantém as 5 a 10 iterações com depósito dentro do tick
(Decisão 9); `+alg1` faz um depósito por tick. O restante (Decisões 10 a 14) é igual.
A configuração padrão continua sendo a da Decisão 9 com reinício por tick, por ser a base em que
as hipóteses da Etapa 2 foram registradas. Resultado na seção 4.5.

## 4. Resultados

Arquivos: `resultados/etapa*/` (um CSV por configuração, uma linha por jogo) e `resultados/analise/`
(`resumo.csv`, `resumo.md`, `mann_whitney.csv`, `todos_os_jogos.csv`, boxplots PNG).
Nenhum jogo teve exceção no controlador nem tick acima de 40 ms.

### 4.1 Etapa 0 — validação do simulador (100 jogos, sementes 1000–1099)

| Agente | vs RandomGhosts (aqui) | Artigo | vs Legacy (aqui) | Artigo |
|---|---|---|---|---|
| Random | 303 ± 450 | 502 ± 156 | 84 ± 25 | 172 ± 61 |
| RandomNonRev | 1710 ± 677 | 1471 ± 411 | 1481 ± 798 | 1522 ± 741 |
| NearestPill (≈ SimplePillEater) | 5102 ± 2432 | 5901 ± 2196 | 3744 ± 538 | 5693 ± 1423 |

A ordem de grandeza e a ordenação dos agentes batem com a Tabela III. As maiores diferenças são no
agente `Random` (cerca de metade do valor do artigo) e no `NearestPill` contra `Legacy` (66% do valor
do artigo). O simulador é uma versão refatorada do código da competição, então pequenas diferenças
nos agentes e fantasmas de exemplo são esperadas.

### 4.2 Etapa 1 — pac-mAnt, preset evoluído, contra Legacy (30 jogos, sementes 1000–1029)

| Configuração | Pontuação (média ± DP) | IC 95% | Fase média | Vidas perdidas/fase | ms/tick (médio / máx.) | s/jogo |
|---|---|---|---|---|---|---|
| **Padrão** (trilha, folga 2, reinício por tick, regra do artigo) | **36.277 ± 14.551** | ± 5.433 | 5,97 | 0,73 | 0,48 / 16,2 | 9,7 |
| `+acs` | 29.324 ± 12.307 | ± 4.595 | 4,17 | 0,93 | 0,43 / 8,3 | 6,5 |
| `+persist` | 47.421 ± 18.339 | ± 6.848 | 7,13 | 0,63 | 0,50 / 18,7 | 12,5 |
| `+acs+persist` | 43.979 ± 17.360 | ± 6.482 | 6,30 | 0,68 | 0,50 / 10,4 | 10,7 |
| `+minimo` | 19.640 ± 8.548 | ± 3.192 | 2,43 | 1,40 | 0,47 / 10,8 | 4,3 |
| `+margem0` | 5.007 ± 1.477 | ± 551 | 0,00 | 3,00 | 0,48 / 9,2 | 0,8 |
| `+minimo+margem0` (leitura literal) | 5.921 ± 2.350 | ± 877 | 0,03 | 2,97 | 0,45 / 19,7 | 0,9 |
| `+minimo+margem0+acs` | 5.799 ± 1.657 | ± 619 | 0,00 | 3,00 | 0,31 / 4,8 | 0,6 |
| `+minimo+margem0+persist` | 8.823 ± 4.212 | ± 1.573 | 0,30 | 2,72 | 0,35 / 6,9 | 1,1 |
| `+minimo+margem0+acs+persist` | 6.931 ± 1.970 | ± 736 | 0,07 | 2,97 | 0,34 / 5,7 | 0,8 |

Referência do artigo (Tabela III, parâmetros manuais, 100 jogos): 36.031 ± 11.819 contra LegacyTeam.

**O que mudou e por quê.**
1. A primeira versão seguia a leitura literal (`+minimo+margem0`) e ficou muito abaixo do artigo
   (5.921 contra 36.031), no nível do `NearestPill`.
2. Como pedido na especificação, foram testadas as flags da seção 1.3 (`+acs`) e da Decisão 3
   (`+persist`) sobre essa versão. Nenhuma resolveu: 5.799, 8.823 e 6.931.
3. O diagnóstico (`Diagnostico.java`) mostrou que todas as mortes aconteciam em modo de fuga e que a
   Ms. Pac-Man invertia de direção cerca de 17 vezes a cada 100 ticks.
4. A causa principal é a folga de captura (Decisão 13): sem ela, a exploradora considera seguro um nó
   em que o fantasma chega 1 ou 2 ticks depois, e o simulador captura a 2 nós de distância.
   Com folga 2 a pontuação sobe de 5.921 para 19.640 (`+minimo`).
5. Medir a distância pela trilha da formiga (Decisão 12) leva de 19.640 para 36.277 (p < 0,001).
   Sem a folga, a trilha sozinha não ajuda (`+margem0`: 5.007).
6. Com as duas escolhas, as reversões caem para cerca de 9 por 100 ticks.

**Efeito das flags sobre a configuração padrão** (Mann-Whitney bilateral, pontuação):
- `+persist`: 47.421 contra 36.277, p = 0,012. O feromônio persistente ajuda.
- `+acs`: 29.324 contra 36.277, p = 0,072. A regra do artigo não é pior que a do ACS clássico.
- `+acs+persist`: 43.979 contra 36.277, p = 0,093.

A configuração padrão foi mantida como base da Etapa 2 (reinício por tick e regra do artigo, como
manda a especificação), apesar de `+persist` pontuar mais.

**Ressalvas.**
- As Decisões 12 e 13 foram escolhidas olhando jogos das sementes 1000–1019, que fazem parte do
  conjunto de avaliação. O efeito (de ~5,9 mil para ~36 mil) é muito maior que o ruído, mas a
  proximidade com o valor do artigo não deve ser lida como replicação exata: o número do artigo é do
  preset manual, e aqui foi usado o evoluído.
- A configuração padrão passa cerca de 78% dos ticks em fuga. No diagnóstico das sementes 1000–1009,
  as 47 fases concluídas terminaram pelo limite de 3000 ticks, nenhuma por limpeza do labirinto
  (ao estourar o limite, o simulador credita as pílulas restantes). A pontuação vem de sobreviver,
  das pílulas comidas no caminho e dos fantasmas comidos após pílulas de poder.
- O tempo de decisão (0,48 ms por tick em média, 16 ms no pior tick) fica muito abaixo de 40 ms.
  Os tempos foram medidos com 9 processos em paralelo em uma CPU de 8 núcleos.

### 4.3 Etapa 2, rodada exploratória — pac-mAnt adaptativo contra Legacy (30 jogos, sementes 1000–1029)

Esta rodada usa sementes que também serviram para escolher as Decisões 12 e 13; vale como
exploratória. A rodada confirmatória está na seção 4.4.

| Agente | Pontuação (média ± DP) | IC 95% | Mediana | Fase média | Vidas perdidas/fase | % ticks em fuga | s/jogo |
|---|---|---|---|---|---|---|---|
| Fixo (evoluído) | 36.277 ± 14.551 | ± 5.433 | 36.630 | 5,97 | 0,73 | 78,5 | 9,7 |
| A1 | 34.615 ± 16.253 | ± 6.069 | 35.530 | 5,73 | 0,84 | 80,0 | 9,2 |
| A2 | 38.091 ± 13.147 | ± 4.909 | 35.870 | 6,87 | 0,59 | 80,3 | 10,2 |

Mann-Whitney bilateral:

| Par | Pontuação (p) | Vidas perdidas/fase (p) |
|---|---|---|
| Fixo × A1 | 0,610 | 0,800 |
| Fixo × A2 | 0,684 | 0,229 |
| A1 × A2 | 0,473 | 0,156 |

**Leitura das hipóteses (`HIPOTESES.md`).**
- **H1 (A1 e A2 perdem menos vidas por fase que o fixo): não confirmada.** A2 tem média menor
  (0,59 contra 0,73), mas a diferença não é significativa (p = 0,23). A1 ficou com média maior que o
  fixo (0,84), também sem significância (p = 0,80).
- **H2 (A2 ≥ A1 em pontuação): não confirmada estatisticamente.** A média de A2 é maior
  (38.091 contra 34.615), mas p = 0,47 e as medianas são quase iguais.

Com 30 jogos e desvio padrão em torno de 14 mil pontos, o IC 95% de cada média tem meia-largura de
5 a 6 mil pontos. Diferenças do tamanho observado (2 a 3,5 mil) não são detectáveis com essa amostra.
A regra de A1 atua pouco: a base já passa ~78% dos ticks em fuga, e a condição de última vida só
vale no trecho final de cada jogo.

### 4.4 Etapa 2, rodada confirmatória (100 jogos, sementes 2000–2099, contra Legacy)

Registrada em `HIPOTESES.md` antes de rodar: sementes novas, nenhum parâmetro nem decisão alterado,
mesmo código compilado. Arquivos: `resultados/confirmatoria/` e `resultados/analise_confirmatoria/`.
Nenhum jogo teve exceção nem tick acima de 40 ms.

| Agente | Pontuação (média ± DP) | IC 95% | Mediana | Fase média | Vidas perdidas/fase | % fuga | s/jogo |
|---|---|---|---|---|---|---|---|
| Fixo | 35.459 ± 14.760 | ± 2.929 | 33.500 | 5,97 | 0,70 | 78,6 | 9,6 |
| A1 | 34.165 ± 14.686 | ± 2.914 | 35.420 | 5,68 | 0,74 | 80,3 | 8,9 |
| A2 | 35.074 ± 12.847 | ± 2.549 | 33.800 | 6,29 | 0,64 | 79,8 | 9,3 |
| Fixo+persist | 47.223 ± 16.358 | ± 3.246 | 44.800 | 7,25 | 0,60 | 74,4 | 12,4 |
| A1+persist | 49.968 ± 18.052 | ± 3.582 | 48.140 | 7,97 | 0,55 | 77,4 | 13,6 |
| A2+persist | 18.965 ± 7.538 | ± 1.496 | 17.945 | 3,11 | 1,06 | 78,7 | 4,6 |

Mann-Whitney bilateral, com correção de Holm por base e por métrica (3 pares):

| Base | Par | Pontuação: p bruto / Holm | Vidas por fase: p bruto / Holm |
|---|---|---|---|
| Reinício (primária) | Fixo × A1 | 0,62 / 1,00 | 0,56 / 0,56 |
| Reinício (primária) | Fixo × A2 | 0,99 / 1,00 | 0,24 / 0,49 |
| Reinício (primária) | A1 × A2 | 0,71 / 1,00 | 0,083 / 0,25 |
| Persistente (secundária) | Fixo × A1 | 0,29 / 0,29 | 0,21 / 0,21 |
| Persistente (secundária) | Fixo × A2 | 9,7e-28 / 2,9e-27 | 7,4e-20 / 1,5e-19 |
| Persistente (secundária) | A1 × A2 | 1,9e-27 / 3,7e-27 | 3,6e-21 / 1,1e-20 |

**Análise primária (base com reinício por tick).**
- **H1 não confirmada:** A1 e A2 não perdem menos vidas por fase que o Fixo (p ajustado 0,56 e 0,49).
- **H2 não confirmada:** as pontuações de A2 e A1 não se distinguem (p ajustado 1,00).
- Com 100 jogos, o IC 95% de cada média tem meia-largura de 2,5 a 2,9 mil pontos, e as três médias
  ficam dentro de 1,3 mil pontos uma da outra. O resultado é o mesmo da rodada exploratória.

**Análise secundária (base com feromônio persistente).**
- A1+persist não difere de Fixo+persist (p = 0,29 em pontuação).
- **A2+persist é muito pior** que Fixo+persist e A1+persist: 18.965 pontos contra 47.223 e 49.968,
  e 1,06 vida perdida por fase contra 0,60 e 0,55. A causa foi investigada no teste post hoc (seção 4.6).

A observação da rodada exploratória de que `+persist` pontua mais que a configuração padrão se
repete aqui (47.223 contra 35.459), mas essa comparação entre bases não foi registrada como hipótese.

### 4.5 Algoritmo 1 fiel (`+alg1`), 100 jogos, sementes 2000–2099, contra Legacy

Comparação registrada em `HIPOTESES.md` antes de implementar e rodar. A flag foi compilada só
depois do fim da rodada confirmatória.

| Agente | Pontuação (média ± DP) | IC 95% | Mediana | Fase média | Vidas perdidas/fase | % fuga | ms/tick (médio / máx.) | s/jogo |
|---|---|---|---|---|---|---|---|---|
| Fixo (Decisão 9, reinício por tick) | 35.459 ± 14.760 | ± 2.929 | 33.500 | 5,97 | 0,70 | 78,6 | 0,48 / 10,4 | 9,6 |
| Fixo+persist | 47.223 ± 16.358 | ± 3.246 | 44.800 | 7,25 | 0,60 | 74,4 | 0,51 / 10,5 | 12,4 |
| **Fixo+alg1** | **53.016 ± 17.488** | ± 3.470 | 53.715 | 8,95 | 0,50 | 80,7 | 0,55 / 7,3 | 16,3 |

Mann-Whitney bilateral com Holm (2 pares):

| Par | Pontuação: p bruto / Holm | Vidas por fase: p bruto / Holm |
|---|---|---|
| Fixo × Fixo+alg1 | 1,2e-11 / 2,5e-11 | 1,5e-7 / 3,0e-7 |
| Fixo+persist × Fixo+alg1 | 0,016 / 0,016 | 0,0027 / 0,0027 |

A versão fiel ao Algoritmo 1 é a melhor das três: pontua mais e perde menos vidas por fase que a
configuração padrão e que `+persist`, com diferenças significativas nos dois pares. A1 e A2 não
foram rodados sobre `+alg1`.

### 4.6 Teste post hoc: χ > 0 com feromônio persistente (`+chi05` e traços por jogada)

**Post hoc e exploratório:** decidido depois de ver a queda de A2+persist na seção 4.4 e registrado
como tal em `HIPOTESES.md` antes de implementar e rodar. Arquivos: `resultados/posthoc_chi/`
(traços por jogada, `resumo_traco.md`, `resumo_traco.csv`) e a família `chi05` em
`resultados/analise_confirmatoria/mann_whitney.csv`.

**Pontuação nas mesmas 50 sementes (2000–2049), contra Legacy:**

| Agente | χ | Pontuação (média ± DP) | Mediana | Fase média | Vidas perdidas/fase |
|---|---|---|---|---|---|
| Fixo+persist | 0 | 46.564 ± 15.822 | 44.450 | 7,22 | 0,59 |
| A2+persist | 0,5 · perigo | 17.796 ± 7.271 | 16.515 | 2,80 | 1,13 |
| Fixo+persist+chi05 | 0,5 fixo | 13.187 ± 6.714 | 12.395 | 2,56 | 1,20 |

Mann-Whitney bilateral com Holm (2 pares):

| Par | Pontuação: p bruto / Holm | Vidas por fase: p bruto / Holm |
|---|---|---|
| Fixo+persist × `+chi05` | 2,0e-16 / 4,0e-16 | 7,6e-13 / 1,5e-12 |
| A2+persist × `+chi05` | 0,0020 / 0,0020 | 0,63 / 0,63 |

**Traços por jogada (5 jogos por configuração, sementes 2000–2004), na melhor coletora do tick:**

| Agente | Termo das pílulas (média por jogada) | χ·Στᵉ (mediana por jogada) | τᵉ máximo no jogo | τᶜ máximo no jogo | Ticks com soma infinita |
|---|---|---|---|---|---|
| Fixo+persist (χ = 0) | 2,6 a 3,8 | 0 (Στᵉ mediana de 77 a 127) | 346 a 973 | 29 a 67 | 0% |
| A2+persist | 1,2 a 2,0 | 1e40 a 1e307 | 7,1e307 a 1,3e308 | 1,1e308 a 1,2e308 | 9% a 23% |
| Fixo+persist+chi05 | 1,0 a 1,4 | 1e307 a 3e307 | 1,1e308 a 1,2e308 | 1,2e308 | 18% a 28% |

O limite de um `double` é 1,8e308. A média de χ·Στᵉ por jogada fica entre 3e306 e 4e307 nos jogos
com χ > 0; a mediana é mais informativa porque a média é dominada pelos ticks próximos do limite.

**O que os traços mostram.**
- Com χ = 0 o feromônio fica limitado: τᵉ não passa de 973 em nenhum dos 5 jogos.
- Com χ = 0,5 fixo, τᵉ passa de 1e6 no tick 1 ou 2 e de 1e100 por volta do tick 20. O crescimento
  é de cerca de 5 ordens de grandeza por tick.
- Em A2+persist, τᵉ não passa de 58 enquanto χ = 0 (nenhuma exploradora morta). χ fica positivo
  pela primeira vez no tick 41 nos 5 jogos (quando o primeiro fantasma sai da toca); τᵉ passa de
  1e6 entre os ticks 42 e 49 e chega a 1e100 entre os ticks 61 e 152.
- Depois disso o termo χ·Στᵉ é maior que o das pílulas em 61% a 90% dos ticks de A2+persist e em
  100% dos ticks de `+chi05`, por centenas de ordens de grandeza. A coletora deixa de enxergar pílulas.
- Não apareceu NaN. Os valores param perto de 1,2e308 porque o código não deposita quando a
  qualidade é infinita.

**Conclusão.**
- **A adaptação de χ não é a causa.** χ fixo é tão ruim quanto, ou pior: 13.187 contra 17.796
  pontos (p = 0,002), com vidas por fase equivalentes (p = 0,63). A2+persist se sai um pouco melhor
  só porque passa parte do jogo com χ = 0 ou baixo.
- **A causa é χ > 0 combinado com persistência**, e o mecanismo é uma divergência numérica: Qᶜ soma
  χ·τᵉ ao longo de até 100 arcos, o depósito das coletoras cresce com Qᶜ, Qᵉ soma ω·τᶜ (ω = 0,72),
  e o depósito das exploradoras cresce com Qᵉ. Com reinício por tick, o laço é cortado a cada
  jogada; com persistência, não.
- **Não é um erro de digitação no código**, e sim uma consequência das eq. 14 a 19 como
  implementadas, somadas às várias atualizações globais por tick (Decisão 9). No artigo, χ = 0 nos
  dois conjuntos de parâmetros, então esse laço nunca foi fechado lá.

**Limites desta conclusão.**
- Vale para a base `+persist` (Decisão 9 com persistência). **Não foi testado com `+alg1`**, que faz
  um depósito por tick. Uma conta aproximada do ganho por tick
  (αᵉ·ω·100 × αᶜ·χ·100 ≈ 7,9 × 11,5 ≈ 91) indica que também divergiria, mais devagar; isso é
  estimativa, não medida.
- **Implicação para a análise primária (base com reinício), por inferência:** no tick 0 do traço de
  `+chi05`, partindo de τ0, Στᵉ já vale 2,5e6 contra 0,78 do termo das pílulas. Ou seja, mesmo sem
  persistência, quando χ > 0 o termo colaborativo domina dentro do próprio tick. Isso sugere que o
  A2 da base primária, nos ticks com χ > 0, escolhe a coletora quase só pela sobreposição com as
  exploradoras. Não foi traçado um jogo de A2 sem `+persist` para confirmar.
- Para χ ser um parâmetro utilizável com persistência, a eq. 16 precisaria de alguma normalização
  (por exemplo, média de τᵉ no caminho em vez de soma) ou de um teto para τ. Nenhuma das duas foi implementada.
