package pacmant;

import game.controllers.pacman.IPacManController;
import game.controllers.pacman.PacManAction;
import game.core.Game;

/**
 * Envolve um controlador da Ms. Pac-Man e mede o tempo gasto em cada chamada de tick().
 * Não altera a decisão do controlador envolvido. Exceções do controlador são contadas
 * (e a primeira é impressa), porque o simulador as engoliria em silêncio.
 */
public final class Cronometrado implements IPacManController {

	private final IPacManController interno;
	private final long limiteNanos;

	private long ticks;
	private long somaNanos;
	private long maxNanos;
	private long estouros;        // ticks acima do limite de tempo
	private long erros;           // exceções lançadas pelo controlador

	public Cronometrado(IPacManController interno, int limiteMillis) {
		this.interno = interno;
		this.limiteNanos = limiteMillis * 1_000_000L;
	}

	@Override
	public void reset(Game game) {
		ticks = 0;
		somaNanos = 0;
		maxNanos = 0;
		estouros = 0;
		erros = 0;
		interno.reset(game);
	}

	@Override
	public void nextLevel(Game game) {
		interno.nextLevel(game);
	}

	@Override
	public void tick(Game game, long timeDue) {
		long t0 = System.nanoTime();
		try {
			interno.tick(game, timeDue);
		} catch (Throwable t) {
			if (erros == 0) {
				System.err.println("[ERRO] excecao no controlador (tick " + game.getTotalTime() + "):");
				t.printStackTrace();
			}
			erros++;
		}
		long dt = System.nanoTime() - t0;
		ticks++;
		somaNanos += dt;
		if (dt > maxNanos) maxNanos = dt;
		if (dt > limiteNanos) estouros++;
	}

	@Override
	public void killed() {
		interno.killed();
	}

	@Override
	public PacManAction getAction() {
		return interno.getAction();
	}

	public IPacManController interno() {
		return interno;
	}

	public long ticks() {
		return ticks;
	}

	public double msMedio() {
		return ticks == 0 ? 0.0 : (somaNanos / 1e6) / ticks;
	}

	public double msMax() {
		return maxNanos / 1e6;
	}

	public long estouros() {
		return estouros;
	}

	public long erros() {
		return erros;
	}
}
