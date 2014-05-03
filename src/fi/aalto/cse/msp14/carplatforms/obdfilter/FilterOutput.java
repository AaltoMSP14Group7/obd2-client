package fi.aalto.cse.msp14.carplatforms.obdfilter;

import org.w3c.dom.Element;

import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions.FilterFunction;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions.FunctionRegistry;
import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDDataSource;


public class FilterOutput {

	private String name;
	private float outputRate;
	private FilterFunction function;


	public FilterOutput(OBDDataSource source, Element e) {
		this.name = e.getAttribute("name");
		this.outputRate = Float.parseFloat(e.getAttribute("outputRate"));
		this.function = FunctionRegistry.getFunctionFromRegistry(e.getAttribute("filter"));
	}

	public FilterAggregate flushResult() throws Exception {
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

	public float getOutputRate() {
		return outputRate;
	}

	public void setOutputRate(float outputRate) {
		this.outputRate = outputRate;
	}
	

}