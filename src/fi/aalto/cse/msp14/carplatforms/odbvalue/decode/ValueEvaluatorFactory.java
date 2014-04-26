package fi.aalto.cse.msp14.carplatforms.odbvalue.decode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import org.w3c.dom.Element;

public class ValueEvaluatorFactory {
	static private HashMap<String, Constructor<?>> m_evaluatorConstructionRegistry = createConstructionRegistry();
	
	static public IValueEvaluator parse (final Element e, final OBDSourceDefinition srcDef) throws EvaluatorParseError {
		// check the registry
		final Constructor<?> evaluatorConstructor = m_evaluatorConstructionRegistry.get(e.getTagName()); 
		if (evaluatorConstructor == null)
			new EvaluatorParseError("unknown element " + e.getTagName());
		
		// construct
		try {
			return (IValueEvaluator) evaluatorConstructor.newInstance(e, srcDef);
		} catch (InvocationTargetException ex) {
			// constructor failed, detect parse errors and extract them
			if (ex.getCause() != null && ex.getCause() instanceof EvaluatorParseError)
				throw (EvaluatorParseError)ex.getCause();
			
			// should never happen, just forward as RuntimeException
			throw new RuntimeException("failed to constuct evaluation tree", ex);
		} catch (Exception ex) {
			// should never happen, just forward as RuntimeException
			throw new RuntimeException("failed call constructor", ex);
		}
	}
	
	static private synchronized HashMap<String, Constructor<?>> createConstructionRegistry() {
		final HashMap<String, Constructor<?>> registry = new HashMap<String, Constructor<?>>();
		
		try {
			registry.put("mul", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.MulValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("div", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.DivValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("add", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.AddValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("sub", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.SubValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("or",  			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.OrValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("xor", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.XorValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("and", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.AndValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("equal", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.EqualValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("notEqual", 		Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.NotEqualValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("greater", 		Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.GreaterValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("greaterOrEqual", 	Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.GreaterOrEqualValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("less", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.LessValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("lessOrEqual", 	Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.LessOrEqualValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("float", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.ConstantFloatValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("integer", 		Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.ConstantIntValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("toFloat", 		Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.ToFloatValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("decodeFailure", 	Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.DecodeFailureValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("inputByte", 		Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.InputByteValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
			registry.put("cond", 			Class.forName("fi.aalto.cse.msp14.carplatforms.odbvalue.decode.evaluators.ConditionalValueEvaluator").getConstructor(Element.class, OBDSourceDefinition.class));
		} catch (Exception ex) {
			// should never happen, just forward as RuntimeException
			throw new RuntimeException("failed to constuct registry", ex);
		}
		
		return registry;
	}
}
