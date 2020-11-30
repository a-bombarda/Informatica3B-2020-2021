package sax_lookup_stampaDocenti;
/*
 * LookupHandler.java
 *
 * Questa classe mostra un esempio di handler di eventi SAX per 
 * effettuare una semplice ricerca (di elementi "docenti") su file 
 * di tipo corso.dtd.
 *
 */

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class LookupHandler extends org.xml.sax.helpers.DefaultHandler {
	private int nErrors, nWarnings, nFatals;
	private boolean inDocenti, printChars;

	public LookupHandler() {
		reset();
	}

	public void reset() {
		nErrors = 0;
		nWarnings = 0;
		nFatals = 0;
		inDocenti = false;
		printChars = false;

	}

	public void warning(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
		nWarnings++;
		System.out.println("WARNING (" + e.getSystemId() + ":" + e.getLineNumber() + "," + e.getColumnNumber() + ") "
				+ e.getMessage());
	}

	public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
		nFatals++;
		System.out.println("ERRORE FATALE (" + e.getSystemId() + ":" + e.getLineNumber() + "," + e.getColumnNumber()
				+ ") " + e.getMessage());
		throw e;
	}

	public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
		nErrors++;
		System.out.println("ERRORE (" + e.getSystemId() + ":" + e.getLineNumber() + "," + e.getColumnNumber() + ") "
				+ e.getMessage());

	}

	public int getNWarnings() {
		return nWarnings;
	}

	public int getNErrors() {
		return nErrors;
	}

	public int getNFatals() {
		return nFatals;
	}

	public boolean hadProblems() {
		return (nErrors + nFatals > 0);
	}

	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		// Nota l'uso del flag inDocenti per indicare se stiamo o meno
		// all'interno dell'elenco di docenti (una serie di tag <docente>).
		// Similmente per il flag printChars (per le sezioni PCDATA).
		if (inDocenti) {
			if (qName.equals("docente")) {
				System.out.println();
				System.out.print("Docente " + atts.getValue("rif") + ": ");
			} else if (qName.equals("nome")) {
				System.out.print(" Nome: ");
				printChars = true;
			} else if (qName.equals("cognome")) {
				System.out.print(" Cognome: ");
				printChars = true;
			}

		} else if (qName.equals("docenti"))
			inDocenti = true;
	}

	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (qName.equals("docenti"))
			inDocenti = false;
		else if (qName.equals("nome") || qName.equals("cognome"))
			printChars = false;
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		if (printChars)
			System.out.print(String.valueOf(ch, start, length));
	}

}
