package fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.BinaryValueEvaluator;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.EvaluatorParseError;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.OBDSourceDefinition;

public class LessValueEvaluator extends BinaryValueEvaluator {

	public LessValueEvaluator(Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		super(e, srcDef, BinaryValueEvaluator.AllowedOverloads.ALLOW_FLOAT_AND_INT);
	}

	@Override
	public int evaluateInteger(EvaluationContext context) throws DecodeFailure {
		// void  * void  -> doesn't matter
		// void  * int   -> doesn't matter
		// void  * float -> doesn't matter
		// int   * int   -> int
		// float * float -> float
		// float * int   -> Error in ctor()
		
		if (m_left.getReturnType() == Type.FLOAT || m_right.getReturnType() == Type.FLOAT)
			return (m_left.evaluateFloat(context) < m_right.evaluateFloat(context)) ? (1) : (0);
		else
			return (m_left.evaluateInteger(context) < m_right.evaluateInteger(context)) ? (1) : (0);
	}

	@Override
	public float evaluateFloat(EvaluationContext context) throws DecodeFailure {
		return 0.0f; // never called
	}

	@Override
	public Type getReturnType() {
		return Type.INTEGER;
	}
}
