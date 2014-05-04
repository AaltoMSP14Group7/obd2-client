package fi.aalto.cse.msp14.carplatforms.obdfilter.FilterFunctions;

import java.util.ArrayList;
import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;
import fi.aalto.cse.msp14.carplatforms.obdfilter.FilterAggregate;


public class VarianceValueFilterFunction implements FilterFunction {

	private final Object dataLock;
	private boolean hasValue;
	private float variance;
	private ArrayList<Float> data;
	private float mean;
	private long timestamp;


	public VarianceValueFilterFunction() {
		this.dataLock = new Object();
		this.hasValue = false;
		this.data = new ArrayList<Float>();
	}

	public void addSample(float value, long timestamp) {
		synchronized(dataLock) {
			data.add(value);
			float tmp = 0;
			for(float f : data) {
				tmp += f;
			}
			mean = tmp/data.size();
			
			tmp = 0;
			for(float f : data) {
				tmp += (mean-f)*(mean-f);
			}
			variance = tmp/data.size();
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
				return new FilterAggregate(variance, timestamp);
			}
		}
	}

}