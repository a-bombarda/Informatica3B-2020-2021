package test;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import triangolo.Triangolo;


public class TriangoloTest {

	static private Triangolo t1,t2,t3,t4;

	//Creo i triangoli prima del test
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		t1 = new Triangolo(2,4,3);   //regolare
		t2 = new Triangolo(2,4,8);   //non valido
		t3 = new Triangolo(2,4,6);   //degenere		
		t4 = new Triangolo(3,4,5);   //regolare
	}

	@Test
	public void testValido() {
		assertTrue(t1.valido());       //t1 è valido
		assertFalse(t2.valido());      //t2 è non valido
		assertEquals(3,t1.valido2());  //t1 è valido
		assertEquals(1,t2.valido2());  //t2 è non valido
		assertEquals(2,t3.valido2());  //t3 è degenere
		
		assertFalse("FALLISCE, t4 è valido", t4.valido());  //Fa fallire il test, t4.valido() == true!
	}

	@Test
	public void testPerimetro() {
		assertEquals(9,t1.perimetro());   //perimetro 
		assertEquals(0,t2.perimetro());   //perimetro di un triangolo non valido --> 0
		assertEquals(12,t3.perimetro());  //perimetro di un triangolo degenere
		
		assertEquals("FALLISCE t4.perimetro è 12",11, t4.perimetro());  //Fa fallire il test il perimetro è 12
	}
}
