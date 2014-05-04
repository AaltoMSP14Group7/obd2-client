package fi.aalto.cse.msp14.carplatforms.obdfilter;

import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions.FilterFunction;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions.FunctionRegistry;


public class FilterOutput {

	private String name;
	private FilterFunction function;


	public FilterOutput(String name, String functionName) {
		this.name = name;
		this.function = FunctionRegistry.getFunctionFromRegistry(functionName);
	}

	public FilterAggregate flushResult() throws NoValueException {
		return function.flushResult();
	}

	public FilterFunction getFunction() {
		return function;
	}

	public void setFunction(FilterFunction function) {
		this.function = function;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	

}