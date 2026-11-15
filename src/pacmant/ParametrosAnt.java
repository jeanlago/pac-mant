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

	/** Troca os parâmetros de uma lista "nome=valor,nome=valor"; os que não aparecem ficam como estão. */
	public void aplicar(String lista) {
		if (lista == null || lista.trim().isEmpty()) return;
		for (String item : lista.split(",")) {
			String[] kv = item.trim().split("=");
			if (kv.length != 2) throw new IllegalArgumentException("parametro mal formado: " + item);
			String k = kv[0].trim();
			double v = Double.parseDouble(kv[1].trim());
			switch (k) {
				case "maxAnt": maxAnt = (int) Math.round(v); break;
				case "maxDist": maxDist = (int) Math.round(v); break;
				case "phi": phi = v; break;
				case "dMin": dMin = v; break;
				case "mu": mu = v; break;
				case "rhoC": rhoC = v; break;
				case "rhoE": rhoE = v; break;
				case "alphaC": alphaC = v; break;
				case "alphaE": alphaE = v; break;
				case "omega": omega = v; break;
				case "q0C": q0C = v; break;
				case "q0E": q0E = v; break;
				case "chi": chi = v; break;
				default: throw new IllegalArgumentException("parametro desconhecido: " + k);
			}
		}
	}

	@Override
	public String toString() {
		return String.format(java.util.Locale.ROOT,
			"maxAnt=%d,maxDist=%d,phi=%s,dMin=%s,mu=%s,rhoC=%s,rhoE=%s,alphaC=%s,alphaE=%s,omega=%s,q0C=%s,q0E=%s,chi=%s",
			maxAnt, maxDist, phi, dMin, mu, rhoC, rhoE, alphaC, alphaE, omega, q0C, q0E, chi);
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
