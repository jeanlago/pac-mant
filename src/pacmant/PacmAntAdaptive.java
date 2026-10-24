package pacmant;

import game.core.Game;

/**
 * pac-mAnt adaptativo (contribuição 1): parte do preset evoluído e ajusta parâmetros durante o jogo,
 * como sugerido na seção V do artigo ("um mecanismo para ajustar dinamicamente os parâmetros").
 *
 * A1: só a regra de fuga (Algoritmo 2).
 *     - última vida (getLivesRemaining() == 1): d_min x 1,5 e mu - 15;
 *     - fase alta (getCurLevel() >= 4): d_min x 1,2.
 *     As duas condições se acumulam (d_min x 1,8).
 * A2: A1 + chi adaptativo: chi(t) = CHI_MAX * perigo(t-1), em que perigo é a fração de
 *     exploradoras mortas no tick anterior.
 */
public class PacmAntAdaptive extends PacmAnt {

	public enum Variante { A1, A2 }

	public static final double FATOR_ULTIMA_VIDA = 1.5;
	public static final double DESCONTO_MU_ULTIMA_VIDA = 15.0;
	public static final int FASE_ALTA = 4;
	public static final double FATOR_FASE_ALTA = 1.2;
	public static final double CHI_MAX = 0.5;

	private final Variante variante;

	public PacmAntAdaptive(ParametrosAnt par, long semente, Variante variante) {
		super(par, semente);
		this.variante = variante;
	}

	@Override
	protected double dMinEfetivo(Game game) {
		double d = par.dMin;
		if (game.getLivesRemaining() == 1) d *= FATOR_ULTIMA_VIDA;
		if (game.getCurLevel() >= FASE_ALTA) d *= FATOR_FASE_ALTA;
		return d;
	}

	@Override
	protected double muEfetivo(Game game) {
		double mu = par.mu;
		if (game.getLivesRemaining() == 1) mu -= DESCONTO_MU_ULTIMA_VIDA;
		return mu;
	}

	@Override
	protected double chiEfetivo(Game game) {
		if (variante != Variante.A2) return par.chi;
		// chamado no início do tick: fracaoMortas ainda guarda o valor do tick anterior
		// (é zerada no início do jogo e a cada vida perdida)
		return CHI_MAX * fracaoMortas;
	}
}
