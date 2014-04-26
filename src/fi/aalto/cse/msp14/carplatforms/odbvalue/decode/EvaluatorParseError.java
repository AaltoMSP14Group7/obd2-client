package fi.aalto.cse.msp14.carplatforms.odbvalue.decode;

import fi.aalto.cse.msp14.carplatforms.odbvalue.ParseError;

public class EvaluatorParseError extends ParseError {
	private static final long serialVersionUID = -1647353263357480016L;

	public EvaluatorParseError(final String description) {
		super(description);
	}
}
