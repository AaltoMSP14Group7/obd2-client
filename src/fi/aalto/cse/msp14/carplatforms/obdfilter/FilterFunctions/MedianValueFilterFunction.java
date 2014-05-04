package fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions;

import java.util.ArrayList;
import java.util.Collections;

import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterAggregate;


public class MedianValueFilterFunction implements FilterFunction {

	private final Object dataLock;
	private boolean hasValue;
	private float median;
	private ArrayList<Float> data;
	private long timestamp;


	public MedianValueFilterFunction() {
		this.dataLock = new Object();
		this.hasValue = false;
		this.data = new ArrayList<Float>();
	}

	public void addSample(float value, long timestamp) {
		synchronized(dataLock) {
			data.add(value);
			Collections.sort(data);
			median = data.get(data.size()/2);
			this.timestamp = timestamp;
			hasValue = true;
		}
	}

	public FilterAggregate flushResult() throws NoValueException {
		synchronized(dataLock) {
			if(!hasValue) {
				throw new NoValueException("");
			} else {
				hasValue = false;
				data.clear();
				return new FilterAggregate(median, timestamp);
			}
		}
	}

}