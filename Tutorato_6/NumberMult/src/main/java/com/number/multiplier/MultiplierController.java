package com.number.multiplier;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.*;

@RestController
public class MultiplierController {

	private final AtomicLong id = new AtomicLong();
	
	@GetMapping("/multiply")
	public Multiplier multiply(@RequestParam(value = "n", defaultValue = "1") int n, @RequestParam(value = "n", defaultValue = "1") int m) {
		return new Multiplier(n, m, id.incrementAndGet());
	}
	
}
