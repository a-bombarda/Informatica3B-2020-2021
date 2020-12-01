package sax_employees;
/* @author Patrizia Scandurra
 * Esempio di lettura di un documento XML con JAXP SAX. 
 *  
 * Dopo l'allocazione del parser e del file xml di lettura (employees.xml) 
 * determinati eventi vengono scatenati, in funzione dell'elemento che viene
 * incontrato. I tre medoti principali: startElement, characters, e endElement 
 * gestiscono il tutto.
 * 
 * Con lo startElement un oggetto Employee viene allocato e con esso il suo 
 * attributo type. Con characters i PCDATA vengono memorizzati temporaneamente, 
 * e con endElement si completa l'oggetto Employee nel caso di chiusura 
 * dell'oggetto stesso. 
 * 
 * Se l'oggetto che viene chiuso non è Employee, vengono immagazzinati i valori 
 * PCDATA degli altri oggetti. Alla fine del parsing, vengono stampati tutti 
 * gli oggetti Employee che sono stati individuati nel file xml.
 */

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

// La classe che fa il parsing estende anche il DefaultHandler
public class SaxParserEmployee extends DefaultHandler {

	List<Employee> myEmpls;
	private String tempVal;
	private Employee tempEmp;

	// Costruttore
	public SaxParserEmployee() {
		myEmpls = new ArrayList<Employee>();
	}

	private void parseDocument(String path) {

		// Si imposta il factory
		SAXParserFactory spf = SAXParserFactory.newInstance();
		try {

			// Si richiede un nuovo parser
			SAXParser sp = spf.newSAXParser();

			// Si imposta la caratteristica non validante (non c'è DTD):
			// Se ci fosse il DTD, la struttura
			// del file XML bverrebbe verificata rispetto al DTD
			spf.setValidating(false);

			// Parsing del file e registrazione della classe per le "call backs"
			// Nota: il metodo parse usato è:
			// public void parse(String uri, DefaultHandler dh)
			// dove uri è la locazione del contenuto da parsare!
			sp.parse(path, this);

		} catch (SAXException se) {
			se.printStackTrace();
		} catch (ParserConfigurationException pce) {
			pce.printStackTrace();
		} catch (IOException ie) {
			ie.printStackTrace();
		}
	}

	/**
	 * Itera nella lista myEmpls e stampa il contenuto
	 * 
	 */
	private void printData() {
		System.out.println("Number of Employees '" + myEmpls.size() + "'.");
		for (Employee i : myEmpls) {
			System.out.println(i.toString());
		}
	}

	// Gestione degli eventi:
	// Evento di apertura del tag nel file XML
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		// Reset delle variabili temporanee usate per la lettura
		tempVal = "";
		if (qName.equalsIgnoreCase("Employee")) {
			// create a new instance of employee
			tempEmp = new Employee();
			tempEmp.setType(attributes.getValue("type"));
		}
	}

	// Evento PCDATA nel file XML
	public void characters(char[] ch, int start, int length) throws SAXException {
		tempVal = new String(ch, start, length);
	}

	// Evento chiusura tag nel file XML
	public void endElement(String uri, String localName, String qName) throws SAXException {

		if (qName.equalsIgnoreCase("Employee")) {
			// Aggiungiamo l'oggetto impiegato appena incontrato alla lista myEmpls
			myEmpls.add(tempEmp);
		} else if (qName.equalsIgnoreCase("Name")) {
			tempEmp.setName(tempVal);
		} else if (qName.equalsIgnoreCase("Id")) {
			tempEmp.setId(Integer.parseInt(tempVal));
		} else if (qName.equalsIgnoreCase("Age")) {
			tempEmp.setAge(Integer.parseInt(tempVal));
		}
	}

	// Main
	public static void main(String[] args) {
		SaxParserEmployee spe = new SaxParserEmployee();
		spe.parseDocument("employees.xml");
		spe.printData();
	}

}
