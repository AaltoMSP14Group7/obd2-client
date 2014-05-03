package fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions;

import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterAggregate;


public interface FilterFunction {

	void addSample(float value, long timestamp);
	FilterAggregate flushResult() throws NoValueException;
}