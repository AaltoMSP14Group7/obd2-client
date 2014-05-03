package fi.aalto.cse.msp14.carplatforms.obd2_client;

import fi.aalto.cse.msp14.carplatforms.exceptions.IllegalThreadUseException;
import fi.aalto.cse.msp14.carplatforms.serverconnection.CloudService;
import fi.aalto.cse.msp14.carplatforms.serverconnection.NotCreatedYetException;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 
 * @author Maria
 *
 */
public class BootStrapper extends AsyncTask<Void, String, Boolean /* TODO into something more informative */> {
	
	private AsyncTask currentTask;
	private BootActivity parent; // Not just activity, because some things have to be passed to this one.
	private Intent cloud = null;
	
	/**
	 * 
	 * @param activity
	 */
	public BootStrapper(BootActivity activity) {
		assert(activity != null);
		parent = activity;
		ProgressBar pb = (ProgressBar) activity.findViewById(R.id.progressBar1);
		pb.setVisibility(View.VISIBLE);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		// TODO bluetooth
		publishProgress("Starting service");
        cloud = new Intent(parent, CloudService.class);
        parent.startService(cloud);
		
        
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
		
        // TODO Create CloudValueProviders. And remove these vvv
		publishProgress("Starting process");
		createCloudValueProviders(scheduler);
		if (this.isCancelled()) return false;

		publishProgress("Connection done!");
		return true;
	}

	/**
	 * TODO real things
	 * @param scheduler
	 */
	private void createCloudValueProviders(Scheduler scheduler) {
        TempCloudValueProviderInterface cvp1 = new TempCloudValueProviderInterface() {
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
        // TODO Remove those ^^^
	}

	/**
	 * TODO should return something and actually do something.
	 */
	private void fetchXMLSpecs() {
        try {
			CloudService.getInstance().getXML();
		} catch (IllegalThreadUseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (NotCreatedYetException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} // No problems if this blocks, because this is done in background!
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
		TextView tw = (TextView) parent.findViewById(R.id.textView3);
		tw.setText(newText);
	}

	@Override
	public void onPostExecute(Boolean v) {
		TextView tw = (TextView) parent.findViewById(R.id.textView3);
		if (v != null) { // Successful
			parent.changeButtonAppearance(ProgramState.STARTED);
		} else { // Not successful
			tw.setText(R.string.state_connection_failed);
			parent.changeButtonAppearance(ProgramState.IDLE);
		}
		ProgressBar pb = (ProgressBar) parent.findViewById(R.id.progressBar1);
		pb.setVisibility(View.GONE);

		// This should be changed?
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
    
    /**
     * This class only takes care of canceling everything what was done before.
     * @author Maria
     *
     */
    private class CancelBootStrapper extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
	        if (currentTask != null) {
	        	currentTask.cancel(true);
	        }
	        if (cloud != null) {
	        	parent.stopService(cloud);
	        }
	        
	        try {
	        	Thread.sleep(3000); // TODO cancelling everything
	        } catch (Exception e) {
	        }
			return null;
		}
		
		@Override
		protected void onPostExecute(Void v) {
			TextView tw = (TextView) parent.findViewById(R.id.textView3);
			tw.setText(R.string.state_cancelling);
			
	        parent.changeButtonAppearance(ProgramState.IDLE);
		}
    }
}
