# Hipóteses (escritas antes de rodar cada etapa)

## Etapa 2 — pac-mAnt adaptativo (registrada antes da execução de A1 e A2)

Comparação contra `Legacy`, 30 jogos, sementes 1000–1029, mesma base (preset evoluído).

- **H1:** A1 e A2 perdem menos vidas por fase que o pac-mAnt fixo.
- **H2:** A2 ≥ A1 em pontuação.

Medidas: `vidas_perdidas / (fase + 1)` para H1 e `pontuacao` para H2, com teste de Mann-Whitney
bilateral nos pares fixo × A1, fixo × A2 e A1 × A2.

Observação feita antes de rodar: a regra da última vida de A1 só atua quando resta uma vida, e a
regra de fase alta só a partir de `getCurLevel() >= 4`; em jogos curtos o efeito esperado é pequeno.
A parte "contra `RandomGhosts` a diferença deve ser pequena" da hipótese original não será testada,
porque o escopo final ficou restrito a `Legacy`.

## Etapa 2 — rodada confirmatória (registrada antes da execução)

A rodada anterior da Etapa 2 (sementes 1000–1029, 30 jogos) passa a ser tratada como
**exploratória**: as Decisões 12 e 13 foram escolhidas olhando jogos das sementes 1000–1019.
Esta é a **rodada confirmatória**.

- **Sementes novas:** 2000–2099 (100 jogos por configuração), contra `Legacy`. Nenhuma dessas
  sementes foi usada antes.
- **Nenhum parâmetro e nenhuma decisão foram alterados** depois da rodada exploratória; o código
  compilado é o mesmo.
- **Seis configurações, em duas bases:**
  - base com reinício do feromônio a cada tick: Fixo (`PacmAnt-evoluido`), `PacmAnt-A1`, `PacmAnt-A2`;
  - base com feromônio persistente: `PacmAnt-evoluido+persist`, `PacmAnt-A1+persist`, `PacmAnt-A2+persist`.
- **Hipóteses, as mesmas, avaliadas dentro de cada base:**
  - **H1:** A1 e A2 perdem menos vidas por fase que o Fixo.
  - **H2:** A2 ≥ A1 em pontuação.
- **Análise:** média ± DP, IC 95%, mediana, vidas perdidas por fase e % de ticks em fuga por
  configuração; teste de Mann-Whitney bilateral nos pares Fixo × A1, Fixo × A2 e A1 × A2, só dentro
  de cada base. Os p-valores são reportados sem correção para comparações múltiplas (3 pares por
  base e por métrica); 5% é a referência de significância.

### Complemento ao registro da rodada confirmatória (feito com a rodada ainda em execução)

- **Comparações múltiplas:** correção de **Holm por base e por métrica** (família de 3 pares:
  Fixo × A1, Fixo × A2, A1 × A2). Reportam-se o p bruto e o p ajustado; a referência de 5% vale
  para o p ajustado. Isto substitui a frase anterior sobre p-valores sem correção.
- **Análise primária:** H1 e H2 são avaliadas na base com **reinício do feromônio a cada tick**
  (a base registrada). As configurações `+persist` são **análise secundária**.
- **Transparência:** este complemento foi escrito antes do fim da rodada, mas depois de um teste do
  script de análise sobre dados parciais (de 3 a 9 jogos por configuração), cuja saída foi vista.
  Nenhum resultado completo havia sido visto.

### Comparação adicional: Algoritmo 1 fiel (`+alg1`), registrada antes de implementar e rodar

- Depois que a rodada confirmatória terminar, será criada a flag `+alg1`: uma única construção por
  tick (cada ninho recebe suas formigas e, ao fim, um único depósito da melhor de cada tipo), com
  feromônio persistente, como no Algoritmo 1 do artigo. Será documentada como Decisão 15.
- Roda-se só o Fixo com `+alg1`, sementes 2000–2099, contra `Legacy`.
- Comparação: Fixo × Fixo+alg1 e Fixo+persist × Fixo+alg1, Mann-Whitney bilateral com Holm
  (família de 2 pares), p bruto e ajustado.

## Teste post hoc: χ fixo com feromônio persistente (`+chi05`)

**Natureza:** teste **post hoc**, exploratório. Foi decidido **depois** de ver o resultado completo
da rodada confirmatória, em que A2+persist ficou muito abaixo de Fixo+persist (18.965 contra 47.223
pontos). Não faz parte das hipóteses registradas e não deve ser lido como confirmatório. Este
registro foi escrito antes de implementar a flag, de rodar os jogos e de ver qualquer traço.

**Objetivo:** separar três explicações para a queda de A2+persist:
1. χ > 0 com feromônio persistente é ruim por si só;
2. a adaptação de χ (χ = 0,5 · fração de exploradoras mortas no tick anterior) é que é ruim;
3. há um defeito de implementação (por exemplo, instabilidade numérica do feromônio).

**O que será rodado:**
- `PacmAnt-evoluido+persist+chi05`: Fixo+persist com χ fixo = 0,5; sementes 2000–2049 (50 jogos),
  contra `Legacy`. Comparação com Fixo+persist e A2+persist **nas mesmas 50 sementes**
  (Mann-Whitney bilateral, Holm com 2 pares, p bruto e ajustado).
- Traço por jogada em 5 jogos (sementes 2000–2004) de A2+persist e de Fixo+persist (e também de
  `+chi05`): os dois termos da eq. 16 na melhor coletora do tick — o termo das pílulas,
  Σ ηᶜ/(δ+1), e o termo colaborativo, χ·Στᵉ — e o valor máximo de τᵉ na matriz. Os jogos traçados
  são novas execuções (com feromônio persistente os jogos não são reproduzíveis bit a bit).

**Leituras previstas:**
- `+chi05` tão ruim quanto A2+persist, e Fixo+persist bom → explicação 1.
- `+chi05` próximo de Fixo+persist, e A2+persist ruim → explicação 2.
- τᵉ crescendo sem limite, valores infinitos ou NaN nos traços → explicação 3 (ou a 1, se o
  crescimento for a consequência direta das equações e não de um erro de código).

**Conjectura anotada antes de rodar:** com persistência, χ > 0 e ω = 0,72 formam um laço de
realimentação: Qᶜ soma χ·τᵉ, o depósito das coletoras cresce com Qᶜ, Qᵉ soma ω·τᶜ, e o depósito
das exploradoras cresce com Qᵉ. Sem reinício por tick, nada limita esse crescimento. Se for isso,
o termo χ·Στᵉ deve dominar o termo das pílulas por ordens de grandeza.

## Etapa 4 — calibração por ILS e termo colaborativo normalizado (registrada antes de implementar)

Este registro foi escrito antes de implementar a flag `+norm`, o ILS e qualquer script da etapa, e
antes de rodar qualquer jogo nas sementes 5000–5023 e 3000–3099.

### Hipóteses

- **H4:** o pac-mAnt fiel ao Algoritmo 1 (`+alg1`) com parâmetros recalibrados por ILS pontua mais
  que com o preset evoluído do artigo, contra `Legacy`.
- **H5:** A2 com termo colaborativo normalizado (média de τᵉ no caminho em vez da soma, flag
  `+norm`), sobre `+alg1`, pontua pelo menos tanto quanto Fixo+alg1.

### Flag `+norm`

Na eq. 16, o termo colaborativo passa de χ·Σ τᵉ(arco) para χ·(Σ τᵉ(arco) / número de arcos do
caminho da coletora). O termo das pílulas não muda. Sem a flag, o código fica como estava.

### ILS (calibração)

- **Base:** `PacmAnt-evoluido+alg1` (Fixo, sem A1/A2 e sem `+norm`), contra `Legacy`.
- **Solução:** os 12 parâmetros do preset, exceto χ (fica 0). Cada parâmetro anda em uma grade
  ancorada no valor do preset evoluído (valor = preset + k·passo), para que a solução inicial seja
  exatamente o preset:

  | Parâmetro | Preset | Passo | Limites da grade |
  |---|---|---|---|
  | maxAnt | 19 | 2 | 5 a 39 |
  | maxDist | 100 | 10 | 20 a 150 |
  | Φ | 79 | 10 | 9 a 149 |
  | d_min | 13 | 2 | 3 a 41 |
  | μ | 60 | 5 | 10 a 100 |
  | ρᶜ | 0,22 | 0,05 | 0,02 a 0,97 |
  | ρᵉ | 0,70 | 0,05 | 0,05 a 1,00 |
  | αᶜ | 0,23 | 0,05 | 0,03 a 0,98 |
  | αᵉ | 0,11 | 0,05 | 0,01 a 0,96 |
  | ω | 0,72 | 0,05 | 0,02 a 0,97 |
  | q0ᶜ | 0,37 | 0,05 | 0,02 a 0,97 |
  | q0ᵉ | 0,69 | 0,05 | 0,04 a 0,99 |

- **Solução inicial:** preset evoluído.
- **Avaliação:** média da pontuação em 24 sementes **fixas** de treino (5000–5023) contra `Legacy`,
  um processo por jogo, vários em paralelo. A semente do gerador do agente segue a regra de sempre
  (7919·semente + 13).
- **Busca local:** primeira melhora; vizinhança = ±1 passo em um parâmetro (até 24 vizinhos),
  percorrida em ordem sorteada. Só melhora estrita conta.
- **Perturbação:** 3 parâmetros distintos sorteados, cada um deslocado em 2 ou 3 passos, com sinal
  sorteado. Se o deslocamento sair da grade, inverte-se o sinal; se ainda sair, fica no limite.
- **Aceitação:** o ótimo local obtido depois da perturbação substitui a solução atual só se for
  estritamente melhor; a melhor solução global é guardada.
- **Orçamento:** 150 avaliações ou 3 horas, o que vier primeiro. As 3 horas são a soma das durações
  das avaliações. A avaliação da solução inicial conta.
- **Repetições:** uma solução já avaliada não é avaliada de novo (vale a média já gravada) e não
  consome orçamento.
- **Sorteios do ILS:** gerador próprio com semente fixa (4), para a execução poder ser retomada.
- **Registro:** cada avaliação em `resultados/ils/avaliacoes.csv` (número, iteração, fase da busca,
  os 12 parâmetros, média, DP, mediana, duração) e os jogos individuais em `resultados/ils/jogos.csv`.
- **Preset do ILS:** a melhor solução global pela média de treino (em empate, a primeira encontrada).

### Teste final (confirmatório)

- **Sementes novas:** 3000–3099, 100 jogos por configuração, contra `Legacy`. **Nenhuma dessas
  sementes é usada antes do teste final**, nem no ILS, nem em verificações.
- **Configurações:**
  1. Fixo+alg1 com o preset evoluído do artigo (`PacmAnt-evoluido+alg1`);
  2. Fixo+alg1 com o preset do ILS (`PacmAnt-ils+alg1`);
  3. A2+alg1+norm (`PacmAnt-A2+alg1+norm`), com o preset evoluído do artigo (não o do ILS).
- **Análise:** Mann-Whitney bilateral sobre a pontuação, com correção de Holm em uma família de
  2 pares: ILS × Fixo+alg1 e A2norm × Fixo+alg1. Reportam-se p bruto e p ajustado, média ± DP,
  IC 95%, mediana e a probabilidade de um jogo de uma configuração superar um da outra. A referência
  é 5% no p ajustado. Vidas perdidas por fase entra como métrica secundária, com o mesmo procedimento.
- **Leitura de H4:** confirmada se o preset do ILS pontuar mais que o do artigo com p ajustado < 0,05.
- **Leitura de H5:** refutada se A2+alg1+norm pontuar menos que Fixo+alg1 com p ajustado < 0,05.
  Se não houver diferença significativa, H5 fica como "não refutada"; isso não prova equivalência,
  e o tamanho do efeito é reportado junto.
- Nenhum parâmetro, flag ou regra é alterado depois de visto qualquer resultado das sementes 3000–3099.

### Verificações permitidas antes do teste final

- Testes de funcionamento do código (compila, roda, grava CSV) em sementes já usadas (1000–1004).
- Um traço por jogada de `PacmAnt-A2+alg1+norm` nas sementes 2000–2004, só para ver se o feromônio
  continua limitado com a normalização. O que aparecer é reportado; a definição do `+norm` não muda
  por causa disso.

### Observações anotadas antes de rodar

- Com DP de cerca de 17,5 mil pontos por jogo em Fixo+alg1 (seção 4.5 de `DECISOES.md`), a média de
  24 jogos tem erro padrão de cerca de 3,6 mil pontos. O ILS não distingue melhoras reais menores
  que isso de ruído, e a busca de primeira melhora tende a aceitar parte do ruído. A média de treino
  da melhor solução deve sair otimista; o que vale para H4 é o teste nas sementes 3000–3099.
- Os jogos com feromônio persistente não são reproduzíveis bit a bit, então a mesma solução
  avaliada duas vezes pode dar médias diferentes. Por isso vale a primeira avaliação gravada.
- Em A2+alg1+norm, χ ≤ 0,5 e o termo colaborativo passa a ser uma média. Uma conta aproximada do
  ganho do laço τᵉ → Qᶜ → τᶜ → Qᵉ → τᵉ por tick dá, no pior caso,
  αᵉ·ω·100 × αᶜ·χ ≈ 7,9 × 0,115 ≈ 0,9, abaixo de 1 mas perto. A expectativa é que não haja
  divergência; o traço dirá.
