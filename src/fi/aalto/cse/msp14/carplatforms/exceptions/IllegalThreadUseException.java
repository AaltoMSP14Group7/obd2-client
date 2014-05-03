package fi.aalto.cse.msp14.carplatforms.exceptions;

public class IllegalThreadUseException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public IllegalThreadUseException(String message) {
		super(message);
	}
}
