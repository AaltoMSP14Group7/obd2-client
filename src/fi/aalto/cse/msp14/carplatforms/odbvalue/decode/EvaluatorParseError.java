package fi.aalto.cse.msp14.carplatforms.odbvalue.decode;

public class EvaluatorParseError extends Exception {
	private static final long serialVersionUID = -1647353263357480016L;

	public EvaluatorParseError(final String description) {
		super(description);
	}
}
