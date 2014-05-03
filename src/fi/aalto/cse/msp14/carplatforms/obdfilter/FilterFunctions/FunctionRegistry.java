package fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions;

import java.util.Map;


public class FunctionRegistry {
	
	private static Map<String,FilterFunction> functionRegistry;
	
	public FunctionRegistry() {
		functionRegistry.put("minimum", new MinValueFilterFunction());
		functionRegistry.put("maximum", new MaxValueFilterFunction());
		functionRegistry.put("current", new CurrentValueFilterFunction());
		functionRegistry.put("average", new AverageValueFilterFunction());
		functionRegistry.put("variance", new VarianceValueFilterFunction());
	}

	public static FilterFunction getFunctionFromRegistry(String functionName) {
		
		if (functionRegistry.containsKey(functionName)) {
			try {
				return functionRegistry.get(functionName).getClass().newInstance();
			} catch (InstantiationException | IllegalAccessException e) {}
		}
		return null;
	}
	
}
