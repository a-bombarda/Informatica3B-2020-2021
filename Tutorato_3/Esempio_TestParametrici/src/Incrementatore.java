
public class Incrementatore {

	int ctr;
	
	public Incrementatore(){
		this.ctr = 1;
	}
	
	public int incrementa(int p){
		this.ctr+=p;
		return this.ctr;
	}
	
	public static void main(String[] args) {
		Incrementatore i = new Incrementatore();
		
		System.out.println("valore: "+i.incrementa(1));

	}

}
