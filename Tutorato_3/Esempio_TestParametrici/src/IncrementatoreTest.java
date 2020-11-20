import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class) 
public class IncrementatoreTest {


	private int input; 
	private int inputIncrementato;

		public IncrementatoreTest(int p1, int p2){ 
			input = p1; 
			inputIncrementato = p2; 
		}

		@Parameters 
		public static Collection<Object[]> creaParametri(){ 
			return Arrays.asList(new Object[][] { {0, 1}, {2, 3}, {5, 6}});
		};  

		@Test 
		public void testParametrico(){ 
			Incrementatore i = new Incrementatore();
			int outputAttuale = i.incrementa(input);
			assertEquals(inputIncrementato,outputAttuale);} 
	}

