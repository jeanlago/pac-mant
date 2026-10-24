package pacmant;

import game.controllers.ghosts.IGhostsController;
import game.controllers.ghosts.examples.AttractRepelGhosts;
import game.controllers.ghosts.examples.Legacy;
import game.controllers.ghosts.examples.Legacy2TheReckoning;
import game.controllers.ghosts.examples.RandomGhosts;
import game.controllers.pacman.IPacManController;
import game.controllers.pacman.examples.NearestPillPacMan;
import game.controllers.pacman.examples.RandomNonRevPacMan;
import game.controllers.pacman.examples.RandomPacMan;

/** Fábrica de controladores a partir do nome usado na linha de comando e nos CSVs. */
public final class Agentes {

	private Agentes() {
	}

	public static final String[] PACMAN = {
		"Random", "RandomNonRev", "NearestPill",
		"PacmAnt-evoluido", "PacmAnt-A1", "PacmAnt-A2",
	};

	public static final String[] FANTASMAS = {
		"RandomGhosts", "Legacy", "Legacy2", "AttractRepel",
	};

	/**
	 * @param nome     nome do agente
	 * @param semente  semente do gerador aleatório próprio do agente (derivada da semente do jogo)
	 */
	public static IPacManController pacman(String nome, long semente) {
		// Variantes de teste, por sufixo (ex.: PacmAnt-evoluido+acs+persist):
		//   +acs      regra de transição do ACS clássico (q <= q0 aproveita)   [seção 1.3]
		//   +persist  feromônio persistente dentro da vida/fase                 [DECISÃO 3]
		//   +minimo   distância da Ms. Pac-Man pelo caminho mínimo              [DECISÃO 12]
		//   +margemN  folga N no teste de morte da exploradora (0 = literal)    [DECISÃO 13]
		//   +chi05    chi fixo = 0,5 (teste post hoc; não vale para A2, que adapta chi)
		//   +alg1     Algoritmo 1 fiel: uma construção e um depósito por tick,
		//             com feromônio persistente                                [DECISÃO 15]
		String[] partes = nome.split("[+]");
		boolean acs = false, persist = false, trilha = true, alg1 = false, chi05 = false;
		int margem = game.core.Game.EAT_DISTANCE;
		for (int i = 1; i < partes.length; i++) {
			if (partes[i].equals("acs")) acs = true;
			else if (partes[i].equals("persist")) persist = true;
			else if (partes[i].equals("minimo")) trilha = false;
			else if (partes[i].equals("alg1")) alg1 = true;
			else if (partes[i].equals("chi05")) chi05 = true;
			else if (partes[i].startsWith("margem")) margem = Integer.parseInt(partes[i].substring(6));
			else throw new IllegalArgumentException("sufixo desconhecido: " + partes[i]);
		}
		PacmAnt ant = null;
		switch (partes[0]) {
			case "PacmAnt-evoluido":
				ant = new PacmAnt(ParametrosAnt.evoluido(), semente);
				break;
			case "PacmAnt-A1":
				ant = new PacmAntAdaptive(ParametrosAnt.evoluido(), semente, PacmAntAdaptive.Variante.A1);
				break;
			case "PacmAnt-A2":
				ant = new PacmAntAdaptive(ParametrosAnt.evoluido(), semente, PacmAntAdaptive.Variante.A2);
				break;
			default:
				break;
		}
		if (ant != null && chi05) ant.comChi(0.5);
		if (ant != null)
			return ant.comRegraAcsClassica(acs).comFeromonioPersistente(persist)
				.comDistanciaPelaTrilha(trilha).comMargemCaptura(margem).comAlgoritmo1(alg1);
		switch (nome) {
			case "Random":
				return new RandomPacMan();
			case "RandomNonRev":
				return new RandomNonRevPacMan();
			case "NearestPill":
				return new NearestPillPacMan();
			default:
				throw new IllegalArgumentException("agente desconhecido: " + nome);
		}
	}

	public static IGhostsController fantasmas(String nome) {
		switch (nome) {
			case "RandomGhosts":
				return new RandomGhosts();
			case "Legacy":
				return new Legacy();
			case "Legacy2":
				return new Legacy2TheReckoning();
			case "AttractRepel":
				return new AttractRepelGhosts(true);
			default:
				throw new IllegalArgumentException("fantasma desconhecido: " + nome);
		}
	}
}
