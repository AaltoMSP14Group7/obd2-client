package fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDSourceDefinition;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.BinaryValueEvaluator;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.EvaluatorParseError;

public class XorValueEvaluator extends BinaryValueEvaluator {

	public XorValueEvaluator(Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		super(e, srcDef, BinaryValueEvaluator.AllowedOverloads.ALLOW_ONLY_INT);
	}

	@Override
	public int evaluateInteger(EvaluationContext context) throws DecodeFailure {
		return m_left.evaluateInteger(context) ^ m_right.evaluateInteger(context);
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
