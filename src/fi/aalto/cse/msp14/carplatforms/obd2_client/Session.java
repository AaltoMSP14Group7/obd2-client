package fi.aalto.cse.msp14.carplatforms.obd2_client;

import android.content.Intent;

/**
 * This class is just a container.
 * 
 * @author Maria
 *
 */
public class Session {

	private Intent cloud;
	
	private Scheduler scheduler;
	
	private ProgramState state;

	/**
	 * 
	 * @return
	 */
	public Intent getCloud() {
		return cloud;
	}

	/**
	 * 
	 * @param cloud
	 */
	public void setCloud(Intent cloud) {
		this.cloud = cloud;
	}

	/**
	 * 
	 * @return
	 */
	public Scheduler getScheduler() {
		return scheduler;
	}

	/**
	 * 
	 * @param scheduler
	 */
	public void setScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}

	/**
	 * 
	 * @return
	 */
	public ProgramState getState() {
		return state;
	}

	/**
	 * 
	 * @param state
	 */
	public void setState(ProgramState state) {
		this.state = state;
	}
	
	// TODO all sources etc.
	
}
