public class CoinBox {
	// Quarti di dollaro correnti
	int curQtrs;
	
	// Abilita la vendita
	boolean allowVend;
	
	// Costruisce un CoinBox inizialmente vuoto (contiene 0 monete)
	public CoinBox(){
		curQtrs = 0;
		allowVend = false;
	}
	
	// Restituisce il credito dell'utente corrente
	int display(){
		return curQtrs;
	}
	
	// Inserisce una moneta da un quarto di dollaro
	public void addQtr() {
		curQtrs++;
		if(curQtrs>1){
			allowVend = true;
		}
	}
	
	/* Richiede la vendita di un prodotto e restituisce un'indicazione 
		dell'effettiva erogazione o meno,
	 	decrementando il credito in caso di erogazione 
	 */
	boolean vend(){
		if(allowVend) {
			curQtrs -= 2;
			return true;
		}
		return false;
	}
}