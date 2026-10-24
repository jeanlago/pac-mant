package pacmant;

import game.PacManSimulator.GameConfig;
import game.controllers.ghosts.IGhostsController;
import game.controllers.pacman.IPacManController;
import game.core.G;
import game.core.Game;
import game.core._G_;

import java.util.Locale;
import java.util.Random;

/**
 * Ferramenta de diagnóstico: roda jogos num laço síncrono (sem threads nem limite de tempo)
 * e resume como o agente se comporta: tempo por fase, fração de ticks em fuga, contexto das mortes.
 * Não é usada nos experimentos; serve para entender o comportamento.
 *
 * Uso: java -cp build pacmant.Diagnostico AGENTE FANTASMA semIni semFim [-v]
 */
public final class Diagnostico {

	public static void main(String[] args) {
		String agente = args[0], fantasma = args[1];
		int ini = Integer.parseInt(args[2]), fim = Integer.parseInt(args[3]);
		boolean verboso = args.length > 4 && args[4].equals("-v");

		long somaPontos = 0, somaTicks = 0, somaFases = 0;
		long mortes = 0, mortesEmFuga = 0, mortesPosComestivel = 0, fasesPorTempo = 0, fasesLimpas = 0;
		long ticksFuga = 0, ticksAnt = 0, reversoes = 0;
		double somaMortas = 0;
		int jogos = 0;

		for (int semente = ini; semente <= fim; semente++) {
			G.rnd = new Random(semente);
			GameConfig gc = new GameConfig();
			gc.seed = semente;
			_G_ game = new _G_();
			game.newGame(gc);
			IPacManController pac = Agentes.pacman(agente, 7919L * semente + 13);
			IGhostsController gh = Agentes.fantasmas(fantasma);
			pac.reset(game);
			gh.reset(game);
			PacmAnt ant = pac instanceof PacmAnt ? (PacmAnt) pac : null;

			int ultimoNivel = 0;
			int ticksComestivelRecente = 999;
			int ultimaDir = -1;
			while (!game.gameOver()) {
				pac.tick(game.copy(), 0);
				gh.tick(game, 0);
				int vidas = game.getLivesRemaining();
				int nivel = game.getCurLevel();
				int tempoNivel = game.getLevelTime();
				int pilulas = game.getNumActivePills() + game.getNumActivePowerPills();
				boolean algumComestivel = false;
				int perto = 999;
				for (int g = 0; g < 4; g++) {
					if (game.isEdible(g)) algumComestivel = true;
					else if (game.getLairTime(g) == 0) {
						int d = game.getPathDistance(game.getCurPacManLoc(), game.getCurGhostLoc(g));
						if (d < perto) perto = d;
					}
				}
				ticksComestivelRecente = algumComestivel ? 0 : ticksComestivelRecente + 1;
				if (ant != null) {
					ticksAnt++;
					if (ant.ultimoFoiFuga) ticksFuga++;
					somaMortas += ant.fracaoMortas();
				}

				game.advanceGame(pac.getAction().clone(), gh.getActions().clone());

				int dir = game.getCurPacManDir();
				if (ultimaDir >= 0 && dir == game.getReverse(ultimaDir)) reversoes++;
				ultimaDir = dir;

				if (game.getCurLevel() != nivel) {
					if (tempoNivel + 1 >= Game.LEVEL_LIMIT) fasesPorTempo++;
					else fasesLimpas++;
					if (verboso)
						System.out.printf(Locale.ROOT, "  sem %d: fase %d terminou em %d ticks (%s), pontos %d%n",
							semente, nivel, tempoNivel + 1, tempoNivel + 1 >= Game.LEVEL_LIMIT ? "tempo" : "limpa", game.getScore());
					pac.nextLevel(game.copy());
					gh.nextLevel(game.copy());
					ultimaDir = -1;
				}
				if (game.getLivesRemaining() < vidas || game.gameOver() && game.getLivesRemaining() <= 0) {
					mortes++;
					boolean emFuga = ant != null && ant.ultimoFoiFuga;
					if (emFuga) mortesEmFuga++;
					if (ticksComestivelRecente < 15) mortesPosComestivel++;
					if (verboso)
						System.out.printf(Locale.ROOT,
							"  sem %d: morte no tick %d (fase %d, t=%d), modo=%s, fantasma mais perto=%d, mortas=%.2f, pilulas restantes=%d, comestivel ha %d ticks%n",
							semente, game.getTotalTime(), nivel, tempoNivel, emFuga ? "fuga" : "coleta", perto,
							ant != null ? ant.fracaoMortas() : -1.0, pilulas, ticksComestivelRecente);
					pac.killed();
					ultimaDir = -1;
				}
				ultimoNivel = game.getCurLevel();
			}
			jogos++;
			somaPontos += game.getScore();
			somaTicks += game.getTotalTime();
			somaFases += ultimoNivel;
			if (verboso)
				System.out.printf(Locale.ROOT, "sem %d: pontos %d, fase %d, ticks %d%n", semente, game.getScore(), ultimoNivel, game.getTotalTime());
		}

		System.out.printf(Locale.ROOT, "%s x %s (%d jogos): pontos %.0f, fase %.2f, ticks %.0f%n",
			agente, fantasma, jogos, (double) somaPontos / jogos, (double) somaFases / jogos, (double) somaTicks / jogos);
		System.out.printf(Locale.ROOT, "  fases limpas %d, fases por tempo %d, reversoes por 100 ticks %.1f%n",
			fasesLimpas, fasesPorTempo, 100.0 * reversoes / Math.max(1, somaTicks));
		System.out.printf(Locale.ROOT, "  mortes %d: em fuga %d, em coleta %d, logo apos fantasma comestivel %d%n",
			mortes, mortesEmFuga, mortes - mortesEmFuga, mortesPosComestivel);
		if (ticksAnt > 0)
			System.out.printf(Locale.ROOT, "  ticks em fuga %.1f%%, fracao media de exploradoras mortas %.2f%n",
				100.0 * ticksFuga / ticksAnt, somaMortas / ticksAnt);
	}
}
