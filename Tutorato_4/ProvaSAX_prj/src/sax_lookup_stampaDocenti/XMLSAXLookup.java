package sax_lookup_stampaDocenti;
/*
 * XMLSAXLookup.java
 *
 * @author Patrizia Scandurra
 * Questa classe mostra un esempio d'uso del parser SAX per effettuare una
 * semplice ricerca su file XML.
 * 
 * La classe è in verità riusabile perchè fa riferimento ad un gestore 
 * eventi separato.
 *
 */

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class XMLSAXLookup {

	public void lookupDocument(String path) {
		LookupHandler h = new LookupHandler();
		SAXParserFactory spf = SAXParserFactory.newInstance();
		//spf.setValidating(true); 	// con DTD
		spf.setValidating(false); 	// senza DTD
		spf.setNamespaceAware(false);
		try {
			SAXParser sp = spf.newSAXParser();
			sp.parse(path,h);
			if (h.hadProblems()) {
				System.out.println("Il documento contiene errori non fatali.");
			}            
		} catch (ParserConfigurationException ex) {
			ex.printStackTrace();
			System.exit(10);
		} catch (SAXException sxe) {
			System.out.println("Il documento contiene errori fatali!");
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	public static void main(String[] args) {
		/*if (args.length<1) {
			System.out.println("Sintassi: XMLSAXLookup nomefile.xml");
			System.exit(1);
		}*/
		XMLSAXLookup instance = new XMLSAXLookup();
		//instance.lookupDocument(args[0]);
		instance.lookupDocument("corso_x.xml");

	}
}
