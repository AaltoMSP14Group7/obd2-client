package fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions;

import java.util.HashMap;
import java.util.Map;


public class FunctionRegistry {
	
	private static Map<String,FilterFunction> functionRegistry;
	
	static {
		functionRegistry = new HashMap<String, FilterFunction>();
		functionRegistry.put("minimum", new MinValueFilterFunction());
		functionRegistry.put("maximum", new MaxValueFilterFunction());
		functionRegistry.put("current", new CurrentValueFilterFunction());
		functionRegistry.put("average", new AverageValueFilterFunction());
		functionRegistry.put("variance", new VarianceValueFilterFunction());
		functionRegistry.put("median", new MedianValueFilterFunction());
	}

	public static synchronized FilterFunction getFunctionFromRegistry(String functionName) {
		if (functionRegistry.containsKey(functionName)) {
			try {
				return functionRegistry.get(functionName).getClass().newInstance();
			} catch (InstantiationException | IllegalAccessException e) {}
		}
		return null;
	}
	
}
