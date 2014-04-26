package fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators;

import java.util.ArrayList;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDSourceDefinition;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.EvaluatorParseError;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.IValueEvaluator;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.ValueEvaluatorFactory;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.XmlDefinedValueEvaluator;

public class ToFloatValueEvaluator extends XmlDefinedValueEvaluator {
	private final IValueEvaluator m_integerSource;
	
	public ToFloatValueEvaluator(Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		final ArrayList<Element> childElements = getChildElements(e);
		
		if (childElements.size() != 1)
			throw new EvaluatorParseError(e.getTagName() + ": Expected 1 child, got: " + childElements.size());
		
		m_integerSource = ValueEvaluatorFactory.parse(childElements.get(0), srcDef);
		
		// don't "cast" from float to float
		if (m_integerSource.getReturnType() == Type.FLOAT)
			throw new EvaluatorParseError(e.getTagName() + ": type mismatch, no overload for " + e.getTagName() + "(" + m_integerSource.getReturnType().toString() + ")");
	}

	@Override
	public int evaluateInteger(EvaluationContext context) throws DecodeFailure {
		return 0; // never called
	}

	@Override
	public float evaluateFloat(EvaluationContext context) throws DecodeFailure {
		return (float)m_integerSource.evaluateInteger(context);
	}

	@Override
	public Type getReturnType() {
		return Type.FLOAT;
	}
}
