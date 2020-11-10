import java.util.ArrayList;
import java.util.List;

// TODO: Auto-generated Javadoc
/**
 * Classe utilizzata per il calcolo della media di una serie di valori.
 *
 * @author Andrea Bombarda
 */
public class MeanValueCalculator {

	/** Lista dei valori utilizzati per il calcolo della media. */
	List<Integer> valueList;

	/**
	 * Instantiates a new mean value calculator.
	 */
	public MeanValueCalculator() {
		valueList = new ArrayList<Integer>();
	}

	/**
	 * Metodo utilizzato per l'aggiunta di un valore alla lista di valori da mediare.
	 *
	 * @param val il valore da aggiungere alla lista
	 */
	public void addValue(int val) {
		valueList.add(val);
	}

	/**
	 * Metodo che ritorna la <b>media</b> dei valori inseriti.
	 *
	 * @return il valore corrispondente alla media dei numeri
	 */
	public float getMean() {
		float v = 0;

		for (Integer val : valueList)
			v += val;

		return v / valueList.size();
	}
	
	
	public static void main(String[] args) {
		MeanValueCalculator mvc = new MeanValueCalculator();
		
		mvc.addValue(2);
		mvc.addValue(4);
		
		System.out.println(mvc.getMean());
	}

}
