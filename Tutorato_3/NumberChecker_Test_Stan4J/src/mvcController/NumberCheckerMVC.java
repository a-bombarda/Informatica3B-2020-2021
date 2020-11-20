package mvcController;

public class NumberCheckerMVC {
	// Main
	public static void main(String[] args) {
		// Creo il model
		NumberCheckerModel model = new NumberCheckerModel();
		// Creo la view passando il rif al model
		NumberCheckerView view = new NumberCheckerView(model);
		// Creo il controller passando il rif al model e alla view
		NumberCheckerController controller = new NumberCheckerController(model, view);
		// Rendo visibile la view
		view.setVisible(true);
	}
}
