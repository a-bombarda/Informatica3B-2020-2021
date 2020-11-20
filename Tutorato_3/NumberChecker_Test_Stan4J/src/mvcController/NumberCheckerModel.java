package mvcController;

import java.util.ArrayList;
import java.util.Observable;

/*
 *  Definiamo il modello come "Observable". Una classe model che estende
 *  "Observable" può essere gestita in modo molto più sempice, in quanto ogni
 *  modifica fatta al suo contenuto, genera in automatico una notifica
 *  per gli observer
 */
public class NumberCheckerModel extends Observable{
	// Campo privato, la "memoria" vera e propria del modello 
	private ArrayList<Integer> numbers; 
	private boolean outcome;

	// Costruttore: chiama il reset per azzerare la lista dei numeri
	NumberCheckerModel() {
		reset();
	}

	// Reset del valore iniziale
	public void reset() {
		System.out.println("[MODEL] reset ");
		numbers = new ArrayList<Integer>();
		outcome = false;
		// Comunica un cambio dello stato
		setChanged();
		// Notifica gli observer (la view)
		notifyObservers();
		System.out.println("[MOSEL] Observers notified (reset)");
	}


	// Controlla se il numero passato è o meno già visto
	// Attenzione: non dalla GUI ma dal controller
	public void checkNumber(String num) {
		outcome = false;
		for (Integer d : numbers)
			if (d.equals(new Integer(num)))
				outcome = true;
		if (!outcome)
			numbers.add(new Integer(num));
		System.out.println("[MODEL] Check numbers "+ num);
		// Comunica un cambio dello stato
		setChanged();
		// Notifica gli observer (la view)
		notifyObservers();
		System.out.println("[MDOEL] Observers notified (checkNumber)");
	}

	// Ritorna il valore dell'outcome
	public String getValue() {
		if (outcome)
			return "Già visto";
		return "Nuovo";
	}
}
