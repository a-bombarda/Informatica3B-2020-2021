package sax_libro;

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
public class SaxParserLibro extends DefaultHandler {

	List<Libro> myBooks;
	private String tempVal;
	private Libro tempBook;

	// Costruttore
	public SaxParserLibro() {
		myBooks = new ArrayList<Libro>();
	}

	private void parseDocument(String path) {

		// Si imposta il factory
		SAXParserFactory spf = SAXParserFactory.newInstance();
		try {

			// Si richiede un nuovo parser
			SAXParser sp = spf.newSAXParser();

			// Si imposta la caratteristica non validante (senza DTD):
			spf.setValidating(false);

			// Se volessi, invece utilizzare il DTD dovrei fare:
			// spf.setValidating(true);

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
	 * Itera nella lista myBooks e stampa il contenuto
	 * 
	 */
	private void printData() {
		System.out.println("Number of Books '" + myBooks.size() + "'.");
		for (Libro i : myBooks) {
			System.out.println(i.toString());
		}
	}

	// Gestione degli eventi:
	// Evento di apertura del tag nel file XML
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		// Reset delle variabili temporanee usate per la lettura
		tempVal = "";
		if (qName.equalsIgnoreCase("libro")) {
			// create a new instance of Libro
			tempBook = new Libro();
			tempBook.setTitolo(attributes.getValue("titolo"));
			tempBook.setAutore(attributes.getValue("autore"));
			tempBook.setEditore(attributes.getValue("editore"));
		} else if (qName.equalsIgnoreCase("prefazione")) {
			tempBook.setAutorePrefazione(attributes.getValue("autore"));
		}
	}

	// Evento PCDATA nel file XML
	public void characters(char[] ch, int start, int length) throws SAXException {
		tempVal = new String(ch, start, length);
	}

	// Evento chiusura tag nel file XML
	public void endElement(String uri, String localName, String qName) throws SAXException {

		if (qName.equalsIgnoreCase("libro")) {
			// Aggiungiamo l'oggetto impiegato appena incontrato alla lista myBooks
			myBooks.add(tempBook);
		} else if (qName.equalsIgnoreCase("prefazione")) {
			tempBook.setPrefazione(tempVal);
		} else if (qName.equalsIgnoreCase("titolo")) {
			tempBook.addIndice(tempVal);
		} else if (qName.equalsIgnoreCase("capitolo")) {
			tempBook.addCapitolo(tempVal);
		}
	}

	// Main
	public static void main(String[] args) {
		SaxParserLibro spe = new SaxParserLibro();
		spe.parseDocument("libro.xml");
		spe.printData();
	}

}
