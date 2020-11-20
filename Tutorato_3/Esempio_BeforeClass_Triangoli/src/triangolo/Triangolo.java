package triangolo;

public class Triangolo {
	private int latoA, latoB, latoC;

	public Triangolo(int a, int b, int c) {
		latoA = a;
		latoB = b;
		latoC = c; 
	}

	public boolean valido() {
		if (latoA == 0 || latoB == 0 || latoC == 0){
			return false;
		}
		
		if ((latoA+latoB < latoC) || (latoA+latoC < latoB) || (latoB+latoC < latoA)){
			return false;
		}
		return true;
	} 

	public int valido2() {
		if(latoA < 0 || latoB < 0 || latoC < 0 || latoA > latoB+latoC || latoB > latoA+latoC || latoC > latoA+latoB){
			return 1; 		//non valido
		}
			
		if(latoA == 0 || latoB == 0 || latoC == 0 || latoA == latoB + latoC || latoB == latoA + latoC || latoC == latoA + latoB){
			return 2; 		//degenere
		}
			
		return 3; 			//regolare
 
	}

	public int perimetro() {
		if (valido2()!= 1){ 				//se è un triangolo valido
			return latoA+latoB+latoC;
		}
		else return 0; 
	}

}

