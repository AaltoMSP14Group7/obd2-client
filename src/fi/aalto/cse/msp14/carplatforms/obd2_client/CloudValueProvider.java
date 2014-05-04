package fi.aalto.cse.msp14.carplatforms.obd2_client;

import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;


/**
 * This class is here for the development purposes. To be replaced with the real CloudValueProviderInterface when they are implemented.
 * @author Maria
 *
 */
public interface CloudValueProvider {

	public void tickQuery();
	public void tickOutput() throws NoValueException;
	public long getQueryTickInterval();
	public long getOutputTickInterval();
	
}
