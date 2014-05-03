package fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions;

import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterAggregate;


public class AverageValueFilterFunction implements FilterFunction{

	private final Object dataLock;
	private boolean hasValue;
	private float average;
	private long timestamp;
	private int n;


	public AverageValueFilterFunction() {
		this.dataLock = new Object();
		this.hasValue = false;
	}

	public void addSample(float value, long timestamp) {
		synchronized(dataLock) {
			if(!hasValue) {
				average = (average + value) / n++;
				this.timestamp = timestamp;
				hasValue = true;
			}
		}
	}

	public FilterAggregate flushResult() throws NoValueException {
		synchronized(dataLock) {
			if(!hasValue) {
				throw new NoValueException("");
			} else {
				hasValue = false;
				return new FilterAggregate(average, timestamp);
			}
		}
	}

}