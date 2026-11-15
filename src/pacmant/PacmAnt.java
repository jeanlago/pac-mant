package pacmant;

import game.controllers.pacman.PacManControllerBase;
import game.core.Game;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * pac-mAnt: colônia de formigas para a Ms. Pac-Man, conforme
 * Recio, Martín, Estébanez e Sáez, "AntBot: Ant Colonies for Video Games", IEEE TCIAIG 4(4), 2012.
 *
 * Dois tipos de formiga partem dos nós vizinhos da Ms. Pac-Man (ninhos):
 * coletoras (buscam pontos) e exploradoras (buscam o caminho mais seguro).
 * As escolhas que o artigo deixa em aberto estão numeradas em DECISOES.md.
 */
public class PacmAnt extends PacManControllerBase {

	public static final double EPS = 0.01;        // DECISÃO 1: heurística da coletora em nó vazio
	public static final double TAU0 = 0.1;        // DECISÃO 2: feromônio inicial
	public static final int SEM_FANTASMA = 200;   // DECISÃO 10: distância usada quando não há fantasma perigoso

	protected final ParametrosAnt par;
	protected final Random rnd;

	/** Seção 1.3: false = regra como está no artigo (q > q0 aproveita); true = ACS clássico (q <= q0 aproveita). */
	protected boolean regraAcsClassica = false;
	/** DECISÃO 3: false = feromônio reiniciado a cada tick; true = persiste dentro da vida/fase. */
	protected boolean persistirFeromonio = false;

	/**
	 * DECISÃO 12: como medir a "distância da Ms. Pac-Man até o nó" da formiga.
	 * true (padrão) = passos ao longo do caminho da formiga; false = caminho mínimo no labirinto (getPathDistance).
	 */
	protected boolean distanciaPelaTrilha = true;
	/**
	 * DECISÃO 13: folga somada ao tempo da Ms. Pac-Man no teste de morte da exploradora.
	 * Padrão = EAT_DISTANCE (2): no simulador o fantasma captura a até 2 nós de distância. 0 = leitura literal.
	 */
	protected int margemCaptura = Game.EAT_DISTANCE;

	/**
	 * DECISÃO 15: false (padrão) = várias iterações com depósito dentro do tick (Decisão 9);
	 * true = Algoritmo 1 do artigo: uma única construção por tick e um único depósito da melhor
	 * formiga de cada tipo ao fim, com feromônio persistente entre ticks.
	 */
	protected boolean algoritmo1 = false;

	/**
	 * DECISÃO 16: false (padrão) = termo colaborativo da eq. 16 como soma, chi * Σ tauE;
	 * true = normalizado pelo tamanho do caminho, chi * (Σ tauE / número de arcos).
	 */
	protected boolean chiNormalizado = false;

	// --- grafo e feromônio (um valor por arco dirigido: nó*4 + direção) ---
	private int numNos = -1;
	private int nivelAtual = -1;
	private double[] tauC, tauE;
	private boolean reiniciarPendente = true;

	// --- caches válidos por um tick ---
	private int carimbo = 0;
	private int[] carimboDist, distFantasma;    // δ(nó, fantasma perigoso mais próximo)
	private int[] carimboCheg, chegadaFantasma; // menor tempo de chegada de um fantasma perigoso (sem reversão)
	private int[] carimboComestivel;            // nó contém fantasma comestível neste tick
	private final int[] perigosos = new int[Game.NUM_GHOSTS];
	private int numPerigosos;
	private double razaoPilulas;
	private int posPac;

	// --- formigas ---
	private int[] visitado;
	private int carimboFormiga = 0;
	private int[] caminhoArcos;       // arcos da formiga em construção
	private int tamCaminho;
	private int[] melhorArcosC, melhorArcosE;
	private int tamMelhorC, tamMelhorE;
	private final int[] cand = new int[4];
	private final double[] peso = new double[4];

	// --- traço por jogada (teste post hoc da eq. 16); não altera nenhuma decisão ---
	public static final String CABECALHO_TRACO =
		"tick,fase,modo,chi,termo_pilulas,soma_tau_e,termo_chi,q_melhor_coletora,nos_melhor_coletora,tau_e_max,tau_c_max";
	private PrintWriter traco;
	private double termoPilulas, somaTauE;                // da coletora em construção
	private double melhorTermoPilulas, melhorSomaTauE;    // da melhor coletora do tick

	// --- diagnóstico (lido pelas variantes adaptativas e pelos testes) ---
	protected double fracaoMortas;     // fração de exploradoras mortas no último tick
	protected boolean ultimoFoiFuga;
	public long ticksTotal, ticksFuga;

	public PacmAnt(ParametrosAnt par, long semente) {
		this.par = par.clone();
		this.rnd = new Random(semente);
	}

	public PacmAnt comRegraAcsClassica(boolean v) {
		this.regraAcsClassica = v;
		return this;
	}

	public PacmAnt comFeromonioPersistente(boolean v) {
		this.persistirFeromonio = v;
		return this;
	}

	public PacmAnt comDistanciaPelaTrilha(boolean v) {
		this.distanciaPelaTrilha = v;
		return this;
	}

	/** Algoritmo 1 fiel ao artigo; liga também o feromônio persistente, sem o qual o depósito seria descartado. */
	public PacmAnt comAlgoritmo1(boolean v) {
		this.algoritmo1 = v;
		if (v) this.persistirFeromonio = true;
		return this;
	}

	/** Termo colaborativo da eq. 16 como média de tauE no caminho da coletora, em vez da soma. */
	public PacmAnt comChiNormalizado(boolean v) {
		this.chiNormalizado = v;
		return this;
	}

	/** Troca os parâmetros indicados em uma lista "nome=valor,nome=valor" (usado na calibração). */
	public PacmAnt comParametros(String lista) {
		this.par.aplicar(lista);
		return this;
	}

	/** Fixa o valor de chi (peso do feromônio das exploradoras na qualidade das coletoras). */
	public PacmAnt comChi(double v) {
		this.par.chi = v;
		return this;
	}

	/** Liga o traço por jogada: uma linha por tick com os dois termos da eq. 16 na melhor coletora. */
	public PacmAnt comTraco(PrintWriter w) {
		this.traco = w;
		return this;
	}

	public PacmAnt comMargemCaptura(int v) {
		this.margemCaptura = v;
		return this;
	}

	// ------------------------------------------------------------------
	// Ganchos sobrescritos pela versão adaptativa
	// ------------------------------------------------------------------

	protected double dMinEfetivo(Game game) {
		return par.dMin;
	}

	protected double muEfetivo(Game game) {
		return par.mu;
	}

	protected double chiEfetivo(Game game) {
		return par.chi;
	}

	// ------------------------------------------------------------------
	// Ciclo de vida do controlador
	// ------------------------------------------------------------------

	@Override
	public void reset(Game game) {
		super.reset(game);
		numNos = -1;
		nivelAtual = -1;
		reiniciarPendente = true;
		fracaoMortas = 0;
		ultimoFoiFuga = false;
		ticksTotal = 0;
		ticksFuga = 0;
	}

	@Override
	public void nextLevel(Game game) {
		// a base reconstrói um módulo de labirinto que não usamos; basta reiniciar o feromônio
		reiniciarPendente = true;
	}

	@Override
	public void killed() {
		// vida perdida: o feromônio persistente deixa de valer (o simulador também chama isto ao ganhar a vida extra)
		reiniciarPendente = true;
		fracaoMortas = 0;
	}

	@Override
	public void tick(Game game, long timeDue) {
		int dir = decidir(game);
		if (dir >= 0) pacman.set(dir);
	}

	// ------------------------------------------------------------------
	// Algoritmo 1
	// ------------------------------------------------------------------

	protected int decidir(Game game) {
		preparar(game);

		int[] viz = game.getPacManNeighbours();
		int[] ninhoDir = new int[4];
		int numNinhos = 0;
		for (int d = 0; d < 4; d++)
			if (viz[d] != -1) ninhoDir[numNinhos++] = d;
		if (numNinhos == 0) return -1;

		final double chi = chiEfetivo(game);
		final double omega = par.omega;

		// melhor coletora até agora
		double melhorQC = Double.NEGATIVE_INFINITY;
		int melhorDirC = ninhoDir[0];
		tamMelhorC = 0;
		// melhor exploradora até agora: viva vence morta; entre vivas, maior Q; entre mortas, mais nós visitados
		boolean melhorEViva = false;
		double melhorQE = Double.NEGATIVE_INFINITY;
		int melhorNosE = -1;
		int melhorDirE = ninhoDir[0];
		tamMelhorE = 0;

		int mortas = 0;

		for (int k = 0; k < par.maxAnt; k++) {
			int d0 = ninhoDir[k % numNinhos];   // DECISÃO 7: rodízio entre os ninhos

			// ---------------- exploradora ----------------
			boolean viva = construirExploradora(game, d0, omega);
			double qe = qualidade;
			if (!viva) mortas++;
			boolean troca;
			if (viva != melhorEViva) troca = viva;
			else if (viva) troca = qe > melhorQE;
			else troca = tamCaminho > melhorNosE || (tamCaminho == melhorNosE && qe > melhorQE);
			if (troca) {
				melhorEViva = viva;
				melhorQE = qe;
				melhorNosE = tamCaminho;
				melhorDirE = d0;
				System.arraycopy(caminhoArcos, 0, melhorArcosE, 0, tamCaminho);
				tamMelhorE = tamCaminho;
			}

			// ---------------- coletora ----------------
			construirColetora(game, d0, chi);
			double qc = qualidade;
			if (qc > melhorQC) {
				melhorTermoPilulas = termoPilulas;
				melhorSomaTauE = somaTauE;
				melhorQC = qc;
				melhorDirC = d0;
				System.arraycopy(caminhoArcos, 0, melhorArcosC, 0, tamCaminho);
				tamMelhorC = tamCaminho;
			}

			// ---------------- atualização global (eq. 14-15 e 17-18) ----------------
			// DECISÃO 9: uma iteração = uma passada pelos ninhos; ao fim dela a melhor formiga
			// de cada tipo (até agora) deposita nos arcos que percorreu.
			// DECISÃO 15 (+alg1): um único depósito, depois que todos os ninhos receberam suas formigas.
			boolean ultima = k == par.maxAnt - 1;
			if (algoritmo1 ? ultima : ((k + 1) % numNinhos == 0 || ultima)) {
				depositar(tauC, melhorArcosC, tamMelhorC, par.alphaC, melhorQC);
				depositar(tauE, melhorArcosE, tamMelhorE, par.alphaE, melhorQE);
			}
		}

		fracaoMortas = par.maxAnt > 0 ? (double) mortas / par.maxAnt : 0.0;

		// ---------------- Algoritmo 2: decisão ----------------
		int distMaisProximo = distFantasma(game, posPac);
		boolean fuga = distMaisProximo <= dMinEfetivo(game) || 100.0 * fracaoMortas >= muEfetivo(game);
		ultimoFoiFuga = fuga;
		if (traco != null) {
			double maxE = 0, maxC = 0;
			maxE = maximo(tauE);
			maxC = maximo(tauC);
			traco.println(String.format(Locale.ROOT, "%d,%d,%s,%.4f,%.6g,%.6g,%.6g,%.6g,%d,%.6g,%.6g",
				game.getTotalTime(), game.getCurLevel(), fuga ? "fuga" : "coleta", chi,
				melhorTermoPilulas, melhorSomaTauE,
				chi * (chiNormalizado ? melhorSomaTauE / Math.max(1, tamMelhorC) : melhorSomaTauE),
				melhorQC, tamMelhorC, maxE, maxC));
		}
		ticksTotal++;
		if (fuga) ticksFuga++;
		return fuga ? melhorDirE : melhorDirC;
	}

	/** Maior valor do vetor; devolve NaN se houver algum NaN (para o traço denunciar instabilidade numérica). */
	private static double maximo(double[] v) {
		double m = 0;
		for (double x : v) {
			if (Double.isNaN(x)) return Double.NaN;
			if (x > m) m = x;
		}
		return m;
	}

	private void depositar(double[] tau, int[] arcos, int n, double alpha, double q) {
		if (n == 0 || Double.isInfinite(q)) return;
		for (int i = 0; i < n; i++) {
			int a = arcos[i];
			tau[a] = (1.0 - alpha) * tau[a] + alpha * q;
		}
	}

	// ------------------------------------------------------------------
	// Preparação do tick
	// ------------------------------------------------------------------

	private void preparar(Game game) {
		int n = game.getNumberOfNodes();
		if (n != numNos) {
			numNos = n;
			tauC = new double[n * 4];
			tauE = new double[n * 4];
			carimboDist = new int[n];
			distFantasma = new int[n];
			carimboCheg = new int[n];
			chegadaFantasma = new int[n];
			carimboComestivel = new int[n];
			visitado = new int[n];
			caminhoArcos = new int[n + 8];
			melhorArcosC = new int[n + 8];
			melhorArcosE = new int[n + 8];
			carimbo = 0;
			carimboFormiga = 0;
			reiniciarPendente = true;
		}
		if (game.getCurLevel() != nivelAtual) {
			nivelAtual = game.getCurLevel();
			reiniciarPendente = true;
		}
		if (!persistirFeromonio || reiniciarPendente) {   // DECISÃO 3
			Arrays.fill(tauC, TAU0);
			Arrays.fill(tauE, TAU0);
			reiniciarPendente = false;
		}

		carimbo++;
		posPac = game.getCurPacManLoc();
		razaoPilulas = (double) game.getNumberPills() / Math.max(1, game.getNumActivePills());

		numPerigosos = 0;
		for (int g = 0; g < Game.NUM_GHOSTS; g++) {
			if (game.getLairTime(g) > 0) continue;            // na toca: não conta
			if (game.isEdible(g)) carimboComestivel[game.getCurGhostLoc(g)] = carimbo;
			else perigosos[numPerigosos++] = g;
		}
	}

	/** δ(nó, fantasma não comestível mais próximo), em distância de caminho. */
	protected final int distFantasma(Game game, int no) {
		if (carimboDist[no] == carimbo) return distFantasma[no];
		int m = SEM_FANTASMA;
		for (int i = 0; i < numPerigosos; i++) {
			int d = game.getPathDistance(no, game.getCurGhostLoc(perigosos[i]));
			if (d >= 0 && d < m) m = d;
		}
		carimboDist[no] = carimbo;
		distFantasma[no] = m;
		return m;
	}

	/** Menor tempo de chegada de um fantasma perigoso ao nó, sem reverter (equivale a getGhostPathDistance). */
	private int chegadaFantasma(Game game, int no) {
		if (carimboCheg[no] == carimbo) return chegadaFantasma[no];
		int m = Integer.MAX_VALUE;
		for (int i = 0; i < numPerigosos; i++) {
			int d = distSemReverter(game, perigosos[i], no, numNos);
			if (d < m) m = d;
		}
		carimboCheg[no] = carimbo;
		chegadaFantasma[no] = m;
		return m;
	}

	/**
	 * Mesma conta de Game.getGhostPathDistance (caminho guloso sem reversão), sem alocar memória e
	 * com um limite de passos por segurança.
	 */
	public static int distSemReverter(Game game, int fantasma, int destino, int limite) {
		int atual = game.getCurGhostLoc(fantasma);
		int ultimaDir = game.getCurGhostDir(fantasma);
		int passos = 0;
		while (atual != destino && passos < limite) {
			int proibida = game.getReverse(ultimaDir);
			int melhor = -1;
			int min = Integer.MAX_VALUE;
			for (int d = 0; d < 4; d++) {
				if (d == proibida) continue;
				int v = game.getNeighbour(atual, d);
				if (v == -1) continue;
				int dist = game.getPathDistance(v, destino);
				if (dist < min) {
					min = dist;
					melhor = d;
				}
			}
			if (melhor == -1) return limite;
			ultimaDir = melhor;
			atual = game.getNeighbour(atual, melhor);
			passos++;
		}
		return passos;
	}

	private boolean temPilula(Game game, int no) {
		int pi = game.getPillIndex(no);
		if (pi >= 0 && game.checkPill(pi)) return true;
		return temPilulaDePoder(game, no);
	}

	private boolean temPilulaDePoder(Game game, int no) {
		int pp = game.getPowerPillIndex(no);
		return pp >= 0 && game.checkPowerPill(pp);
	}

	/** Heurística da coletora (eq. 10). */
	private double etaC(Game game, int no) {
		if (carimboComestivel[no] == carimbo) return par.phi * razaoPilulas;
		if (temPilula(game, no)) return razaoPilulas;
		return EPS;
	}

	/** Heurística da exploradora (eq. 11). */
	private double etaE(Game game, int no) {
		return distFantasma(game, no) + game.getNumNeighbours(no);
	}

	// ------------------------------------------------------------------
	// Construção das soluções
	// ------------------------------------------------------------------

	private double qualidade;   // qualidade da última formiga construída

	/**
	 * Regra de transição (eq. 4-5) com alfa = beta = 1. Devolve a direção escolhida a partir de 'no',
	 * ou -1 se todos os vizinhos já foram visitados.
	 */
	private int escolher(Game game, int no, double[] tau, boolean coletora, double q0) {
		int n = 0;
		double soma = 0;
		int melhor = -1;
		double max = -1;
		for (int d = 0; d < 4; d++) {
			int v = game.getNeighbour(no, d);
			if (v == -1 || visitado[v] == carimboFormiga) continue;
			double w = tau[no * 4 + d] * (coletora ? etaC(game, v) : etaE(game, v));
			cand[n] = d;
			peso[n] = w;
			n++;
			soma += w;
			if (w > max) {
				max = w;
				melhor = d;
			}
		}
		if (n == 0) return -1;
		if (n == 1) return cand[0];

		double q = rnd.nextDouble();
		// artigo: q > q0 escolhe o melhor (eq. 4); ACS clássico: q <= q0 escolhe o melhor
		boolean aproveita = regraAcsClassica ? (q <= q0) : (q > q0);
		if (aproveita || soma <= 0) return melhor;

		double r = rnd.nextDouble() * soma;
		for (int i = 0; i < n; i++) {
			r -= peso[i];
			if (r < 0) return cand[i];
		}
		return cand[n - 1];
	}

	private void novaFormiga() {
		carimboFormiga++;
		visitado[posPac] = carimboFormiga;   // DECISÃO 11: a formiga não volta pelo nó da Ms. Pac-Man
		tamCaminho = 0;
		qualidade = 0;
		termoPilulas = 0;
		somaTauE = 0;
	}

	private void construirColetora(Game game, int d0, double chi) {
		novaFormiga();
		int atual = posPac;
		int dir = d0;
		while (true) {
			int arco = atual * 4 + dir;
			int prox = game.getNeighbour(atual, dir);
			tauC[arco] = (1.0 - par.rhoC) * tauC[arco] + par.rhoC * TAU0;   // atualização local (eq. 12)
			visitado[prox] = carimboFormiga;
			caminhoArcos[tamCaminho++] = arco;
			// eq. 16, com +1 no denominador (DECISÃO 4)
			int dPac = distanciaPelaTrilha ? tamCaminho : game.getPathDistance(posPac, prox);
			// DECISÃO 16 (+norm): o termo colaborativo entra ao fim da construção, como média
			if (chiNormalizado) qualidade += etaC(game, prox) / (dPac + 1.0);
			else qualidade += etaC(game, prox) / (dPac + 1.0) + chi * tauE[arco];
			// os dois termos em separado, só para o traço (a soma acima fica como estava)
			termoPilulas += etaC(game, prox) / (dPac + 1.0);
			somaTauE += tauE[arco];
			atual = prox;
			if (tamCaminho >= par.maxDist) break;
			dir = escolher(game, atual, tauC, true, par.q0C);
			if (dir == -1) break;   // laço: todos os vizinhos já visitados
		}
		if (chiNormalizado) qualidade += chi * (somaTauE / tamCaminho);
	}

	/** @return true se a exploradora terminou viva. */
	private boolean construirExploradora(Game game, int d0, double omega) {
		novaFormiga();
		int atual = posPac;
		int dir = d0;
		boolean protegida = false;   // já passou por uma pílula de poder
		while (true) {
			int arco = atual * 4 + dir;
			int prox = game.getNeighbour(atual, dir);
			tauE[arco] = (1.0 - par.rhoE) * tauE[arco] + par.rhoE * TAU0;   // atualização local (eq. 13)
			int dPac = distanciaPelaTrilha ? tamCaminho + 1 : game.getPathDistance(posPac, prox);
			// morte: um fantasma perigoso chega ao nó antes ou junto com a Ms. Pac-Man (seção 1.6)
			if (!protegida && numPerigosos > 0 && chegadaFantasma(game, prox) <= dPac + margemCaptura) return false;
			if (temPilulaDePoder(game, prox)) protegida = true;
			visitado[prox] = carimboFormiga;
			caminhoArcos[tamCaminho++] = arco;
			// eq. 19, com (delta + 1)^2 no denominador (DECISÃO 5)
			double den = dPac + 1.0;
			qualidade += distFantasma(game, prox) / (den * den) + omega * tauC[arco];
			atual = prox;
			// só termina em cruzamento (3 ou mais vizinhos)
			if (tamCaminho >= par.maxDist && game.getNumNeighbours(atual) >= 3) break;
			dir = escolher(game, atual, tauE, false, par.q0E);
			if (dir == -1) break;
		}
		return true;
	}

	// ------------------------------------------------------------------

	public double fracaoMortas() {
		return fracaoMortas;
	}

	public double fracaoTicksEmFuga() {
		return ticksTotal == 0 ? 0 : (double) ticksFuga / ticksTotal;
	}
}
