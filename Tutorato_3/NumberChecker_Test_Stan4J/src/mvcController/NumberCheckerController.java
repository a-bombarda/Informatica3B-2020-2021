package mvcController;

import java.awt.event.*;

public class NumberCheckerController {
    //Riferimenti, il controller deve interagire sia con la view che con il modello
    private NumberCheckerModel m_model;
    private NumberCheckerView  m_view;
    
    
    //Costruttore
    NumberCheckerController(NumberCheckerModel model, NumberCheckerView view) {
    	//alloco i riferimenti passati
        m_model = model;
        m_view  = view;
        
        //Aggiungo i listener (definiti qui) alla view attraverso i metodi appositi 
        //messi a disposizione della view
        view.addCheckListener(new CheckListener());
    }
    
    
    /*
     * Definizione dei listener (Inner classes)
     */
    // Listener per il check del numero. Questa azione viene eseguita quando 
    // l'utente preme il tasto che richiede il controllo
    class CheckListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
        	System.out.println("[CONTROLLER] check action received");
            String userInput = "";
            try {
            	//uso il riferimento alla view per catturare l'input
            	//inserito dall'utente
                userInput = m_view.getUserInput();
                //uso il riferimento al modello per fargli eseguire la moltiplicazione
                m_model.checkNumber(userInput);
            } catch (NumberFormatException nfex) {
                m_view.showError("Bad input: '" + userInput + "'");
            }
        }
    }
}
