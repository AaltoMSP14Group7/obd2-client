package fi.aalto.cse.msp14.carplatforms.odbvalue.decode;

import java.util.ArrayList;

import org.w3c.dom.Element;

public abstract class BinaryValueEvaluator extends XmlDefinedValueEvaluator {
	protected final IValueEvaluator m_left, m_right;
	
	public enum AllowedOverloads {
		ALLOW_FLOAT_AND_INT,
		ALLOW_ONLY_INT,
	};
	
	public BinaryValueEvaluator(final Element e, final OBDSourceDefinition srcDef, final AllowedOverloads allowedOverloads) throws EvaluatorParseError {
		final ArrayList<Element> childElements = getChildElements(e);
		
		if (childElements.size() != 2)
			throw new EvaluatorParseError(e.getTagName() + ": Expected 2 children, got: " + childElements.size());
		
		m_left = ValueEvaluatorFactory.parse(childElements.get(0), srcDef);
		m_right = ValueEvaluatorFactory.parse(childElements.get(1), srcDef);
		
		// no implicit float -> int casting
		if ((m_left.getReturnType() == Type.INTEGER && m_right.getReturnType() == Type.FLOAT) ||
			(m_left.getReturnType() == Type.FLOAT && m_right.getReturnType() == Type.INTEGER))
			throw new EvaluatorParseError(e.getTagName() + ": type mismatch, no overload for " + e.getTagName() + "(" + m_left.getReturnType().toString() + ", " + m_right.getReturnType().toString() + ")");
		
		// float allowed?
		if (allowedOverloads != AllowedOverloads.ALLOW_FLOAT_AND_INT) {
			if (m_left.getReturnType() == Type.FLOAT || m_right.getReturnType() == Type.FLOAT)
				throw new EvaluatorParseError(e.getTagName() + ": type mismatch, no overload for " + e.getTagName() + "(" + m_left.getReturnType().toString() + ", " + m_right.getReturnType().toString() + ")");
		}
	}
}
