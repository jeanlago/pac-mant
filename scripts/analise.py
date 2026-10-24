"""Análise dos resultados: tabela-resumo, testes de Mann-Whitney e boxplots.

Uso (na raiz do projeto):
    .venv/Scripts/python scripts/analise.py

Duas rodadas, analisadas em separado:
    exploratória   lê resultados/etapa*/*.csv        e grava em resultados/analise/
    confirmatória  lê resultados/confirmatoria/*.csv e grava em resultados/analise_confirmatoria/

Em cada pasta de saída:
    todos_os_jogos.csv   todas as linhas juntas
    resumo.csv, resumo.md   média, desvio padrão, IC 95%, mediana, vidas/fase e % de fuga por agente
    mann_whitney.csv     testes entre os pares de interesse
    boxplot_<fantasma>.png, boxplot_vidas_<fantasma>.png, boxplot_variantes_<fantasma>.png
"""
import glob
import os

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy import stats

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Pares comparados: fixo x A1, fixo x A2, A1 x A2, sempre dentro da mesma base
PARES = [
    ("PacmAnt-evoluido", "PacmAnt-A1"),
    ("PacmAnt-evoluido", "PacmAnt-A2"),
    ("PacmAnt-A1", "PacmAnt-A2"),
]
PARES_PERSIST = [(a + "+persist", b + "+persist") for a, b in PARES]
# Algoritmo 1 fiel (Decisão 15) contra as duas bases
PARES_ALG1 = [("PacmAnt-evoluido", "PacmAnt-evoluido+alg1"),
              ("PacmAnt-evoluido+persist", "PacmAnt-evoluido+alg1")]
# Teste post hoc: chi fixo = 0,5 com feromônio persistente, só nas sementes 2000-2049
CHI05 = "PacmAnt-evoluido+persist+chi05"
PARES_CHI05 = [("PacmAnt-evoluido+persist", CHI05), ("PacmAnt-A2+persist", CHI05)]
SEMENTES_CHI05 = (2000, 2049)
CONFIRMATORIA = ["PacmAnt-evoluido", "PacmAnt-A1", "PacmAnt-A2",
                 "PacmAnt-evoluido+persist", "PacmAnt-A1+persist", "PacmAnt-A2+persist",
                 "PacmAnt-evoluido+alg1", CHI05]
# Jogos esperados por configuração na pasta da rodada confirmatória. Configurações com menos
# jogos (ainda rodando) ficam fora da análise, para não misturar dados parciais.
JOGOS_CONFIRMATORIA = {"": 100, CHI05: 50}
# Variantes de implementação comparadas com a configuração padrão (só pontuação)
PADRAO = "PacmAnt-evoluido"
ORDEM_VARIANTES = ["", "+acs", "+persist", "+acs+persist", "+minimo", "+margem0", "+minimo+margem0",
                   "+minimo+margem0+acs", "+minimo+margem0+persist", "+minimo+margem0+acs+persist"]
# Agentes do gráfico principal, em ordem
PRINCIPAIS = ["Random", "RandomNonRev", "NearestPill", "PacmAnt-evoluido", "PacmAnt-A1", "PacmAnt-A2"]
# Referência do artigo (Tabela III), média de 100 jogos
ARTIGO = {
    ("Random", "RandomGhosts"): 502, ("Random", "Legacy"): 172,
    ("RandomNonRev", "RandomGhosts"): 1471, ("RandomNonRev", "Legacy"): 1522,
    ("NearestPill", "RandomGhosts"): 5901, ("NearestPill", "Legacy"): 5693,
    ("PacmAnt-evoluido", "RandomGhosts"): None, ("PacmAnt-evoluido", "Legacy"): None,
}


def ic95(x):
    """Meia-largura do IC 95% da média (t de Student)."""
    x = np.asarray(x, dtype=float)
    if len(x) < 2:
        return float("nan")
    return stats.t.ppf(0.975, len(x) - 1) * x.std(ddof=1) / np.sqrt(len(x))


def carregar(padrao_pasta):
    arquivos = sorted(glob.glob(os.path.join(RAIZ, "resultados", padrao_pasta, "*.csv")))
    if not arquivos:
        return None
    partes = []
    for a in arquivos:
        d = pd.read_csv(a)
        d["etapa"] = os.path.basename(os.path.dirname(a))
        partes.append(d)
    df = pd.concat(partes, ignore_index=True)
    df["vidas_por_fase"] = df["vidas_perdidas"] / (df["fase"] + 1)
    return df


def resumo(df):
    linhas = []
    for (etapa, agente, fantasma), g in df.groupby(["etapa", "agente", "fantasma"], sort=False):
        p = g["pontuacao"]
        linhas.append({
            "etapa": etapa, "agente": agente, "fantasma": fantasma, "jogos": len(g),
            "media": p.mean(), "dp": p.std(ddof=1), "ic95": ic95(p),
            "mediana": p.median(), "min": p.min(), "max": p.max(),
            "fase_media": g["fase"].mean(),
            "vidas_perdidas": g["vidas_perdidas"].mean(),
            "vidas_por_fase": g["vidas_por_fase"].mean(),
            "ticks": g["ticks"].mean(),
            "ms_medio_tick": g["ms_medio"].mean(), "ms_max_tick": g["ms_max"].max(),
            "estouros_40ms": int(g["estouros"].sum()), "erros": int(g["erros"].sum()),
            "seg_por_jogo": g["segundos"].mean(),
            "pct_fuga": g["pct_fuga"].mean() if "pct_fuga" in g else float("nan"),
        })
    return pd.DataFrame(linhas)


def holm(p):
    """p-valores ajustados pelo método de Holm (descendente) para uma família de testes."""
    p = np.asarray(p, dtype=float)
    m = len(p)
    ordem = np.argsort(p)
    ajustado = np.empty(m)
    maior = 0.0
    for posicao, i in enumerate(ordem):
        maior = max(maior, min(1.0, (m - posicao) * p[i]))
        ajustado[i] = maior
    return ajustado


def mann_whitney(df, familias, variantes):
    """familias: lista de (nome, pares) ou (nome, pares, (semente_ini, semente_fim)).

    Holm é aplicado por família e por métrica. Com o intervalo de sementes, os dois lados do par
    usam só os jogos dessas sementes.
    """
    linhas = []
    for fantasma, gfan in df.groupby("fantasma"):
        combinacoes = [(f[0], f[1], f[2] if len(f) > 2 else None, m)
                       for f in familias for m in ("pontuacao", "vidas_por_fase", "fase")]
        for familia, pares, sementes, metrica in combinacoes:
            gf = gfan if sementes is None else gfan[gfan["semente"].between(sementes[0], sementes[1])]
            inicio = len(linhas)
            for a, b in pares:
                xa = gf.loc[gf["agente"] == a, metrica].to_numpy(dtype=float)
                xb = gf.loc[gf["agente"] == b, metrica].to_numpy(dtype=float)
                if len(xa) == 0 or len(xb) == 0:
                    continue
                u, p = stats.mannwhitneyu(xa, xb, alternative="two-sided")
                linhas.append({
                    "fantasma": fantasma, "familia": familia, "metrica": metrica, "agente_a": a, "agente_b": b,
                    "n_a": len(xa), "n_b": len(xb),
                    "media_a": xa.mean(), "media_b": xb.mean(),
                    "mediana_a": np.median(xa), "mediana_b": np.median(xb),
                    "U": u, "p_valor": p,
                    # probabilidade de um jogo de A superar um de B (empates contam metade)
                    "prob_a_maior_b": u / (len(xa) * len(xb)),
                })
            if len(linhas) > inicio:
                ajustados = holm([l["p_valor"] for l in linhas[inicio:]])
                for l, pa in zip(linhas[inicio:], ajustados):
                    l["p_holm"] = pa
                    l["testes_na_familia"] = len(ajustados)
        # variantes de implementação x configuração padrão (sem ajuste: análise exploratória)
        gf = gfan
        for v in (ORDEM_VARIANTES[1:] if variantes else []):
            xa = gf.loc[gf["agente"] == PADRAO, "pontuacao"].to_numpy(dtype=float)
            xb = gf.loc[gf["agente"] == PADRAO + v, "pontuacao"].to_numpy(dtype=float)
            if len(xa) == 0 or len(xb) == 0:
                continue
            u, p = stats.mannwhitneyu(xa, xb, alternative="two-sided")
            linhas.append({
                "fantasma": fantasma, "familia": "variantes", "metrica": "pontuacao",
                "agente_a": PADRAO, "agente_b": PADRAO + v,
                "n_a": len(xa), "n_b": len(xb), "media_a": xa.mean(), "media_b": xb.mean(),
                "mediana_a": np.median(xa), "mediana_b": np.median(xb),
                "U": u, "p_valor": p, "prob_a_maior_b": u / (len(xa) * len(xb)),
            })
    return pd.DataFrame(linhas)


def rotulo(agente, presentes):
    """Rótulo curto: no gráfico de variantes mostra só o sufixo."""
    if all(p.startswith(PADRAO) for p in presentes):
        return agente[len(PADRAO):] or "padrão"
    return agente.replace("PacmAnt-evoluido", "Fixo").replace("PacmAnt-", "")


def boxplot(df, agentes, fantasma, arquivo, titulo, metrica="pontuacao", eixo="Pontuação por jogo"):
    gf = df[df["fantasma"] == fantasma]
    presentes = [a for a in agentes if (gf["agente"] == a).any()]
    if not presentes:
        return
    dados = [gf.loc[gf["agente"] == a, metrica].to_numpy() for a in presentes]
    fig, ax = plt.subplots(figsize=(max(7, 1.5 * len(presentes)), 5))
    ax.boxplot(dados, showmeans=True, meanprops={"marker": "D", "markerfacecolor": "black",
                                                 "markeredgecolor": "black", "markersize": 5})
    ax.set_xticks(range(1, len(presentes) + 1))
    ax.set_xticklabels([f"{rotulo(a, presentes)}\n(n={len(d)})" for a, d in zip(presentes, dados)],
                       rotation=20, ha="right")
    ax.set_ylabel(eixo)
    ax.set_title(titulo)
    ax.grid(axis="y", alpha=0.3)
    fig.tight_layout()
    fig.savefig(arquivo, dpi=150)
    plt.close(fig)


def para_markdown(res):
    linhas = ["| Rodada | Agente | Fantasma | Jogos | Média ± DP | IC 95% | Mediana | Fase média | Vidas perdidas/fase | % fuga | ms/tick (médio) | s/jogo |",
              "|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in res.itertuples():
        fuga = "" if pd.isna(r.pct_fuga) else f"{r.pct_fuga:.1f}"
        linhas.append(f"| {r.etapa} | {r.agente} | {r.fantasma} | {r.jogos} | {r.media:.0f} ± {r.dp:.0f} | "
                      f"± {r.ic95:.0f} | {r.mediana:.0f} | {r.fase_media:.2f} | {r.vidas_por_fase:.2f} | {fuga} | "
                      f"{r.ms_medio_tick:.3f} | {r.seg_por_jogo:.1f} |")
    return "\n".join(linhas) + "\n"


def analisar(nome, padrao_pasta, pasta_saida, familias, principais, variantes, jogos_esperados=None):
    df = carregar(padrao_pasta)
    if df is None:
        print(f"[{nome}] sem resultados em resultados/{padrao_pasta}")
        return
    incompletas = []
    if jogos_esperados:
        for agente, n in df.groupby("agente").size().items():
            esperado = jogos_esperados.get(agente, jogos_esperados[""])
            if n < esperado:
                incompletas.append(f"{agente} ({n} de {esperado} jogos)")
                df = df[df["agente"] != agente]
    saida = os.path.join(RAIZ, "resultados", pasta_saida)
    os.makedirs(saida, exist_ok=True)
    df.to_csv(os.path.join(saida, "todos_os_jogos.csv"), index=False)

    res = resumo(df)
    res.to_csv(os.path.join(saida, "resumo.csv"), index=False, float_format="%.4f")
    with open(os.path.join(saida, "resumo.md"), "w", encoding="utf-8") as f:
        f.write(para_markdown(res))
        if incompletas:
            f.write("\nConfigurações ainda incompletas, fora desta análise: " + "; ".join(incompletas) + ".\n")

    mw = mann_whitney(df, familias, variantes)
    mw.to_csv(os.path.join(saida, "mann_whitney.csv"), index=False, float_format="%.6g")

    for fantasma in sorted(df["fantasma"].unique()):
        boxplot(df, principais, fantasma, os.path.join(saida, f"boxplot_{fantasma}.png"),
                f"Pontuação contra {fantasma} ({nome})")
        boxplot(df, [a for a in principais if a.startswith("PacmAnt")], fantasma,
                os.path.join(saida, f"boxplot_vidas_{fantasma}.png"),
                f"Vidas perdidas por fase contra {fantasma} ({nome})",
                metrica="vidas_por_fase", eixo="Vidas perdidas / (fase + 1)")
        if variantes:
            lista = [PADRAO + v for v in ORDEM_VARIANTES if (df["agente"] == PADRAO + v).any()]
            if len(lista) > 1:
                boxplot(df, lista, fantasma, os.path.join(saida, f"boxplot_variantes_{fantasma}.png"),
                        f"pac-mAnt evoluído: variantes de implementação contra {fantasma}")

    pd.set_option("display.width", 250)
    print(f"===== rodada {nome} =====")
    if incompletas:
        print("FORA DA ANALISE (incompletas): " + "; ".join(incompletas))
    print(res[["etapa", "agente", "fantasma", "jogos", "media", "dp", "ic95", "mediana", "fase_media", "vidas_por_fase",
               "pct_fuga", "ms_medio_tick", "ms_max_tick", "estouros_40ms", "erros", "seg_por_jogo"]].round(2).to_string(index=False))
    print()
    if len(mw):
        print(mw[["familia", "metrica", "agente_a", "agente_b", "media_a", "media_b", "mediana_a", "mediana_b",
                  "U", "p_valor", "p_holm", "prob_a_maior_b"]].round(4).to_string(index=False))
    print()


def main():
    analisar("exploratória", "etapa*", "analise", [("reinicio", PARES)], PRINCIPAIS, variantes=True)
    analisar("confirmatória", "confirmatoria", "analise_confirmatoria",
             [("reinicio (primaria)", PARES), ("persist (secundaria)", PARES_PERSIST), ("alg1", PARES_ALG1),
              ("chi05 (post hoc, sementes 2000-2049)", PARES_CHI05, SEMENTES_CHI05)],
             CONFIRMATORIA, variantes=False, jogos_esperados=JOGOS_CONFIRMATORIA)


if __name__ == "__main__":
    main()
