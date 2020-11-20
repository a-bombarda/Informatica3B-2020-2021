
public class Main {

	public static void main(String[] args) {
		CoinBox box = new CoinBox();

		box.addQtr();
//		box.addQtr();
//		box.addQtr();
		

		if(box.vend()){
			System.out.println("Ecco il caffè!, credito residuo: " + box.display());
		}else{
			System.out.println("Credito insufficiente: " + box.display());
		}

	}

}
