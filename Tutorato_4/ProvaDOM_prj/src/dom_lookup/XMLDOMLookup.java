package dom_lookup;
/*
 * XMLLookup.java
 *
 * @author Patrizia Scandurra
 * (originariamente preso da G. Della Penna 
 * http://www.di.univaq.it/gdellape/students.php?crs=mwtxml08)
 *
 * Questa classe mostra un esempio riassuntivo in cui
 * - un documento XML viene caricato in memoria
 * - si effettua una navigazione nel DOM alla ricerca di un particolare elemento
 * - si serializzano (su video) gli elementi individuati
 *
 * Per eseguirla è necessario che i jar di
 * Xerces siano nel classpath.
 */

import java.io.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.w3c.dom.ls.*;
import org.xml.sax.SAXException;

public class XMLDOMLookup {

	public XMLDOMLookup() {
	}

	// Fa il load di un documento
	public Document loadDocument(String path) {
		// Creiamo un'istanza del nostro gestore di errori custom
		MyErrorHandler h = new MyErrorHandler();
		// Ottengo l'istanza della document builder factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		dbf.setValidating(true);
		dbf.setNamespaceAware(false);
		try {
			// Ottengo l'istanza del document builder
			DocumentBuilder db = dbf.newDocumentBuilder();
			// Imposto l'error handler custom precedentemente ottenuto con l'apposito metodo
			db.setErrorHandler(h);
			// Ottengo il documento dal parsing sul file impostato
			Document d = db.parse(new File(path));

			if (h.hadProblems()) {
				System.out.println("Il documento contiene errori non fatali.");
			}
			return d;

		} catch (ParserConfigurationException ex) {
			ex.printStackTrace();
			System.exit(10);
		} catch (SAXException sxe) {
			System.out.println("Il documento contiene errori fatali!");
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return null;
	}

	// Filtra i docenti di un corso
	public NodeList docenti_lookup(Document d) {

		// Cerco (per nome) il tag "docenti"
		// Viene restituita come lista di nodi (nodelist)
		// anche se sappiamo che la lista avrà un solo elemento
		NodeList droot = d.getElementsByTagName("docenti");

		// Cerco nel sottoalbero del primo (e unico) elemento della lista di nodi con
		// nome "docenti" gli elementi il cui tag è "docente" e me li faccio restituire
		NodeList docenti = ((Element) droot.item(0)).getElementsByTagName("docente");
		// Ritorno il risultato
		return docenti;
	}

	// Save di un nodo su uno stream "Writer"
	public boolean saveNode(Node n, Writer w) {
		// Ottengo un load&save object, notare il cast
		DOMImplementationLS ls = (DOMImplementationLS) n.getOwnerDocument().getImplementation();
		// Ottengo un oggetto per fare output
		LSOutput lso = ls.createLSOutput();
		// Creo un serializzatore
		LSSerializer lss = ls.createLSSerializer();
		try {
			lso.setCharacterStream(w); 		// Imposto il writer (parametro)
			lso.setEncoding("ISO-8859-1"); 	// imposto l'encoding
			// Formatta l'output aggiungendo spazi per produrre una stampa
			// "graziosa" (pretty-print) e indentata
			lss.getDomConfig().setParameter("format-pretty-print", true);
			lss.getDomConfig().setParameter("xml-declaration", false);
			// Uso il serializzatore per scrivere passando il documento e l'oggetto output
			// che contiene le informazioni per la scrittura (quale writer e quale codif
			lss.write(n, lso);
			return true;
		} catch (LSException lse) {
			return false;
		}
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Sintassi: XMLDOMLookup nomefile.xml");
			System.exit(1);
		}
		// Crea un'istanza della classe XMLDOMLookup (questa)
		XMLDOMLookup instance = new XMLDOMLookup();

		// Carica in memoria il documento indicato dal primo argomento
		Document d = instance.loadDocument(args[0]);

		if (d != null) { // se il caricamento � andato a buon fine
			// Cerchiamo la lista di docenti attraverso l'apposito metodo
			NodeList risultati = instance.docenti_lookup(d);

			// Impostiamo il video come stream di output
			Writer output = new PrintWriter(System.out);

			// Stampa la lista dei docenti trovati
			for (int i = 0; i < risultati.getLength(); ++i) {
				System.out.println("--- RISULTATO #" + (i + 1));
				instance.saveNode(risultati.item(i), output); // output = System.out
			}
			System.out.println("------------------");
		}
	}
}
