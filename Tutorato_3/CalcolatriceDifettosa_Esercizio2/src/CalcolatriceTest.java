import static org.junit.Assert.*;

import org.junit.Test;

public class CalcolatriceTest {

	@Test
	public void testPow() {
		Calcolatrice c = new Calcolatrice();
		assertEquals(8, c.pow(2, 3));
	}

}
