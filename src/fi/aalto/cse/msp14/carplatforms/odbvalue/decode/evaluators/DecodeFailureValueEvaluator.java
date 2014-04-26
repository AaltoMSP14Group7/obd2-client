package fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators;

import java.util.ArrayList;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDSourceDefinition;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.EvaluatorParseError;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.XmlDefinedValueEvaluator;

public class DecodeFailureValueEvaluator extends XmlDefinedValueEvaluator {
	
	public DecodeFailureValueEvaluator(Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		final ArrayList<Element> childElements = getChildElements(e);
		
		if (childElements.size() != 0)
			throw new EvaluatorParseError(e.getTagName() + ": Expected 0 children, got: " + childElements.size());
	}

	@Override
	public int evaluateInteger(EvaluationContext context) throws DecodeFailure {
		throw new DecodeFailure();
	}

	@Override
	public float evaluateFloat(EvaluationContext context) throws DecodeFailure {
		throw new DecodeFailure();
	}

	@Override
	public Type getReturnType() {
		return Type.VOID;
	}
}
