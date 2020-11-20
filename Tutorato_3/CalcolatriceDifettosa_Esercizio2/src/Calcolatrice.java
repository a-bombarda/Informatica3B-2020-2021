public class Calcolatrice {
	public static int somma(int a,int b) {
		return a+b;
	}

	public static int sottrai(int a,int b) {
		return a-b;
	}

	public static int moltiplica(int a,int b) {
		return a*b;
	}

	public static int dividi(int a,int b) {
		if (b==0){
			System.out.println("Error: division by 0.");
			return 0;
		}
		return a/b;
	}

	public static int abs(int a) {
		if (a >= 0) return a;
		return -a;
	}

	public static int pow(int base, int esponente) {
		int risultato = base;
		if (esponente < 0) {
			System.out.println("Error: esponente negativo.");
			return 0;
		}
		int appoggio = esponente; 	// Variabile di appoggio
		
		while (appoggio-- > 0){    	// Notare il POST-decremento
			risultato *= base;
		}
		
		return risultato;
	}
}