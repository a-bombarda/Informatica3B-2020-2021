package sax_employees;
/*
 * Employee.java
 * è la classe che viene instanziata per l'incapsulamento delle 
 * informazioni (name, age, id e type) relative ad un impiegato. 
 * 
 * E' definita in modo tale da avere degli attributi che rispettino 
 * la costruzione del file XML. 
 */


public class Employee {

	private String name;
	private int age;
	private int id;
	private String type;

	public Employee() {	}

	public Employee(String name, int id, int age,String type) 
	{
		this.name = name;
		this.age = age;
		this.id  = id;
		this.type = type;

	}

	public int getAge() 
	{
		return age;
	}

	public void setAge(int age) 
	{
		this.age = age;
	}

	public int getId() 
	{
		return id;
	}

	public void setId(int id) 
	{
		this.id = id;
	}

	public String getName() 
	{
		return name;
	}

	public void setName(String name) 
	{
		this.name = name;
	}


	public String getType() 
	{
		return type;
	}

	public void setType(String type) 
	{
		this.type = type;
	}	


	public String toString() 
	{
		String s = "Employee Details - ";
		return (s + 
				"Name:" + getName() + ", " +
				"Type:" + getType() + ", " + 
				"Id:" + getId() + ", " +
				"Age:" + getAge() + ".");
	}
}
