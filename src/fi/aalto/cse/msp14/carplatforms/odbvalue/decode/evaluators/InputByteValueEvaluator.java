package fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators;

import java.util.ArrayList;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDSourceDefinition;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.EvaluatorParseError;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.XmlDefinedValueEvaluator;

public class InputByteValueEvaluator extends XmlDefinedValueEvaluator {
	final int m_dataIndex;
	final boolean m_signed;
	
	public InputByteValueEvaluator(Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		final ArrayList<Element> childElements = getChildElements(e);
		
		if (childElements.size() != 0)
			throw new EvaluatorParseError(e.getTagName() + ": Expected 0 children, got: " + childElements.size());
		
		final Attr indexAttribute = e.getAttributeNode("index");
		if (indexAttribute == null)
			throw new EvaluatorParseError(e.getTagName() + ": missing 'index' attribute");
		
		final Attr signedAttribute = e.getAttributeNode("signed");
		if (signedAttribute == null)
			throw new EvaluatorParseError(e.getTagName() + ": missing 'signed' attribute");
		
		try {
			m_dataIndex = Integer.parseInt(indexAttribute.getNodeValue());	
		} catch (NumberFormatException ex) {
			throw new EvaluatorParseError(e.getTagName() + ": 'index' attribute value '" + indexAttribute.getNodeValue() + "' is not a valid integer");
		}
		
		if ("true".equals(signedAttribute.getNodeValue()))
			m_signed = true;
		else if ("false".equals(signedAttribute.getNodeValue()))
			m_signed = false;
		else
			throw new EvaluatorParseError(e.getTagName() + ": 'signed' attribute value '" + signedAttribute.getNodeValue() + "' is not a valid boolean. Expected true or false.");
		
		final int maxIndex = srcDef.nBytes - 1; 
		if (m_dataIndex < 0 || m_dataIndex > maxIndex)
			throw new EvaluatorParseError(e.getTagName() + ": 'index' attribute value was invalid, expected in range [0, " + maxIndex + "]"); 
	}

	@Override
	public int evaluateInteger(EvaluationContext context) throws DecodeFailure {
		if (m_signed)
			return (int)context.rawData[m_dataIndex];
		else
			return context.rawData[m_dataIndex] & (int)0xFF;
	}

	@Override
	public float evaluateFloat(EvaluationContext context) throws DecodeFailure {
		return 0.0f; // never used
	}

	@Override
	public Type getReturnType() {
		return Type.INTEGER;
	}
}
