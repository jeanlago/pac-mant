package pacmant;

import game.PacManSimulator;
import game.SimulatorConfig;
import game.core.Game;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Roda uma série de jogos sem interface para um par agente x fantasma e grava um CSV
 * com uma linha por jogo.
 *
 * Uso:
 *   java -cp build pacmant.Experimento --agente NOME --fantasma NOME
 *        --sementes 1000-1099 --saida resultados/arquivo.csv [--think 40] [--rotulo NOME]
 *
 * Se o CSV de saída já existir, as sementes já gravadas são puladas (permite retomar).
 * Um processo por par agente x fantasma: o simulador usa um gerador aleatório estático
 * (G.rnd), então não é seguro rodar dois jogos na mesma JVM ao mesmo tempo.
 */
public final class Experimento {

	public static final String CABECALHO =
		"agente,fantasma,semente,pontuacao,fase,vidas_perdidas,ticks,ms_medio,ms_max,estouros,erros,segundos,pct_fuga";

	public static void main(String[] args) throws IOException {
		String agente = null, fantasma = null, saida = null, rotulo = null, pastaTraco = null;
		int semIni = 1000, semFim = 1099, think = 40;

		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			if (a.equals("--agente")) agente = args[++i];
			else if (a.equals("--fantasma")) fantasma = args[++i];
			else if (a.equals("--saida")) saida = args[++i];
			else if (a.equals("--rotulo")) rotulo = args[++i];
			else if (a.equals("--traco")) pastaTraco = args[++i];
			else if (a.equals("--think")) think = Integer.parseInt(args[++i]);
			else if (a.equals("--sementes")) {
				String[] p = args[++i].split("-");
				semIni = Integer.parseInt(p[0]);
				semFim = p.length > 1 ? Integer.parseInt(p[1]) : semIni;
			} else throw new IllegalArgumentException("argumento desconhecido: " + a);
		}
		if (agente == null || fantasma == null || saida == null)
			throw new IllegalArgumentException("uso: --agente A --fantasma F --sementes ini-fim --saida arq.csv");
		if (rotulo == null) rotulo = agente;

		File arq = new File(saida);
		if (arq.getParentFile() != null) arq.getParentFile().mkdirs();
		Set<Integer> feitas = sementesFeitas(arq);
		boolean novo = !arq.exists() || arq.length() == 0;

		// o simulador imprime uma linha por jogo e avisos de tempo; guardamos o stdout original
		PrintStream console = System.out;

		try (PrintWriter csv = new PrintWriter(new OutputStreamWriter(new FileOutputStream(arq, true), StandardCharsets.UTF_8))) {
			if (novo) {
				csv.println(CABECALHO);
				csv.flush();
			}
			for (int semente = semIni; semente <= semFim; semente++) {
				if (feitas.contains(semente)) continue;

				Cronometrado pac = new Cronometrado(Agentes.pacman(agente, 7919L * semente + 13), think);

				SimulatorConfig config = new SimulatorConfig();
				config.visualize = false;
				config.mayBePaused = false;
				config.replay = false;
				config.game.seed = semente;
				config.thinkTimeMillis = think;
				config.pacManController = pac;
				config.ghostsController = Agentes.fantasmas(fantasma);

				// traço por jogada (só para o pac-mAnt): um arquivo por jogo em pastaTraco
				PrintWriter traco = null;
				if (pastaTraco != null && pac.interno() instanceof PacmAnt) {
					new File(pastaTraco).mkdirs();
					traco = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
						new File(pastaTraco, "traco_" + rotulo + "_" + semente + ".csv")), StandardCharsets.UTF_8));
					traco.println(PacmAnt.CABECALHO_TRACO);
					((PacmAnt) pac.interno()).comTraco(traco);
				}

				long t0 = System.nanoTime();
				Game g = PacManSimulator.play(config);
				double seg = (System.nanoTime() - t0) / 1e9;
				if (traco != null) traco.close();

				int pontos = g.getScore();
				// a vida extra é dada ao atingir EXTRA_LIFE_SCORE; o contador de vidas inclui a vida atual
				int vidasTotais = Game.NUM_LIVES + (pontos >= Game.EXTRA_LIFE_SCORE ? 1 : 0);
				int vidasPerdidas = vidasTotais - Math.max(0, g.getLivesRemaining());

				// % de ticks em que o pac-mAnt seguiu a exploradora (fuga); vazio para os outros agentes
				String pctFuga = pac.interno() instanceof PacmAnt
					? String.format(Locale.ROOT, "%.2f", 100.0 * ((PacmAnt) pac.interno()).fracaoTicksEmFuga()) : "";

				String linha = String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d,%.4f,%.3f,%d,%d,%.2f,%s",
					rotulo, fantasma, semente, pontos, g.getCurLevel(), vidasPerdidas, g.getTotalTime(),
					pac.msMedio(), pac.msMax(), pac.estouros(), pac.erros(), seg, pctFuga);
				csv.println(linha);
				csv.flush();
				console.println(linha);
			}
		}
		// as threads do simulador são encerradas ao fim de cada jogo, mas garantimos a saída
		System.exit(0);
	}

	private static Set<Integer> sementesFeitas(File arq) throws IOException {
		Set<Integer> s = new HashSet<Integer>();
		if (!arq.exists()) return s;
		try (BufferedReader br = new BufferedReader(new FileReader(arq, StandardCharsets.UTF_8))) {
			String l = br.readLine(); // cabeçalho
			while ((l = br.readLine()) != null) {
				String[] p = l.split(",");
				if (p.length >= 3) {
					try {
						s.add(Integer.parseInt(p[2].trim()));
					} catch (NumberFormatException e) {
						// linha truncada: ignora
					}
				}
			}
		}
		return s;
	}
}
