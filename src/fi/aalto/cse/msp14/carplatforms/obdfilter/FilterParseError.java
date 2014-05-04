package fi.aalto.cse.msp14.carplatforms.obdfilter;

public class FilterParseError extends Exception {
	private static final long serialVersionUID = -2994970126677141134L;

	public FilterParseError(String description) {
		super(description);
	}
}
