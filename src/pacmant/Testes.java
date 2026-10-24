package pacmant;

import game.PacManSimulator;
import game.SimulatorConfig;
import game.controllers.ghosts.examples.Legacy;
import game.controllers.pacman.PacManControllerBase;
import game.core.Game;

import java.util.Random;

/** Verificações rápidas de consistência. Uso: java -cp build pacmant.Testes */
public final class Testes {

	/** Confere que PacmAnt.distSemReverter devolve o mesmo que Game.getGhostPathDistance. */
	static final class ConfereDistancia extends PacManControllerBase {
		final PacmAnt interno = new PacmAnt(ParametrosAnt.evoluido(), 1);
		final Random rnd = new Random(7);
		long comparacoes, diferencas;

		@Override
		public void reset(Game game) {
			super.reset(game);
			interno.reset(game);
		}

		@Override
		public void tick(Game game, long timeDue) {
			for (int g = 0; g < Game.NUM_GHOSTS; g++) {
				if (game.getLairTime(g) > 0) continue;
				for (int i = 0; i < 20; i++) {
					int no = rnd.nextInt(game.getNumberOfNodes());
					if (game.getNumNeighbours(no) == 0) continue;
					int a = game.getGhostPathDistance(g, no);
					int b = PacmAnt.distSemReverter(game, g, no, Integer.MAX_VALUE);
					comparacoes++;
					if (a != b) diferencas++;
				}
			}
			interno.tick(game, timeDue);
			pacman.set(interno.getAction().get());
		}
	}

	public static void main(String[] args) {
		ConfereDistancia c = new ConfereDistancia();
		SimulatorConfig config = new SimulatorConfig();
		config.visualize = false;
		config.mayBePaused = false;
		config.replay = false;
		config.game.seed = 1000;
		config.thinkTimeMillis = 40;
		config.pacManController = c;
		config.ghostsController = new Legacy();
		Game g = PacManSimulator.play(config);
		System.out.println("distancia sem reverter: " + c.comparacoes + " comparacoes, " + c.diferencas + " diferencas");
		System.out.println("jogo de teste: pontuacao " + g.getScore() + ", fase " + g.getCurLevel() + ", ticks " + g.getTotalTime());
		System.exit(c.diferencas == 0 ? 0 : 1);
	}
}
