package fi.aalto.cse.msp14.carplatforms.odbvalue.decode;

public interface IValueEvaluator {
	@SuppressWarnings("serial")
	public static class DecodeFailure extends Exception {
	}
	
	/* package private */ static enum Type {
		FLOAT,
		INTEGER,
		/**
		 * Decode error type
		 */
		VOID
	}
	
	public static class EvaluationContext {
		public final byte[] rawData;
		
		public EvaluationContext (final byte[] rawData_) {
			rawData = rawData_;
		}
	}
	
	/* package private */ abstract int evaluateInteger(final EvaluationContext context) throws DecodeFailure;
	/* package private */ abstract float evaluateFloat(final EvaluationContext context) throws DecodeFailure;
	/* package private */ abstract Type getReturnType();
}
