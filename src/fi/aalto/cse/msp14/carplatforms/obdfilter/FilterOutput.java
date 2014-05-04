package fi.aalto.cse.msp14.carplatforms.obdfilter;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions.FilterFunction;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions.FunctionRegistry;


public class FilterOutput {

	private String name;
	private FilterFunction function;


	public FilterOutput(Element e) throws FilterParseError {
		
		if (e.getAttributeNode("name") == null)
			throw new FilterParseError("output, missing name attribute");
		this.name = e.getAttributeNode("name").getNodeValue();
		
		if (e.getAttributeNode("filter") == null)
			throw new FilterParseError("output, missing filter attribute");
		this.function = FunctionRegistry.getFunctionFromRegistry(e.getAttributeNode("filter").getNodeValue());
		
		if (this.function == null)
			throw new FilterParseError("output, illegal filter");
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