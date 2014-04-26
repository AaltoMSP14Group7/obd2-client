package fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.BinaryValueEvaluator;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.EvaluatorParseError;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.OBDSourceDefinition;

public class MulValueEvaluator extends BinaryValueEvaluator {

	public MulValueEvaluator(Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		super(e, srcDef, BinaryValueEvaluator.AllowedOverloads.ALLOW_FLOAT_AND_INT);
	}

	@Override
	public int evaluateInteger(EvaluationContext context) throws DecodeFailure {
		return m_left.evaluateInteger(context) * m_right.evaluateInteger(context);
	}

	@Override
	public float evaluateFloat(EvaluationContext context) throws DecodeFailure {
		return m_left.evaluateFloat(context) * m_right.evaluateFloat(context);
	}

	@Override
	public Type getReturnType() {
		// void  * void  -> void
		// void  * int   -> int
		// int   * int   -> int
		// void  * float -> float
		// float * float -> float
		// float * int   -> Error in ctor()
		
		if (m_left.getReturnType() != Type.VOID)
			return m_left.getReturnType();
		return m_right.getReturnType();
	}
}
