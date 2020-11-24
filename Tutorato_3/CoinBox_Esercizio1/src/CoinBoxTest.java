import static org.junit.Assert.*;

import org.junit.Test;

public class CoinBoxTest {

	@Test
	public void testInit() {
		CoinBox c = new CoinBox();
		assertEquals(0,c.display());
	}
	
	@Test
	public void testSingleVend() {
		CoinBox c = new CoinBox();
		// Aggiungo due quarti di dollaro
		c.addQtr();
		c.addQtr();
		// Ottengo la bevanda
		assertTrue(c.vend());
	}
	
	@Test
	public void testNotEnough() {
		CoinBox c = new CoinBox();
		// Aggiungo un quarto di dollaro
		c.addQtr();
		// Non ottengo la bevanda
		assertFalse(c.vend());
	}

}
