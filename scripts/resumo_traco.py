"""Resume os traços por jogada do teste post hoc de chi (eq. 16 na melhor coletora).

Uso (na raiz do projeto):
    .venv/Scripts/python scripts/resumo_traco.py

Lê resultados/posthoc_chi/traco_<agente>_<semente>.csv (uma linha por tick) e os CSVs de jogos da
mesma pasta, e grava resumo_traco.csv e resumo_traco.md com uma linha por jogo e uma por agente.
"""
import glob
import os
import re

import numpy as np
import pandas as pd

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PASTA = os.path.join(RAIZ, "resultados", "posthoc_chi")


def num(x):
    """Formata números de ordens de grandeza muito diferentes."""
    if pd.isna(x):
        return "NaN"
    if np.isinf(x):
        return "inf"
    if x != 0 and (abs(x) >= 1e5 or abs(x) < 1e-2):
        return f"{x:.3g}"
    return f"{x:.2f}"


def media_segura(x):
    """Média de valores não negativos que podem chegar perto do limite do double (1,8e308).

    A soma direta estoura; a conta é feita em logaritmo. Zeros entram como zero.
    """
    x = np.asarray(x, dtype=float)
    if len(x) == 0:
        return float("nan")
    pos = x[x > 0]
    if len(pos) == 0:
        return 0.0
    logs = np.log10(pos)
    m = logs.max()
    log_media = m + np.log10(np.sum(10.0 ** (logs - m))) - np.log10(len(x))
    return 10.0 ** log_media if log_media < 308 else float("inf")


def primeiro_tick(t, coluna, limite):
    """Primeiro tick em que a coluna passa do limite (NaN se nunca passa)."""
    acima = t.loc[t[coluna] > limite, "tick"]
    return float(acima.iloc[0]) if len(acima) else float("nan")


def main():
    pontos = {}
    for arq in glob.glob(os.path.join(PASTA, "*__*.csv")):
        for r in pd.read_csv(arq).itertuples():
            pontos[(r.agente, r.semente)] = (r.pontuacao, r.fase)

    linhas = []
    for arq in sorted(glob.glob(os.path.join(PASTA, "traco_*.csv"))):
        m = re.match(r"traco_(.+)_(\d+)\.csv$", os.path.basename(arq))
        agente, semente = m.group(1), int(m.group(2))
        t = pd.read_csv(arq)
        finito = np.isfinite(t["termo_chi"]) & np.isfinite(t["termo_pilulas"]) & np.isfinite(t["tau_e_max"])
        tf = t[finito]
        pts, fase = pontos.get((agente, semente), (float("nan"), float("nan")))
        linhas.append({
            "agente": agente, "semente": semente, "pontuacao": pts, "fase": fase, "ticks": len(t),
            "ticks_nao_finitos": int((~finito).sum()),
            "chi_medio": t["chi"].mean(),
            # médias sobre os ticks com valores finitos (os não finitos são contados à parte)
            "termo_pilulas_medio": tf["termo_pilulas"].mean(),
            "termo_pilulas_mediana": tf["termo_pilulas"].median(),
            "soma_tau_e_media": media_segura(tf["soma_tau_e"]),
            "soma_tau_e_mediana": tf["soma_tau_e"].median(),
            "termo_chi_medio": media_segura(tf["termo_chi"]),
            "termo_chi_mediana": tf["termo_chi"].median(),
            # fração dos ticks em que o termo colaborativo é maior que o das pílulas
            "frac_ticks_chi_domina": (t["termo_chi"] > t["termo_pilulas"]).mean(),
            "tau_e_max_jogo": t["tau_e_max"].max(skipna=False),
            "tau_c_max_jogo": t["tau_c_max"].max(skipna=False),
            "tau_e_max_mediana": t["tau_e_max"].median(),
            "tick_tau_e_passa_1e6": primeiro_tick(t, "tau_e_max", 1e6),
            "tick_tau_e_passa_1e100": primeiro_tick(t, "tau_e_max", 1e100),
        })
    if not linhas:
        print("sem traços em", PASTA)
        return
    jogos = pd.DataFrame(linhas)
    jogos.to_csv(os.path.join(PASTA, "resumo_traco.csv"), index=False, float_format="%.6g")

    with open(os.path.join(PASTA, "resumo_traco.md"), "w", encoding="utf-8") as f:
        f.write("Valores por jogada na melhor coletora do tick (eq. 16). Médias e medianas sobre os ticks "
                "com valores finitos; um double vai até 1,8e308, e os ticks em que a soma deu infinito "
                "estão na coluna \"ticks não finitos\".\n\n")
        f.write("| Agente | Semente | Pontuação | Ticks | χ médio | Pílulas (média) | Pílulas (mediana) | "
                "χ·Στᵉ (média) | χ·Στᵉ (mediana) | Στᵉ (mediana) | % ticks com χ·Στᵉ > pílulas | "
                "τᵉ máx. no jogo | τᶜ máx. no jogo | Ticks não finitos | 1º tick com τᵉ > 1e6 |\n")
        f.write("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
        for r in jogos.itertuples():
            passa = "nunca" if pd.isna(r.tick_tau_e_passa_1e6) else f"{r.tick_tau_e_passa_1e6:.0f}"
            f.write(f"| {r.agente} | {r.semente} | {r.pontuacao:.0f} | {r.ticks} | {r.chi_medio:.3f} | "
                    f"{num(r.termo_pilulas_medio)} | {num(r.termo_pilulas_mediana)} | "
                    f"{num(r.termo_chi_medio)} | {num(r.termo_chi_mediana)} | {num(r.soma_tau_e_mediana)} | "
                    f"{100 * r.frac_ticks_chi_domina:.1f} | "
                    f"{num(r.tau_e_max_jogo)} | {num(r.tau_c_max_jogo)} | {r.ticks_nao_finitos} | {passa} |\n")
        f.write("\nPor agente (5 jogos):\n\n")
        f.write("| Agente | Pontuação média | Pílulas (média) | χ·Στᵉ (mediana dos jogos) | "
                "τᵉ máx. (maior dos jogos) | τᶜ máx. (maior dos jogos) | Ticks não finitos (%) |\n")
        f.write("|---|---|---|---|---|---|---|\n")
        for agente, g in jogos.groupby("agente", sort=False):
            f.write(f"| {agente} | {g.pontuacao.mean():.0f} | {num(g.termo_pilulas_medio.mean())} | "
                    f"{num(g.termo_chi_mediana.median())} | {num(g.tau_e_max_jogo.max(skipna=False))} | "
                    f"{num(g.tau_c_max_jogo.max(skipna=False))} | "
                    f"{100 * g.ticks_nao_finitos.sum() / g.ticks.sum():.1f} |\n")

    pd.set_option("display.width", 280)
    print(jogos[["agente", "semente", "pontuacao", "ticks", "chi_medio", "termo_pilulas_medio", "termo_pilulas_mediana",
                 "termo_chi_medio", "termo_chi_mediana", "soma_tau_e_mediana", "frac_ticks_chi_domina",
                 "tau_e_max_jogo", "tau_c_max_jogo", "ticks_nao_finitos", "tick_tau_e_passa_1e6",
                 "tick_tau_e_passa_1e100"]].to_string(index=False))


if __name__ == "__main__":
    main()
