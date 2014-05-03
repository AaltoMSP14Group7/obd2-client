package fi.aalto.cse.msp14.carplatforms.obd2_client;

import fi.aalto.cse.msp14.carplatforms.exceptions.IllegalThreadUseException;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * TODO Remove
 * @author Maria
 *
 */
public class BootStrapperTaskOld extends AsyncTask<Void, String, Boolean /* TODO into something more informative */> {
	
	private AsyncTask currentTask;
	private OBD2Service parent; // Not just activity, because some things have to be passed to this one.
	
	/**
	 * 
	 * @param activity
	 */
	public BootStrapperTaskOld(OBD2Service activity) {
		assert(activity != null);
		parent = activity;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		// TODO bluetooth
		publishProgress("Starting service");
        
        publishProgress("Connecting bluetooth");
		connectBluetooth();
		if (this.isCancelled()) return false;
		
		publishProgress("Creating content");
		Scheduler scheduler = new Scheduler(); // TODO something with it.
        scheduler.start();

        String deviceID = createID();
		if (this.isCancelled()) return false;
        
        // TODO Now, fetch from cloud that what to get
		fetchXMLSpecs();
		if (this.isCancelled()) return false;
        
        // TODO Create OBD sources
		publishProgress("Starting process");
		createCloudValueProviders(scheduler); // TODO Create CloudValueProviders.
		if (this.isCancelled()) return false;

		publishProgress("Connection done!");
		
/*		if (!this.isCancelled()) {
			Session s = Session.getSession();
			s.setActive(true);
			s.setScheduler(scheduler);
			s.setState(ProgramState.STARTED);
		}*/
		return true;
	}

	/**
	 * TODO real things
	 * @param scheduler
	 */
	private void createCloudValueProviders(Scheduler scheduler) {
/*        TempCloudValueProviderInterface cvp1 = new TempCloudValueProviderInterface() {
			@Override
			public void tick() {
				// Do nothing
				System.out.println("TICK 1");
			}
			@Override
			public long getQueryTickInterval() {
				return 10000;
			}
			@Override
			public long getOutputTickInterval() {
				return 10000;
			}
        };

        TempCloudValueProviderInterface cvp2 = new TempCloudValueProviderInterface() {
			@Override
			public void tick() {
				// Do nothing
				System.out.println("TICK 2");
			}
			@Override
			public long getQueryTickInterval() {
				return 15000;
			}
			@Override
			public long getOutputTickInterval() {
				return 15000;
			}
        };

        if (this.isCancelled()) return;
        try {
            scheduler.registerFilter("Test1", cvp1);
            scheduler.registerFilter("Test2", cvp2);
        } catch (Exception e) {}
        // TODO Remove those ^^^*/
	}

	/**
	 * TODO should return something and actually do something.
	 */
	private void fetchXMLSpecs() {
/*		try {
			parent.getXML();
		} catch (IllegalThreadUseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}

	/**
	 * TODO Bluetooth connection.
	 * @return
	 */
	private boolean connectBluetooth() {
		try {
			Thread.sleep(2000); // TODO bluetooth connection waiting.
		} catch(Exception e) {}
		return true;
	}

	@Override
	public void onProgressUpdate(String... text) {
		if (text.length < 1) return;
		String newText = text[0];
	}

	@Override
	public void onPostExecute(Boolean v) {
		// TODO This should be changed?
	}
	
	/**
	 * Creates some sort of hopefully unique ID for this device.
	 * @return
	 */
	private String createID() {
		TelephonyManager tm = (TelephonyManager) parent.getSystemService(Context.TELEPHONY_SERVICE);
		String tohash = tm.getDeviceId(); // TODO real id fetching
		return tohash;
	}

    @Override
    protected void onCancelled(Boolean v) {
        super.onCancelled();
        System.out.println("CANCEL!");
        new CancelBootStrapper().execute();
    }
    
    /*
     * ------------------------------------------------------------------------
     * Another class
     * ------------------------------------------------------------------------
     */

    /**
     * This class only takes care of canceling everything what was done before.
     * This is needed if in the middle of initialization (before the task is finished)
     * the user decides to cancel whole the connection thing.
     * 
     * @author Maria
     *
     */
    private class CancelBootStrapper extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			// TODO 
	        if (currentTask != null) {
	        	currentTask.cancel(true);
	        }
	        try {
	        	Thread.sleep(3000); // TODO cancelling everything
	        } catch (Exception e) {
	        }
			return null;
		}
		
		@Override
		protected void onPostExecute(Void v) {
			// TODO 
		}
    }
}
