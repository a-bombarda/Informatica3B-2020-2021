// TODO: Auto-generated Javadoc
/**
 * The Class NumberSum
 * 
 * La classe effettua la somma di due numeri.
 *
 * @author Andrea Bombarda
 */
public class NumberSum {

	/** The a. */
	int a;
	
	/** The b. */
	int b;
	
	/**
	 * Instantiates a new number sum.
	 */
	public NumberSum() {
		a = b = 0;
	}
	
	public NumberSum(int a, int b) {
		super();
		this.a = a;
		this.b = b;
	}
	
	/**
	 * Sets the a.
	 *
	 * @param a the new a
	 */
	public void setA(int a) {
		this.a = a;
	}
	
	/**
	 * Sets the b.
	 *
	 * @param b the new b
	 */
	public void setB(int b) {
		this.b = b;
	}
	
	public int getA() {
		return a;
	}

	public int getB() {
		return b;
	}

	
	/**
	 * Gets the sum.
	 *
	 * @return the sum
	 */
	public int calcolaSomma() {
		return a + b;
	}
	
	public static void main(String[] args) {
		NumberSum ns = new NumberSum();
		
		ns.setA(2);
		ns.setB(4);
		
		System.out.println(ns.calcolaSomma());
	}
}
