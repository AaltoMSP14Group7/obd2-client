package fi.aalto.cse.msp14.carplatforms.obd2_client;

import android.content.Context;
import fi.aalto.cse.msp14.carplatforms.obdlink.OBDLinkManager;

public class TempBluetoothConnectionManager {
	private OBDLinkManager obdLink;
	
	private Context appContext;

	private LinkStateListener m_linkListener;
	
	private boolean success;
	
	public TempBluetoothConnectionManager(Context c) {
		this.appContext = c;
		success = false;

		m_linkListener = new LinkStateListener();
		obdLink = OBDLinkManager.getInstance(appContext);
        obdLink.addEventListener(m_linkListener);
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
		obdLink.startListening();
		try {
			wait();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return success;
	}
	
	/**
	 * 
	 */
	public synchronized void disconnect() {
		obdLink.removeEventListener(m_linkListener);
		obdLink.stopListening();
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

	
	class LinkStateListener implements OBDLinkManager.StateEventListener {
		@Override
		public void onStateChange(final OBDLinkManager.LinkState state,
				final boolean powerState, final String reason) {
			System.out.println(state + "; " + powerState + ": " + reason);
			if (state == OBDLinkManager.LinkState.STATE_ON) {
				// Successful!
				success = true;
			} else if (state != OBDLinkManager.LinkState.STATE_TURNING_ON) {
				// Not successful.
				success = false;
			} else {
				return;
			}
			TempBluetoothConnectionManager.this.notifyThis();
		}
	}
}
