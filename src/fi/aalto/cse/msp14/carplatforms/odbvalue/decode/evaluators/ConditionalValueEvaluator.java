package fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators;

import java.util.ArrayList;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.EvaluatorParseError;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.IValueEvaluator;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.OBDSourceDefinition;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.ValueEvaluatorFactory;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.XmlDefinedValueEvaluator;

public class ConditionalValueEvaluator extends XmlDefinedValueEvaluator {
	final IValueEvaluator m_test;
	final IValueEvaluator m_onTrue;
	final IValueEvaluator m_onFalse;
	
	public ConditionalValueEvaluator(Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		final ArrayList<Element> childElements = getChildElements(e);
		
		if (childElements.size() != 3)
			throw new EvaluatorParseError(e.getTagName() + ": Expected 3 children, got: " + childElements.size());
		if (!"test".equals(childElements.get(0).getTagName()))
			throw new EvaluatorParseError(e.getTagName() + ": Expected 'test' at index 0, got " + childElements.get(0).getTagName());
		if (!"onTrue".equals(childElements.get(1).getTagName()))
			throw new EvaluatorParseError(e.getTagName() + ": Expected 'onTrue' at index 1, got " + childElements.get(1).getTagName());
		if (!"onFals".equals(childElements.get(2).getTagName()))
			throw new EvaluatorParseError(e.getTagName() + ": Expected 'onFalse' at index 2, got " + childElements.get(2).getTagName());
		
		// check test element
		{
			final ArrayList<Element> subElements = getChildElements(childElements.get(0));
			
			if (subElements.size() != 1)
				throw new EvaluatorParseError(e.getTagName() + ": Expected 1 child for 'test', got: " + subElements.size());
			
			m_test = ValueEvaluatorFactory.parse(subElements.get(0), srcDef);
			
			if (m_test.getReturnType() == Type.FLOAT)
				throw new EvaluatorParseError(e.getTagName() + ": test element type, expected integer, got float");
		}
		
		// check result elements
		{
			final ArrayList<Element> trueSubElements = getChildElements(childElements.get(1));
			final ArrayList<Element> falseSubElements = getChildElements(childElements.get(0));
			
			if (trueSubElements.size() != 1)
				throw new EvaluatorParseError(e.getTagName() + ": Expected 1 child for 'onTrue', got: " + trueSubElements.size());
			if (falseSubElements.size() != 1)
				throw new EvaluatorParseError(e.getTagName() + ": Expected 1 child for 'onFalse', got: " + falseSubElements.size());
			
			m_onTrue = ValueEvaluatorFactory.parse(trueSubElements.get(0), srcDef);
			m_onFalse = ValueEvaluatorFactory.parse(falseSubElements.get(0), srcDef);
			
			if ((m_onTrue.getReturnType() == Type.INTEGER && m_onFalse.getReturnType() == Type.FLOAT) ||
				(m_onTrue.getReturnType() == Type.FLOAT && m_onFalse.getReturnType() == Type.INTEGER))
				throw new EvaluatorParseError(e.getTagName() + ": type mismatch int 'onTrue' and 'onFalse', branches must have the same return value type.");
		}
	}

	@Override
	public int evaluateInteger(EvaluationContext context) throws DecodeFailure {
		if (m_test.evaluateInteger(context) != 0)
			return m_onTrue.evaluateInteger(context);
		else
			return m_onFalse.evaluateInteger(context);
	}

	@Override
	public float evaluateFloat(EvaluationContext context) throws DecodeFailure {
		if (m_test.evaluateInteger(context) != 0)
			return m_onTrue.evaluateFloat(context);
		else
			return m_onFalse.evaluateFloat(context);
	}

	@Override
	public Type getReturnType() {
		// void  * void  -> void
		// void  * int   -> int
		// int   * int   -> int
		// void  * float -> float
		// float * float -> float
		// float * int   -> Error in ctor()
		
		if (m_onTrue.getReturnType() != Type.VOID)
			return m_onTrue.getReturnType();
		return m_onFalse.getReturnType();
	}
}
