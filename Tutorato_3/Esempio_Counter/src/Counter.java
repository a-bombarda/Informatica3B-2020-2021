
public class Counter {

	private int ctr; 

	public Counter(){
		this.ctr = 0;	
	}	

	public int inc(){
		this.ctr++;
		return this.ctr;
	}

	public int dec(){
		if(this.ctr > 0){
			this.ctr--;
		}else{
			this.ctr = 0;
		}
		return this.ctr;
	}


	public static void main(String[] args) {

		Counter c = new Counter();
		System.out.println("Inc: " + c.inc());
		System.out.println("Dec: " + c.dec());
		System.out.println("Dec: " + c.dec());

	}

}
