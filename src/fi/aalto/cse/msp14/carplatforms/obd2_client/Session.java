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
	
	private ProgramState state  = ProgramState.IDLE;

	private boolean active;
	
	private static final Session instance = new Session();
	
	private Session() {
		active = false;
	}

	/**
	 * Because of lifecycle of Activities in Android, preserving this data is
	 * easiest if using just a singleton. (Otherwise should be using 
	 * for example Parcelable and it would mean a lot of work for nothing.)
	 * 
	 * @return
	 */
	public static Session getSession() {
		return instance;
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean isActive() {
		return this.active;
	}
	
	/**
	 * 
	 * @param active
	 */
	public void setActive(boolean active) {
		if (!active) {
			this.cloud = null;
			this.scheduler = null;
			this.state = ProgramState.IDLE;
		}
		this.active = active;
	}

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
