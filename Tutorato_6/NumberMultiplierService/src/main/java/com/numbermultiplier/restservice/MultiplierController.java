package com.numbermultiplier.restservice;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MultiplierController {
	
	private final AtomicLong counter = new AtomicLong();

	@GetMapping("/multiply")
	public Multiplier multiply(@RequestParam(value = "n", defaultValue = "1") int n,
			@RequestParam(value = "m", defaultValue = "1") int m) {
		
		return new Multiplier(counter.incrementAndGet(), n, m);
		
	}
	
	@GetMapping("/sum")
	public Adder sum(@RequestParam(value = "n", defaultValue = "1") int n,
			@RequestParam(value = "m", defaultValue = "1") int m) {
		
		return new Adder(counter.incrementAndGet(), n, m);
		
	}
}