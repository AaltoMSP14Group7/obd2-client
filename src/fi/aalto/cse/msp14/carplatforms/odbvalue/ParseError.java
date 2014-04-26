package fi.aalto.cse.msp14.carplatforms.odbvalue;

public class ParseError extends Exception {
	private static final long serialVersionUID = 4821464318696758217L;

	public ParseError(final String description) {
		super(description);
	}
}
