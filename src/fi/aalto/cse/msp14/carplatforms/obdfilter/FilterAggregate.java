package fi.aalto.cse.msp14.carplatforms.obdfilter;


public class FilterAggregate {

	private float value;
	private long timestamp;

	public FilterAggregate(float value, long timestamp) {
		this.value = value;
		this.timestamp = timestamp;
	}

	public float getValue() {
		return value;
	}

	public void setValue(float value) {
		this.value = value;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}


}