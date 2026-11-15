"""Calibração dos parâmetros do pac-mAnt fiel ao Algoritmo 1 (+alg1) por busca local iterada (ILS).

Uso (na raiz do projeto):
    python scripts/ils.py                      roda até o orçamento (150 avaliações ou 3 horas)
    python scripts/ils.py --max-avaliacoes 1   só a solução inicial (serve para medir o tempo)

O desenho está registrado em HIPOTESES.md (Etapa 4):
    solução      12 parâmetros do preset (chi fica 0), em uma grade ancorada no preset evoluído
    avaliação    média da pontuação em 24 sementes fixas (5000-5023) contra Legacy
    busca local  primeira melhora; vizinhança = +-1 passo em um parâmetro, em ordem sorteada
    perturbação  3 parâmetros sorteados, cada um deslocado em 2 ou 3 passos
    aceitação    só se o novo ótimo local for estritamente melhor que a solução atual
    orçamento    150 avaliações ou 3 horas (soma das durações das avaliações)

Arquivos gravados em resultados/ils/:
    avaliacoes.csv   uma linha por avaliação (iteração, fase da busca, parâmetros, média, ...)
    jogos.csv        uma linha por jogo, com o número da avaliação
    melhor.json      melhor solução global (lida por scripts/rodar.py --etapa 4)

A execução pode ser interrompida e retomada: os sorteios usam uma semente fixa e as avaliações já
gravadas não são refeitas, então o script refaz o mesmo caminho sem custo até o ponto em que parou.
"""
import argparse
import csv
import glob
import json
import math
import os
import random
import statistics
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PASTA = os.path.join(RAIZ, "resultados", "ils")
ARQ_AVAL = os.path.join(PASTA, "avaliacoes.csv")
ARQ_JOGOS = os.path.join(PASTA, "jogos.csv")
ARQ_MELHOR = os.path.join(PASTA, "melhor.json")

AGENTE = "PacmAnt-evoluido+alg1"
FANTASMA = "Legacy"
SEMENTES_TREINO = list(range(5000, 5024))   # 24 sementes fixas; as do teste final (3000-3099) não entram aqui
SEMENTE_ILS = 4                             # semente dos sorteios do próprio ILS
MAX_AVALIACOES = 150
MAX_SEGUNDOS = 3 * 3600

# (nome, valor no preset evoluído, passo, limite inferior, limite superior, casas decimais)
PARAMETROS = [
    ("maxAnt", 19, 2, 5, 39, 0),
    ("maxDist", 100, 10, 20, 150, 0),
    ("phi", 79, 10, 9, 149, 0),
    ("dMin", 13, 2, 3, 41, 0),
    ("mu", 60, 5, 10, 100, 0),
    ("rhoC", 0.22, 0.05, 0.02, 0.97, 2),
    ("rhoE", 0.70, 0.05, 0.05, 1.00, 2),
    ("alphaC", 0.23, 0.05, 0.03, 0.98, 2),
    ("alphaE", 0.11, 0.05, 0.01, 0.96, 2),
    ("omega", 0.72, 0.05, 0.02, 0.97, 2),
    ("q0C", 0.37, 0.05, 0.02, 0.97, 2),
    ("q0E", 0.69, 0.05, 0.04, 0.99, 2),
]
NOMES = [p[0] for p in PARAMETROS]
# a solução é uma tupla de inteiros k (passos a partir do preset); limites de k por parâmetro
K_MIN = [math.ceil((lo - v0) / passo - 1e-9) for _, v0, passo, lo, hi, _ in PARAMETROS]
K_MAX = [math.floor((hi - v0) / passo + 1e-9) for _, v0, passo, lo, hi, _ in PARAMETROS]

COLUNAS_AVAL = (["avaliacao", "iteracao", "fase_busca"] + NOMES +
                ["media", "dp", "mediana", "min", "max", "fase_media", "vidas_por_fase", "jogos",
                 "estouros_40ms", "erros", "segundos", "segundos_acumulados", "melhor_media_ate_aqui"])


class OrcamentoEsgotado(Exception):
    pass


def valores(sol):
    """Valores dos parâmetros de uma solução, como texto (inteiros sem ponto, reais com 2 casas)."""
    out = []
    for k, (_, v0, passo, _, _, casas) in zip(sol, PARAMETROS):
        v = v0 + k * passo
        out.append(str(int(round(v))) if casas == 0 else f"{v:.{casas}f}")
    return out


def texto_params(sol):
    return ",".join(f"{n}={v}" for n, v in zip(NOMES, valores(sol)))


def achar_java():
    for c in sorted(glob.glob(os.path.join(RAIZ, "tools", "jdk-*", "bin", "java*"))):
        if os.path.basename(c) in ("java", "java.exe"):
            return c
    return "java"


class Ils:
    def __init__(self, paralelo, max_avaliacoes, max_segundos):
        self.paralelo = paralelo
        self.max_avaliacoes = max_avaliacoes
        self.max_segundos = max_segundos
        self.java = achar_java()
        self.rng = random.Random(SEMENTE_ILS)
        self.cache = {}            # tupla de valores (texto) -> média
        self.n_aval = 0
        self.segundos = 0.0
        self.melhor = None         # (média, número da avaliação, params como texto)
        os.makedirs(PASTA, exist_ok=True)
        self.carregar()

    # ---------------- registro ----------------

    def carregar(self):
        """Lê as avaliações já gravadas (retomada)."""
        if not os.path.exists(ARQ_AVAL):
            return
        with open(ARQ_AVAL, encoding="utf-8", newline="") as f:
            for r in csv.DictReader(f):
                chave = tuple(r[n] for n in NOMES)
                media = float(r["media"])
                self.cache[chave] = media
                self.n_aval = int(r["avaliacao"])
                self.segundos = float(r["segundos_acumulados"])
                if self.melhor is None or media > self.melhor[0]:
                    self.melhor = (media, self.n_aval, ",".join(f"{n}={r[n]}" for n in NOMES))
        print(f"retomando: {self.n_aval} avaliacoes ja gravadas, {self.segundos:.0f}s acumulados", flush=True)

    def gravar_melhor(self, motivo):
        if self.melhor is None:
            return
        with open(ARQ_MELHOR, "w", encoding="utf-8") as f:
            json.dump({
                "params": self.melhor[2], "media_treino": self.melhor[0], "avaliacao": self.melhor[1],
                "agente_base": AGENTE, "fantasma": FANTASMA,
                "sementes_treino": f"{SEMENTES_TREINO[0]}-{SEMENTES_TREINO[-1]}",
                "avaliacoes": self.n_aval, "segundos": round(self.segundos, 1), "parada": motivo,
            }, f, indent=2)

    # ---------------- avaliação ----------------

    def jogar(self, semente, params, n):
        """Um jogo em um processo próprio; devolve a linha do CSV como dicionário."""
        saida = os.path.join(PASTA, "tmp", f"aval{n:03d}_{semente}.csv")
        if os.path.exists(saida):
            os.remove(saida)
        cmd = [self.java, "-Xmx512m", "-cp", os.path.join(RAIZ, "build"), "pacmant.Experimento",
               "--agente", AGENTE, "--fantasma", FANTASMA, "--sementes", f"{semente}-{semente}",
               "--saida", saida, "--rotulo", f"ils-{n:03d}", "--params", params]
        for tentativa in range(2):
            p = subprocess.run(cmd, cwd=RAIZ, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
            if p.returncode == 0 and os.path.exists(saida):
                with open(saida, encoding="utf-8", newline="") as f:
                    linhas = list(csv.DictReader(f))
                if len(linhas) == 1:
                    os.remove(saida)
                    return linhas[0]
            print(f"  [aviso] jogo da semente {semente} falhou (tentativa {tentativa + 1}): {p.stderr[-300:]}", flush=True)
        raise RuntimeError(f"jogo da semente {semente} falhou duas vezes")

    def avaliar(self, sol, iteracao, fase_busca):
        """Média da pontuação nas sementes de treino. Soluções já avaliadas não gastam orçamento."""
        vals = valores(sol)
        chave = tuple(vals)
        if chave in self.cache:
            return self.cache[chave]
        if self.n_aval >= self.max_avaliacoes:
            raise OrcamentoEsgotado(f"{self.n_aval} avaliacoes")
        if self.segundos >= self.max_segundos:
            raise OrcamentoEsgotado(f"{self.segundos:.0f}s")

        n = self.n_aval + 1
        params = texto_params(sol)
        os.makedirs(os.path.join(PASTA, "tmp"), exist_ok=True)
        t0 = time.time()
        with ThreadPoolExecutor(max_workers=self.paralelo) as pool:
            jogos = list(pool.map(lambda s: self.jogar(s, params, n), SEMENTES_TREINO))
        dur = time.time() - t0

        pontos = [int(j["pontuacao"]) for j in jogos]
        media = statistics.fmean(pontos)
        self.n_aval = n
        self.segundos += dur
        self.cache[chave] = media
        if self.melhor is None or media > self.melhor[0]:
            self.melhor = (media, n, params)

        novo = not os.path.exists(ARQ_JOGOS)
        with open(ARQ_JOGOS, "a", encoding="utf-8", newline="") as f:
            w = csv.writer(f)
            if novo:
                w.writerow(["avaliacao"] + list(jogos[0].keys()))
            for j in jogos:
                w.writerow([n] + list(j.values()))

        linha = dict(zip(NOMES, vals))
        linha.update({
            "avaliacao": n, "iteracao": iteracao, "fase_busca": fase_busca,
            "media": f"{media:.2f}", "dp": f"{statistics.stdev(pontos):.2f}",
            "mediana": f"{statistics.median(pontos):.1f}", "min": min(pontos), "max": max(pontos),
            "fase_media": f"{statistics.fmean(int(j['fase']) for j in jogos):.3f}",
            "vidas_por_fase": f"{statistics.fmean(int(j['vidas_perdidas']) / (int(j['fase']) + 1) for j in jogos):.3f}",
            "jogos": len(jogos),
            "estouros_40ms": sum(int(j["estouros"]) for j in jogos), "erros": sum(int(j["erros"]) for j in jogos),
            "segundos": f"{dur:.1f}", "segundos_acumulados": f"{self.segundos:.1f}",
            "melhor_media_ate_aqui": f"{self.melhor[0]:.2f}",
        })
        novo = not os.path.exists(ARQ_AVAL)
        with open(ARQ_AVAL, "a", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=COLUNAS_AVAL)
            if novo:
                w.writeheader()
            w.writerow(linha)
        self.gravar_melhor("em andamento")
        print(f"aval {n:3d} | it {iteracao:2d} {fase_busca:11s} | media {media:8.0f} | melhor {self.melhor[0]:8.0f} "
              f"| {dur:5.1f}s (total {self.segundos / 60:5.1f} min) | {params}", flush=True)
        return media

    # ---------------- busca ----------------

    def vizinhos(self, sol):
        viz = []
        for i in range(len(sol)):
            for d in (-1, 1):
                k = sol[i] + d
                if K_MIN[i] <= k <= K_MAX[i]:
                    viz.append(sol[:i] + (k,) + sol[i + 1:])
        return viz

    def busca_local(self, sol, f_sol, iteracao):
        """Primeira melhora: aceita o primeiro vizinho estritamente melhor e recomeça a partir dele."""
        melhorou = True
        while melhorou:
            melhorou = False
            viz = self.vizinhos(sol)
            self.rng.shuffle(viz)
            for v in viz:
                f_v = self.avaliar(v, iteracao, "busca_local")
                if f_v > f_sol:
                    sol, f_sol = v, f_v
                    melhorou = True
                    break
        return sol, f_sol

    def perturbar(self, sol):
        nova = list(sol)
        for i in self.rng.sample(range(len(sol)), 3):
            passos = self.rng.choice([2, 3])
            sinal = self.rng.choice([-1, 1])
            k = sol[i] + sinal * passos
            if not K_MIN[i] <= k <= K_MAX[i]:
                k = sol[i] - sinal * passos
            nova[i] = min(K_MAX[i], max(K_MIN[i], k))
        return tuple(nova)

    def rodar(self):
        motivo = "orcamento"
        try:
            atual = tuple(0 for _ in PARAMETROS)          # solução inicial: preset evoluído
            f_atual = self.avaliar(atual, 0, "inicial")
            atual, f_atual = self.busca_local(atual, f_atual, 0)
            print(f"it  0: otimo local {f_atual:.0f} | {texto_params(atual)}", flush=True)
            for it in range(1, 10000):
                cand = self.perturbar(atual)
                f_cand = self.avaliar(cand, it, "perturbacao")
                cand, f_cand = self.busca_local(cand, f_cand, it)
                aceita = f_cand > f_atual
                print(f"it {it:2d}: otimo local {f_cand:.0f} contra atual {f_atual:.0f} -> "
                      f"{'aceita' if aceita else 'rejeita'} | {texto_params(cand)}", flush=True)
                if aceita:
                    atual, f_atual = cand, f_cand
            motivo = "limite de iteracoes"
        except OrcamentoEsgotado as e:
            motivo = f"orcamento esgotado ({e})"
        self.gravar_melhor(motivo)
        print(f"fim: {motivo}; {self.n_aval} avaliacoes, {self.segundos / 60:.1f} min", flush=True)
        print(f"melhor: media {self.melhor[0]:.0f} na avaliacao {self.melhor[1]} | {self.melhor[2]}", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--paralelo", type=int, default=12, help="jogos simultâneos (um processo por jogo)")
    ap.add_argument("--max-avaliacoes", type=int, default=MAX_AVALIACOES)
    ap.add_argument("--max-horas", type=float, default=MAX_SEGUNDOS / 3600)
    args = ap.parse_args()
    Ils(args.paralelo, args.max_avaliacoes, args.max_horas * 3600).rodar()
    sys.exit(0)


if __name__ == "__main__":
    main()
