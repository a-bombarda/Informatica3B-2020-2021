package dom_create;
/*
 * XMLCreate.java
 *
 * Questa classe mostra come è posibile
 * creare in memoria un documento XML e
 * successivamente serializzarlo in un file.
 *
 * Per eseguirlo è necessario che i jar di
 * Xerces siano nel classpath.
 */

import java.io.*; //Java I/O Standard
import javax.xml.parsers.*; //XML parsers
import org.w3c.dom.*; //Interfacce di DOM 
import org.w3c.dom.ls.*; //Interfacce Load&Save di DOM

public class XMLCreate {

	/*
	 * Crea un elemento "corso" con le caratteristiche date come parametri
	 * L'implementazione di questo metodo si basa sulle implementazioni di una serie
	 * di altri metodi a cui viene delegata la costruzione dei vari elementi del
	 * documento (descrizione, sillabo, docenti, testi...)
	 */
	public Document createCorso(Document d, String nome, String codice, int[] crediti) {

		// Creiamo l'elemento radice "corso"
		Element radice = d.createElement("corso"); // versione senza DTD
		// Element radice = d.getDocumentElement(); //versione con DTD

		// Impostiamo l'attributo "codice"
		radice.setAttribute("codice", codice);

		// Creiamo l'elemento "nome"
		Element _nome = d.createElement("nome");
		_nome.setTextContent(nome);
		// Lo appendiamo alla radice come nodo figlio
		radice.appendChild(_nome);

		// Creiamo altri elementi con dei metodi ausiliari opportuni
		// e li appendiamo alla radice come figli
		// I metodi ausiliari restituiscono tutti degli element,
		// eventualmente contenenti sotto-alberi con sotto-elementi
		radice.appendChild(createDescrizione(d));
		radice.appendChild(createSillabo(d, crediti));
		radice.appendChild(createDocenti(d));
		radice.appendChild(createTesti(d));
		radice.appendChild(createErogazione(d, crediti));

		// Infine, attacchiamo al nodo documento la radice
		d.appendChild(radice); // versione senza DTD

		return d;
	}

	/* Crea un template (senza elementi concreti) per l'elemento descrizione */
	public Element createDescrizione(Document d) {
		// Creo l'elemento descrizione
		Element descrizione = d.createElement("descrizione");
		// Creo i sotto-elementi
		Element obiettivi = d.createElement("obiettivi");
		Element esame = d.createElement("esame");
		// Creo i sotto-sotto-elementi e li appendo
		obiettivi.appendChild(d.createComment("Obiettivi formativi del corso"));
		esame.appendChild(d.createComment("Modalità di esame"));
		// Appendo i sotto-elementi e li appendo a descrizione
		descrizione.appendChild(obiettivi);
		descrizione.appendChild(esame);
		// Restituisco descrizione (verrà appesa a radice)
		return descrizione;
	}

	/* Crea un template per l'elemento sillabo con le caratteristiche date */
	public Element createSillabo(Document d, int[] crediti) {
		// Creo l'element
		Element sillabo = d.createElement("sillabo");
		// Per ogni elemento della lista crediti creo un sotto elemento argomento e un
		// commento
		for (int i = 0; i < crediti.length; ++i) {
			for (int j = 0; j < crediti[i]; ++j) {
				Element argomento = d.createElement("argomento");
				argomento.appendChild(d.createComment("Descrizione del credito"));
				sillabo.appendChild(argomento);
			}
		}

		return sillabo;

	}

	/* Crea un template per l'elemento docenti */
	public Element createDocenti(Document d) {
		// Creo elemento docenti
		Element docenti = d.createElement("docenti");
		// Creo sott-elemento docente...
		Element docente = d.createElement("docente");
		// ... con un attributo rif
		docente.setAttribute("rif", "IDdocente");
		// Creo i sotto-sotto-elementi nome e cognome con relativi commenti che appendo
		Element nome = d.createElement("nome");
		Element cognome = d.createElement("cognome");
		nome.appendChild(d.createComment("Nome del docente"));
		cognome.appendChild(d.createComment("Cognome del docente"));
		// Appendo i sotto-sotto-elementi al sotto-elemento docente
		docente.appendChild(nome);
		docente.appendChild(cognome);
		// Appendo il sotto-elemento docente a docenti
		docenti.appendChild(docente);
		// Restituisco l'elemento docenti che verrà poi appeso
		return docenti;
	}

	/* Crea un template per l'elemento testi, simile a docenti */
	public Element createTesti(Document d) {
		// Creo l'elemento testi
		Element testi = d.createElement("testi");
		// Creo il sotto-elemento testo
		Element testo = d.createElement("testo");
		// Creo i sotto-sotto-elementi autore, titolo, editore. I commenti relativi e li 
		// appendo
		Element autore = d.createElement("autore");
		Element titolo = d.createElement("titolo");
		Element editore = d.createElement("editore");
		autore.appendChild(d.createComment("Autore del testo"));
		titolo.appendChild(d.createComment("Titolo del testo"));
		editore.appendChild(d.createComment("Editore del testo"));
		// Appendo i sotto-sotto-elementi al sotto-elemento
		testo.appendChild(autore);
		testo.appendChild(titolo);
		testo.appendChild(editore);
		// Appendo il sotto-elemento all'elemento testo
		testi.appendChild(testo);
		// Restituisco, verrà poi a sua volta appeso
		return testi;
	}

	/* Crea un template per l'elemento erogazione con le caratteristiche date */
	public Element createErogazione(Document d, int[] crediti) {
		// Creo l'elemento erogazione
		Element erogazione = d.createElement("erogazione");
		// Creo un array di stringhe con le tipologie (ad esempio A, B, C...)
		String[] tipologie = new String[] { "A", "B", "C", "D", "E", "F" };

		for (int i = 0; i < tipologie.length && i < crediti.length; ++i) {
			// Per ogni tipologia creo il sotto-elemento credito con annessi attributi...
			Element credito = d.createElement("crediti");
			credito.setAttribute("numero", String.valueOf(crediti[i]));
			credito.setAttribute("tipologia", tipologie[i]);
			// ...e lo appendo all'elemento erogazione
			erogazione.appendChild(credito);
		}
		// Creo i sotto-elementi periodo, cdl e anno, i relativi commenti e ....
		Element periodo = d.createElement("periodo");
		Element cdl = d.createElement("cdl");
		Element anno = d.createElement("anno");
		periodo.appendChild(d.createComment("Quadrimestre o semestre di erogazione"));
		cdl.appendChild(d.createComment("Nome del corso di laurea"));
		anno.appendChild(d.createComment("Anno di corso"));
		// ...li appendo all'elemento erogazione
		erogazione.appendChild(periodo);
		erogazione.appendChild(cdl);
		erogazione.appendChild(anno);
		// Restituisco l'elemento erogazione che verrà a sua volta appeso
		return erogazione;
	}

	// Versione senza DTD
	/* Creazione di un document attraverso la DocumentBuilderFactory */
	public Document createDocument() {
		// Ottengo un document builder factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setValidating(false); 				// Non uso il DTD, quindi nessuna validazione
		dbf.setNamespaceAware(false); 			// Ignoro l'uso di namespace
		try {
			DocumentBuilder db = dbf.newDocumentBuilder(); 	// Ottengo un documentbuilder dalla factory
			Document d = db.newDocument(); 					// Ottengo un documento dal document buider
			return d;
		} catch (ParserConfigurationException ex) {
			ex.printStackTrace();
			System.exit(10);
		}
		return null;
	}

	// Versione con DTD
	/*
	 * Creazione di un document attraverso la DocumentBuilderFactory Implementazione
	 * simile alla precendente ma con l'uso d un DTD
	 */
	public Document createDocumentDTD(String radice, String pubid, String sysid) {
		// Ottengo un document builder factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setValidating(false); 		// Non uso il DTD per il momento
		dbf.setNamespaceAware(false); 	// Ignoro l'uso di namespace
		try {
			DocumentBuilder db = dbf.newDocumentBuilder(); 		// Ottengo un document builder
			DOMImplementation dbi = db.getDOMImplementation(); 	// Ottengo un'implementazione DOM
			// Creo un doctype che userò per la creazione del documento
			DocumentType doctype = dbi.createDocumentType(radice, pubid, sysid);
			// Creo il document
			Document d = dbi.createDocument("", radice, doctype);
			return d;
		} catch (ParserConfigurationException ex) {
			ex.printStackTrace();
			System.exit(10);
		}
		return null;
	}

	public boolean saveDocument(Document d, Writer w) {

		// Ottengo un load&save object, notare il cast
		DOMImplementationLS ls = (DOMImplementationLS) d.getImplementation();
		// Ottengo un oggetto per fare output
		LSOutput lso = ls.createLSOutput();

		// Creo un serializzatore
		LSSerializer lss = ls.createLSSerializer();
		try {
			lso.setCharacterStream(w); 			// Imposto il writer (parametro)
			lso.setEncoding("ISO-8859-1"); 		// imposto l'encoding
			// Formatta l'output aggiungendo spazi per produrre una stampa
			// "graziosa" (pretty-print) e indentata
			lss.getDomConfig().setParameter("format-pretty-print", true);
			// Uso il serializzatore per scrivere passando il documento e l'oggetto output
			// che contiene le informazioni per la scrittura (quale writer e quale codifica)
			lss.write(d, lso);

			return true;
		} catch (LSException lse) {
			return false;
		}
	}

	public static void main(String[] args) {

		if (args.length < 4) {
			System.err.println("SINTASSI: XMLCreate nome_corso codice creditiA creditiB CreditiC ...");
			System.exit(1);
		}

		// Gestione dei parametri di input
		int[] crediti = new int[args.length - 2];
		for (int i = 2; i < args.length; ++i)
			crediti[i - 2] = (Integer.parseInt(args[i]));

		// Creiamo un oggetto della classe XMLCreate, che è user-defined
		XMLCreate instance = new XMLCreate();

		// Creiamo un oggetto Document XML
		// Document d = instance.createDocumentDTD("corso","","corso.dtd"); //Versione con DTD
		Document d = instance.createDocument(); 							// Versione senza DTD

		// Creiamo il contenuto dell'albero XML specifico ad un corso
		instance.createCorso(d, args[0], args[1], crediti);
		// A questo punto abbiamo il contenuto dell'albero XML in memoria

		// Salviamo il documento XML (d) attraverso un filewriter a cui passiamo
		// il nome del file
		try {
			instance.saveDocument(d, new FileWriter("Corso_" + args[1] + ".xml"));
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		// Avremmo potuto salvare con un altro writer, ad esempio il PrintWriter
		// standard
		// indirizzato sullo standard output (console)
		// instance.saveDocument(d, new PrintWriter(System.out));
	}
}
