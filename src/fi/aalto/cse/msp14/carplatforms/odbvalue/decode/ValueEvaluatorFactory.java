package fi.aalto.cse.msp14.carplatforms.odbvalue.decode;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDSourceDefinition;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.*;

public class ValueEvaluatorFactory {
	static public IValueEvaluator parse (final Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		if ("mul".equals(e.getTagName()))
			return new MulValueEvaluator(e, srcDef);
		else if ("div".equals(e.getTagName()))
			return new DivValueEvaluator(e, srcDef);
		else if ("add".equals(e.getTagName()))
			return new AddValueEvaluator(e, srcDef);
		else if ("sub".equals(e.getTagName()))
			return new SubValueEvaluator(e, srcDef);
		else if ("or".equals(e.getTagName()))
			return new OrValueEvaluator(e, srcDef);
		else if ("xor".equals(e.getTagName()))
			return new XorValueEvaluator(e, srcDef);
		else if ("and".equals(e.getTagName()))
			return new AndValueEvaluator(e, srcDef);
		else if ("equal".equals(e.getTagName()))
			return new EqualValueEvaluator(e, srcDef);
		else if ("notEqual".equals(e.getTagName()))
			return new NotEqualValueEvaluator(e, srcDef);
		else if ("greater".equals(e.getTagName()))
			return new GreaterValueEvaluator(e, srcDef);
		else if ("greaterOrEqual".equals(e.getTagName()))
			return new GreaterOrEqualValueEvaluator(e, srcDef);
		else if ("less".equals(e.getTagName()))
			return new LessValueEvaluator(e, srcDef);
		else if ("lessOrEqual".equals(e.getTagName()))
			return new LessOrEqualValueEvaluator(e, srcDef);
		else if ("float".equals(e.getTagName()))
			return new ConstantFloatValueEvaluator(e, srcDef);
		else if ("integer".equals(e.getTagName()))
			return new ConstantIntValueEvaluator(e, srcDef);
		else if ("toFloat".equals(e.getTagName()))
			return new ToFloatValueEvaluator(e, srcDef);
		else if ("decodeFailure".equals(e.getTagName()))
			return new DecodeFailureValueEvaluator(e, srcDef);
		else if ("inputByte".equals(e.getTagName()))
			return new InputByteValueEvaluator(e, srcDef);
		else if ("cond".equals(e.getTagName()))
			return new ConditionalValueEvaluator(e, srcDef);

		throw new EvaluatorParseError("unknown element " + e.getTagName());
	}
}
