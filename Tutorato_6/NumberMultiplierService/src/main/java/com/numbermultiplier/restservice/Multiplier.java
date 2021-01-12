package com.numbermultiplier.restservice;



public class Multiplier {
	
	private long id;
	private int n;
	private int m;
	private int result;
	
	public Multiplier() {}

	public Multiplier(long id, int n, int m) {
		this.id = id;
		this.n = n;
		this.m = m;
		this.result = n*m;
	}

	public long getId() {
		return id;
	}
	
	public int getN() {
		return n;
	}

	public int getM() {
		return m;
	}

	public int getResult() {
		return result;
	}
	
}