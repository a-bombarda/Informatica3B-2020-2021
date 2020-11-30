package dom_lookup;

public class MyErrorHandler implements org.xml.sax.ErrorHandler {
	// Contatori
	private int nErrors, nWarnings, nFatals;

	// Costruttore
	public MyErrorHandler() {
		reset();
	}

	// Reset dei contatori
	public void reset() {
		nErrors = 0;
		nWarnings = 0;
		nFatals = 0;
	}

	/*
	 * Questi metodi implementano l'interfaccia org.xml.sax.ErrorHandler Vengono
	 * chiamati dal DocumentBuilder quando, durante il parsing, si verificano: -
	 * errori gravi (fatal) - errori - warning Senza questo handler non potrei
	 * sapere se, ed in che punto, si sono verificati errori durante il parsing.
	 */
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

	// Metodi per ottenere il valore dei contatori e lo stato generale
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

}
