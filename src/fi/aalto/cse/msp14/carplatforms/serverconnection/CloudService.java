package fi.aalto.cse.msp14.carplatforms.serverconnection;

import java.util.ArrayList;

import fi.aalto.cse.msp14.carplatforms.obd2_client.CloudValueProvider;
import fi.aalto.cse.msp14.carplatforms.obd2_client.ProgramState;
import fi.aalto.cse.msp14.carplatforms.obd2_client.R;
import fi.aalto.cse.msp14.carplatforms.obd2_client.Scheduler;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * 
 * @author Maria
 */
public class CloudService extends Service {
	// These are identifiers for messages.
	public static final int MSG_START = 0;
	public static final int MSG_STOP = 1;
	public static final int MSG_CANCEL = 2;
	public static final int MSG_REGISTER_AS_STATUS_LISTENER = 3;
	public static final int MSG_UNREGISTER_AS_STATUS_LISTENER = 4;
	public static final int MSG_GET_XML = 5;
	public static final int MSG_MSG = 6;
	public static final int MSG_PUB_STATUS = 7;
	public static final int MSG_PUB_STATUS_TXT = 8;

	private static final String LOCK_TAG = "obd2datatocloud";
	
	private PowerManager.WakeLock wakelock;
	private static long t = System.nanoTime();
	
	private CloudConnection cloud;
	private BootStrapperTask task;
	
	private static boolean started = false;
	
	// Messaging related stuff
	final Messenger messenger = new Messenger(new IncomingHandler());
	ArrayList<Messenger> statusListeners = new ArrayList<Messenger>(); // All status listeners

	/**
	 * 
	 * @return
	 */
    public static boolean isRunning() {
        return started;
    }
	
	/**
	 * 
	 * @author Maria
	 *
	 */
	class IncomingHandler extends Handler { // Handler of incoming messages from clients.
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_GET_XML:
            	// TODO Send response to msg.replyTo
                break;
            case MSG_MSG:
            	// TODO handle message
                break;
            case MSG_REGISTER_AS_STATUS_LISTENER:
            	System.out.println("SERVICE GOT MESSAGE " + msg.replyTo + " " + t);
            	statusListeners.add(msg.replyTo);
            	break;
            case MSG_UNREGISTER_AS_STATUS_LISTENER:
            	statusListeners.remove(msg.replyTo);
            	break;
            case MSG_STOP: // Not used
            	break;
            case MSG_START: // Not used
            	break;
            case MSG_CANCEL: // Not used
            	cancel();
            	break;
            default:
                super.handleMessage(msg);
            }
        }
    }
	
	/**
	 * 
	 * @param intvaluetosend
	 */
    private void broadcast(int type, String value1, String value2) {
        for (int i = statusListeners.size() - 1; i >= 0; i--) {
            try {
                //Send data as a String
                Bundle b = new Bundle();
                b.putString("str1", value1);
                if (value2 != null) {
                    b.putString("str2", value1);
                }
                Message msg = Message.obtain(null, type);
                msg.setData(b);
                statusListeners.get(i).send(msg);
            } catch (RemoteException e) {
            	statusListeners.remove(i); // TODO Be careful for concurrent modification exception!
            }
        }
    }
	
	/**
	 * Requests wake lock which keeps CPU running even if the device is otherwise sleeping: 
	 * that way data is fetched from OBD and sent to cloud even if the screen is shut.
	 */
	private void requestWakeLock() {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG);
		wakelock.acquire();
	}	

	@Override
	public void onCreate() {
		System.out.println("SERVICE ON CREATE " + started);
	    if (!started) {
			task = null;
			
		    started = true;
	
			// Create notification which tells that this service is running.
			NotificationManager mNotifyManager =
			        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Notification.Builder nBuild = 
			new Notification.Builder(this)
			    .setContentTitle("OBD2 client")
			    .setContentText("Service is running")
			    .setSmallIcon(android.R.drawable.ic_notification_overlay)
			    .setOngoing(true);
		    Notification noti = nBuild.build();
		    int id = 1;
		    String tag = "obd2ServerNotifyer";
		    mNotifyManager.notify(tag, id, noti);
		    // Notification created!

			requestWakeLock();
			cloud = new CloudConnection(this);
			Thread t = new Thread(cloud);
			t.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
			t.start();

			System.out.println("Start task");
			task = new BootStrapperTask(this);
			task.execute();
	    }
	}
	  
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("LocalService", "Received start id " + startId + ": " + intent);
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return messenger.getBinder();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		System.out.println("ON DESTROY");
		NotificationManager mNotifyManager =
		        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		int id = 1;
	    String tag = "obd2ServerNotifyer";
	    mNotifyManager.cancel(tag, id);
		stop();
		CloudService.this.broadcast(CloudService.MSG_PUB_STATUS_TXT, getText(R.string.state_connection_closed).toString(), null);
		CloudService.this.broadcast(CloudService.MSG_PUB_STATUS, ProgramState.IDLE.name(), null);
	    started = false;
	}

	/**
	 * 
	 */
	private void stop() {
		System.out.println("STOP CALLED!");
		if (cloud != null) {
			cloud.stop();
		}
		if (wakelock != null) {
			try {
				wakelock.release();
				wakelock = null;
			} catch (Exception e) {
				System.out.println(e.toString());
				// Do nothing
			}
		}
	}
	
	/**
	 * 
	 */
	private void cancel() {
		if (this.task != null) {
			task.cancel(true);
		}
	}
	
	/**
	 * So, this method takes care of creating stuff.
	 * It is a long class, but inside this class because it makes things easier. Yes, I am lazy in that way.
	 * 
	 * @author Maria
	 *
	 */
	private class BootStrapperTask extends AsyncTask<Void, String, Boolean /* TODO into something more informative */> {
		
		private AsyncTask currentTask;
		private CloudService parent; // Not just activity, because some things have to be passed to this one.
		
		/**
		 * 
		 * @param activity
		 */
		public BootStrapperTask(CloudService activity) {
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
			return true;
		}

		/**
		 * TODO real things
		 * @param scheduler
		 */
		private void createCloudValueProviders(Scheduler scheduler) {
	        CloudValueProvider cvp1 = new CloudValueProvider() {
				@Override
				public long getQueryTickInterval() {
					return 10000;
				}
				@Override
				public long getOutputTickInterval() {
					return 10000;
				}
				@Override
				public void tickQuery() {
					System.out.println("TICK QUERY 1");
				}
				@Override
				public void tickOutput() {
					System.out.println("TICK OUT 1");
				}
	        };

	        CloudValueProvider cvp2 = new CloudValueProvider() {
				@Override
				public long getQueryTickInterval() {
					return 15000;
				}
				@Override
				public long getOutputTickInterval() {
					return 15000;
				}
				@Override
				public void tickQuery() {
					System.out.println("TICK QUERY 1");
				}
				@Override
				public void tickOutput() {
					System.out.println("TICK OUT 1");
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
			System.out.println("Get xml");
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
			System.out.println("Progress update");
			if (text.length < 1) return;
			String newText = text[0];
			CloudService.this.broadcast(CloudService.MSG_PUB_STATUS_TXT, newText, null);
		}

		@Override
		public void onPostExecute(Boolean v) {
			if (v) { // Successful!
				CloudService.this.broadcast(CloudService.MSG_PUB_STATUS_TXT, parent.getText(R.string.state_connection_established).toString(), null);
				CloudService.this.broadcast(CloudService.MSG_PUB_STATUS, ProgramState.STARTED.name(), null);
			} else { // Not so successful
				CloudService.this.broadcast(CloudService.MSG_PUB_STATUS_TXT, parent.getText(R.string.state_connection_failed).toString(), null);
				CloudService.this.broadcast(CloudService.MSG_PUB_STATUS, ProgramState.IDLE.name(), null);
			}
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
	    class CancelBootStrapper extends AsyncTask<Void, Void, Void> {
			@Override
			protected Void doInBackground(Void... params) {
		        if (currentTask != null) {
		        	currentTask.cancel(true);
		        }
				System.out.println("CANCELLING...");
				return null;
			}
			@Override
			protected void onPostExecute(Void v) {
				System.out.println("DONE?");
				CloudService.this.broadcast(MSG_PUB_STATUS, ProgramState.IDLE.name(), null);
				CloudService.this.broadcast(MSG_PUB_STATUS_TXT, CloudService.this.getText(R.string.state_connection_cancelled).toString(), null);
				CloudService.this.stopSelf();
				System.out.println("DONE!");
			}
	    }
	}
}
