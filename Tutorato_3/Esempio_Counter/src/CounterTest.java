import static org.junit.Assert.*;

import org.junit.Test;


public class CounterTest {


	@Test
	public void testInc() {
		Counter c = new Counter();
//		assertEquals(1, c.inc()); //PASS
		assertEquals("FALLISCE", 2, c.inc()); //FAIL
	}

	@Test
	public void testDec() {
		Counter c = new Counter();
		assertEquals(0, c.dec());
	}

}
