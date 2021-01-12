package com.number.multiplier;

public class Multiplier {
	
	private long id;
	private int n;
	private int m;
	private int result;
	
	public Multiplier() {};
	
	public Multiplier(int n, int m, long id) {
		this.m = m;
		this.n = m;
		this.id = id;
		this.result = n*m;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int getN() {
		return n;
	}

	public void setN(int n) {
		this.n = n;
	}

	public int getM() {
		return m;
	}

	public void setM(int m) {
		this.m = m;
	}

	public int getResult() {
		return result;
	}

	public void setResult(int result) {
		this.result = result;
	}
	
}
