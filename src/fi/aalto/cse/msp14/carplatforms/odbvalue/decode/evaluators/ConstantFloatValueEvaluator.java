package fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators;

import java.util.ArrayList;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDSourceDefinition;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.EvaluatorParseError;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.XmlDefinedValueEvaluator;

public class ConstantFloatValueEvaluator extends XmlDefinedValueEvaluator {
	private final float m_constant;
	
	public ConstantFloatValueEvaluator(Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		final ArrayList<Element> childElements = getChildElements(e);
		
		if (childElements.size() != 0)
			throw new EvaluatorParseError(e.getTagName() + ": Expected 0 children, got: " + childElements.size());
		
		final Attr valueAttribute = e.getAttributeNode("value");
		if (valueAttribute == null)
			throw new EvaluatorParseError(e.getTagName() + ": missing 'value' attribute");
		
		try {
			m_constant = Float.parseFloat(valueAttribute.getNodeValue());	
		} catch (NumberFormatException ex) {
			throw new EvaluatorParseError(e.getTagName() + ": 'value' attribute value '" + valueAttribute.getNodeValue() + "' is not a valid float");
		}
	}

	@Override
	public int evaluateInteger(EvaluationContext context) throws DecodeFailure {
		return 0; // never called
	}

	@Override
	public float evaluateFloat(EvaluationContext context) throws DecodeFailure {
		return m_constant;
	}

	@Override
	public Type getReturnType() {
		return Type.FLOAT;
	}
}
