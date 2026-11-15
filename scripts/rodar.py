"""Dispara os experimentos: um processo Java por par agente x fantasma.

Exemplos (na raiz do projeto):
    python scripts/rodar.py --etapa 1
    python scripts/rodar.py --etapa 2 --paralelo 4
    python scripts/rodar.py --etapa confirmatoria
    python scripts/rodar.py --etapa 4        (teste final da Etapa 4; exige resultados/ils/melhor.json)
    python scripts/rodar.py --agentes PacmAnt-evoluido --fantasmas Legacy --sementes 1000-1009 --saida resultados/teste

Cada par grava resultados/<pasta>/<agente>__<fantasma>.csv (uma linha por jogo) e um .log.
Se o CSV já existir, as sementes já gravadas são puladas, então é seguro reexecutar.
"""
import argparse
import glob
import json
import os
import subprocess
import sys
import time

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Escopo final: 30 jogos (sementes 1000-1029), apenas contra Legacy.
# (A Etapa 0 foi rodada antes da redução de escopo, com 100 jogos e os dois fantasmas.)
FANTASMAS = ["Legacy"]
SEMENTES = "1000-1029"

ETAPAS = {
    "0": ["Random", "RandomNonRev", "NearestPill"],
    # Etapa 1: preset evoluído e as variantes de teste (flags) registradas em DECISOES.md
    "1": ["PacmAnt-evoluido",
          "PacmAnt-evoluido+acs", "PacmAnt-evoluido+persist", "PacmAnt-evoluido+acs+persist",
          "PacmAnt-evoluido+minimo", "PacmAnt-evoluido+margem0", "PacmAnt-evoluido+minimo+margem0",
          # flags pedidas na especificação aplicadas sobre a leitura literal (+minimo+margem0)
          "PacmAnt-evoluido+minimo+margem0+acs", "PacmAnt-evoluido+minimo+margem0+persist",
          "PacmAnt-evoluido+minimo+margem0+acs+persist"],
    "2": ["PacmAnt-A1", "PacmAnt-A2"],
    # Rodada confirmatória da Etapa 2: sementes novas, 100 jogos, com e sem feromônio persistente
    "confirmatoria": ["PacmAnt-evoluido", "PacmAnt-A1", "PacmAnt-A2",
                      "PacmAnt-evoluido+persist", "PacmAnt-A1+persist", "PacmAnt-A2+persist"],
}
SEMENTES_CONFIRMATORIA = "2000-2099"

# Etapa 4, teste final: sementes novas (3000-3099), que não entram em nenhuma rodada anterior nem no ILS.
# O preset do ILS é lido de resultados/ils/melhor.json (gravado por scripts/ils.py) e roda com o
# rótulo PacmAnt-ils+alg1.
ETAPAS["4"] = ["PacmAnt-evoluido+alg1", "PacmAnt-ils+alg1", "PacmAnt-A2+alg1+norm"]
SEMENTES_ETAPA4 = "3000-3099"
ROTULO_ILS = "PacmAnt-ils+alg1"
BASE_ILS = "PacmAnt-evoluido+alg1"


def params_ils():
    arq = os.path.join(RAIZ, "resultados", "ils", "melhor.json")
    if not os.path.exists(arq):
        sys.exit("falta resultados/ils/melhor.json: rode scripts/ils.py antes do teste final")
    with open(arq, encoding="utf-8") as f:
        return json.load(f)["params"]


def achar_java():
    candidatos = sorted(glob.glob(os.path.join(RAIZ, "tools", "jdk-*", "bin", "java*")))
    for c in candidatos:
        if os.path.basename(c) in ("java", "java.exe"):
            return c
    return "java"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--etapa", choices=sorted(ETAPAS), help="conjunto predefinido de agentes")
    ap.add_argument("--agentes", nargs="*", default=None)
    ap.add_argument("--fantasmas", nargs="*", default=FANTASMAS)
    ap.add_argument("--sementes", default=None)
    ap.add_argument("--saida", default=None, help="pasta de saída (padrão: resultados/etapaN)")
    ap.add_argument("--paralelo", type=int, default=6, help="processos simultâneos")
    ap.add_argument("--think", type=int, default=40)
    args = ap.parse_args()

    agentes = args.agentes if args.agentes else ETAPAS.get(args.etapa or "", [])
    if not agentes:
        ap.error("informe --etapa ou --agentes")
    if args.sementes is None:
        args.sementes = {"confirmatoria": SEMENTES_CONFIRMATORIA, "4": SEMENTES_ETAPA4}.get(args.etapa, SEMENTES)
    nome_pasta = "confirmatoria" if args.etapa == "confirmatoria" else "etapa" + (args.etapa or "X")
    pasta = args.saida or os.path.join("resultados", nome_pasta)
    pasta = os.path.join(RAIZ, pasta) if not os.path.isabs(pasta) else pasta
    os.makedirs(pasta, exist_ok=True)

    java = achar_java()
    fila = [(a, f) for a in agentes for f in args.fantasmas]
    ativos = []
    inicio = time.time()
    falhas = 0

    def lancar(a, f):
        base = os.path.join(pasta, f"{a}__{f}")
        log = open(base + ".log", "a", encoding="utf-8")
        cmd = [java, "-Xms256m", "-Xmx512m", "-cp", os.path.join(RAIZ, "build"), "pacmant.Experimento",
               "--agente", a, "--fantasma", f, "--sementes", args.sementes,
               "--saida", base + ".csv", "--think", str(args.think)]
        if a == ROTULO_ILS:   # mesmo agente do Fixo+alg1, com os parâmetros achados pelo ILS
            cmd[cmd.index("--agente") + 1] = BASE_ILS
            cmd += ["--rotulo", ROTULO_ILS, "--params", params_ils()]
        p = subprocess.Popen(cmd, cwd=RAIZ, stdout=log, stderr=subprocess.STDOUT)
        print(f"[{time.time() - inicio:7.0f}s] iniciou  {a} x {f}", flush=True)
        return (p, a, f, log)

    while fila or ativos:
        while fila and len(ativos) < args.paralelo:
            ativos.append(lancar(*fila.pop(0)))
        time.sleep(1.0)
        for item in list(ativos):
            p, a, f, log = item
            if p.poll() is not None:
                log.close()
                ativos.remove(item)
                ok = p.returncode == 0
                falhas += 0 if ok else 1
                print(f"[{time.time() - inicio:7.0f}s] {'terminou' if ok else 'FALHOU  '} {a} x {f}"
                      f" (codigo {p.returncode})", flush=True)

    print(f"fim: {time.time() - inicio:.0f}s, falhas: {falhas}", flush=True)
    sys.exit(1 if falhas else 0)


if __name__ == "__main__":
    main()
