
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class NumberMultiplier {
	public static void main(String[] args) {
		try {
			// Definiamo due numeri
			int n = 5;
			int m = 4;

			// Chiamiamo il servizio REST per la moltiplicazione
			RestTemplate restTemplate = new RestTemplate();
			HttpHeaders headers = new HttpHeaders();
			headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
			UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("http://localhost:8080/multiply")
					.queryParam("n", n).queryParam("m", m);
			HttpEntity<?> entity = new HttpEntity<>(headers);
			JSONObject res = new JSONObject(restTemplate.exchange(
			           builder.build().encode().toUri(), 
			           HttpMethod.GET, 
			           entity, 
			           String.class).getBody());

			System.out.println("Risultato della moltiplicazione: " + res.getInt("result"));
			
			// Chiamiamo il servizio REST per la somma
			builder = UriComponentsBuilder.fromHttpUrl("http://localhost:8080/sum")
					.queryParam("n", n).queryParam("m", m);
			res = new JSONObject(restTemplate.exchange(
			           builder.build().encode().toUri(), 
			           HttpMethod.GET, 
			           entity, 
			           String.class).getBody());

			System.out.println("Risultato della somma: " + res.getInt("result"));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}