package fi.aalto.cse.msp14.carplatforms.obd2_client;

import android.content.Context;
import fi.aalto.cse.msp14.carplatforms.obdlink.OBDLinkManager;


/**
 * This class is far from being thread safe, so use carefully!
 * It is recommended that only one thread uses this.
 * Great design, n'est-ce pas!
 * @author 
 */
public class TempBluetoothConnectionManager {
	private OBDLinkManager obdLink;
	private Context appContext;
	private LinkStateListener m_linkListener;
	private boolean success;
	private static String mostRecentVin = null;
	private boolean waitingForVin;
	
	private boolean mostRecentPowerstate = false;
	private OBDLinkManager.LinkState mostRecentState = null;
	private static TempBluetoothConnectionManager instance = new TempBluetoothConnectionManager();
	
	private static boolean hasStarted = false;
	
	/**
	 * 
	 * @param service
	 * @param c
	 */
	private TempBluetoothConnectionManager() {
        waitingForVin = false;
		success = false;
	}
	
	public static TempBluetoothConnectionManager getInstance() {
		return instance;
	}

	/**
	 * 
	 */
	public void setContext(Context contex) {
		if (!hasStarted) {
			this.appContext = contex;
			m_linkListener = new LinkStateListener();
			obdLink = OBDLinkManager.getInstance(appContext);
	        obdLink.addEventListener(m_linkListener);
	        obdLink.addEventListener(new VINGetter());
			obdLink.startListening();
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public OBDLinkManager getThisOBDLinkManager() {
		return obdLink;
	}

	/**
	 * This method blocks until connection has been established.
	 * @return
	 */
	public synchronized boolean connect() {
		while (mostRecentState != OBDLinkManager.LinkState.STATE_ON) {
			try {
				wait();
			} catch (InterruptedException e) {
				return false;
			}
		}
		return success;
	}
	
	/**
	 * 
	 */
	public synchronized void disconnect() {
		/* Naah...
		obdLink.removeEventListener(m_linkListener);
		obdLink.stopListening();
		*/
	}
	
	/**
	 * If connection has not been established, cancel trying.
	 */
	public synchronized void cancel() {
		obdLink.removeEventListener(m_linkListener);
		obdLink.stopListening();
		success = false;
		notify();
	}
	
	/**
	 * Yeah yeah.
	 */
	private synchronized void notifyThis() {
		notify();
	}

	/**
	 * 
	 * @return
	 */
	public synchronized String waitForVin() {
		while (mostRecentVin == null) {
			waitingForVin = true;
			try {
				wait();
			} catch (InterruptedException e) {
				return null;
			}
			waitingForVin = false;
		}
		return mostRecentVin;
	}

	/**
	 * 
	 * @return
	 */
	public synchronized boolean waitForCarConnection() {
		while (mostRecentState != OBDLinkManager.LinkState.STATE_ON || !mostRecentPowerstate) {
			try {
				wait();
			} catch (InterruptedException e) {
				return false;
			}
		}
		return mostRecentPowerstate;
	}

	/**
	 * 
	 * @author 
	 *
	 */
	class LinkStateListener implements OBDLinkManager.StateEventListener {
		@Override
		public void onStateChange(final OBDLinkManager.LinkState state, final boolean powerState, final String reason) {
			mostRecentPowerstate = powerState;
			mostRecentState = state;
			
			if (state == OBDLinkManager.LinkState.STATE_ON) {
				// Successful!
				success = true;
			} else if (state != OBDLinkManager.LinkState.STATE_TURNING_ON) {
				// Not successful.
				success = false;
			} else if (!waitingForVin) {
				return;
			}
			TempBluetoothConnectionManager.this.notifyThis();
		}
	}
	
	/**
	 * 
	 * @author 
	 *
	 */
	class VINGetter implements OBDLinkManager.ConfigurationEventListener {
		@Override
		public void onConfigurationChanged(String targetVIN) {
			System.out.println("Setting VIN " + targetVIN);
			mostRecentVin = targetVIN;
			TempBluetoothConnectionManager.this.notifyThis();
		}
    }
}
