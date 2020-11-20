package main;

import triangolo.Triangolo;

public class Main {

	public static void main(String[] args) {
		int l1 = 3;
		int l2 = 4;
		int l3 = 5;

		Triangolo triangolo = new Triangolo(l1, l2, l3);

		System.out.println("Triangolo con lati: " + l1 + ", " + l2 + ", " + l3);

		switch(triangolo.valido2()){
		case 1:
			System.out.println("Triangolo non valido");
			break;

		case 2:
			System.out.println("Triangolo degenere");
			break;
			
		case 3:
			System.out.println("Triangolo valido");
			break;
		}
	}

}
