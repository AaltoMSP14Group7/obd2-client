package fi.aalto.cse.msp14.carplatforms.obd2_client;


/**
 * This class is here for the development purposes. To be replaced with the real CloudValueProviderInterface when they are implemented.
 * @author Maria
 *
 */
public interface TempCloudValueProviderInterface {

	public void tick();
	public long getQueryTickInterval();
	public long getOutputTickInterval();
	
}
