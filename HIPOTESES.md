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
