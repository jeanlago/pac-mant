package pacmant;

/** Parâmetros do pac-mAnt (Tabela I do artigo) e os dois conjuntos usados (seção 1.10 da especificação). */
public final class ParametrosAnt implements Cloneable {

	public int maxAnt;       // formigas de cada tipo por tick
	public int maxDist;      // nós visitados por formiga
	public double phi;       // Φ: peso do fantasma comestível na heurística da coletora (peso direto)
	public double dMin;      // d_min: distância ao fantasma mais próximo que dispara a fuga
	public double rhoC, rhoE;     // ρ: taxa da atualização local
	public double alphaC, alphaE; // α: taxa da atualização global
	public double chi;       // χ: peso do feromônio das exploradoras na qualidade das coletoras (eq. 16)
	public double omega;     // ω: peso do feromônio das coletoras na qualidade das exploradoras (eq. 19)
	public double q0C, q0E;  // q0 da regra de transição
	public double mu;        // μ: % de exploradoras mortas que dispara a fuga (0 a 100)

	/** Parâmetros ajustados à mão (usados na Tabela III do artigo). */
	public static ParametrosAnt manual() {
		ParametrosAnt p = new ParametrosAnt();
		p.maxAnt = 15;
		p.maxDist = 100;
		p.phi = 70;
		p.dMin = 35;
		p.rhoC = 0.5;
		p.rhoE = 0.5;
		p.alphaC = 0.5;
		p.alphaE = 0.5;
		p.chi = 0.0;
		p.omega = 0.0;
		p.q0C = 0.7;
		p.q0E = 0.95;
		p.mu = 50;
		return p;
	}

	/** Parâmetros evoluídos pelo algoritmo genético (versão da competição). */
	public static ParametrosAnt evoluido() {
		ParametrosAnt p = new ParametrosAnt();
		p.maxAnt = 19;
		p.maxDist = 100;
		p.phi = 79;
		p.dMin = 13;
		p.rhoC = 0.22;
		p.rhoE = 0.70;
		p.alphaC = 0.23;
		p.alphaE = 0.11;
		p.chi = 0.0;
		p.omega = 0.72;
		p.q0C = 0.37;
		p.q0E = 0.69;
		p.mu = 60;
		return p;
	}

	@Override
	public ParametrosAnt clone() {
		try {
			return (ParametrosAnt) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}
}
