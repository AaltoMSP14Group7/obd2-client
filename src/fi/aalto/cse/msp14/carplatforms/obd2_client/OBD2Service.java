package fi.aalto.cse.msp14.carplatforms.obd2_client;

import java.util.ArrayList;

import org.w3c.dom.NodeList;

import fi.aalto.cse.msp14.carplatforms.exceptions.IllegalThreadUseException;
import fi.aalto.cse.msp14.carplatforms.location.DeviceLocationDataSource;
import fi.aalto.cse.msp14.carplatforms.obd2_client.R;
import fi.aalto.cse.msp14.carplatforms.serverconnection.CloudConnection;
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

/**
 * 
 * @author Maria
 */
public class OBD2Service extends Service {
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

	private static final int TAG_ID = 1;
	private static final String TAG_TAG = "obd2ServerNotifyer";

	private PowerManager.WakeLock wakelock;

	private String deviceID;
	private String vin;

	/*
	 * Different components
	 */
	private CloudConnection cloud;
	private Scheduler scheduler;
	private BootStrapperTask task;
	private DeviceLocationDataSource locationData;
	private TempBluetoothConnectionManager btConManager;

	private String prevState = null;
	private String prevTxt = null;

	private static boolean started = false;

	// Messaging related stuff
	final Messenger messenger = new Messenger(new IncomingHandler());
	ArrayList<Messenger> statusListeners = new ArrayList<Messenger>(); 
		// All status listeners

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
	class IncomingHandler extends Handler { // Handler of incoming messages from
											// clients.
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_GET_XML:
				// Not needed?
				break;
			case MSG_MSG:
				// Not needed?
				break;
			case MSG_REGISTER_AS_STATUS_LISTENER:
				statusListeners.add(msg.replyTo);
				if (prevState != null) {
					try {
						Bundle b = new Bundle();
						b.putString("str1", prevState);
						Message msg1 = Message.obtain(null, MSG_PUB_STATUS);
						msg1.setData(b);
						msg.replyTo.send(msg1);
					} catch (Exception e) {
						e.printStackTrace();
					} // Didn't succeed. Shame.
				}
				if (prevTxt != null) {
					try {
						Bundle b2 = new Bundle();
						b2.putString("str1", prevTxt);
						Message msg2 = Message.obtain(null, MSG_PUB_STATUS_TXT);
						msg2.setData(b2);
						msg.replyTo.send(msg2);
					} catch (Exception e) {
						e.printStackTrace();
					} // Didn't succeed. Shame.
				}
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
				// Send data as a String
				Bundle b = new Bundle();
				b.putString("str1", value1);
				if (value2 != null) {
					b.putString("str2", value1);
				}
				Message msg = Message.obtain(null, type);
				msg.setData(b);
				statusListeners.get(i).send(msg);
			} catch (RemoteException e) {
				System.out.println("EXCEPTION broadcasting: " + e.toString());
				statusListeners.remove(i); // TODO Be careful for concurrent
											// modification exception!
			}
		}
		if (type == MSG_PUB_STATUS_TXT) {
			prevTxt = value1;
		} else if (type == MSG_PUB_STATUS) {
			prevState = value1;
		}
	}

	/**
	 * Requests wake lock which keeps CPU running even if the device is
	 * otherwise sleeping: that way data is fetched from OBD and sent to cloud
	 * even if the screen is shut.
	 */
	private void requestWakeLock() {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG);
		wakelock.acquire();
	}

	@Override
	public void onCreate() {
		task = null;

		// Create notification which tells that this service is running.
		NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder nBuild = new Notification.Builder(this)
				.setContentTitle("OBD2 client")
				.setContentText("Service is running")
				.setSmallIcon(android.R.drawable.ic_notification_overlay)
				.setOngoing(true);
		Notification noti = nBuild.build();
		mNotifyManager.notify(TAG_TAG, TAG_ID, noti);
		// Notification created!

		requestWakeLock();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (!started) {
			started = true;
			cloud = new CloudConnection(this);
			Thread t = new Thread(cloud);
			t.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
			t.start();

			task = new BootStrapperTask(this);
			task.execute();
		}
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return messenger.getBinder();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotifyManager.cancel(TAG_TAG, TAG_ID);
		stop();
		OBD2Service.this.broadcast(OBD2Service.MSG_PUB_STATUS_TXT,
				getText(R.string.state_connection_closed).toString(), null);
		OBD2Service.this.broadcast(OBD2Service.MSG_PUB_STATUS,
				ProgramState.IDLE.name(), null);
		started = false;
	}

	/**
	 * Any possible exception should not cause that some components are not
	 * stopped. So, catch for ANY even slightly possible exceptions!
	 */
	private void stop() {
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
		if (scheduler != null) {
			try {
				scheduler.pause();
				scheduler = null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (this.btConManager != null) {
			try {
				this.btConManager.disconnect();
				this.btConManager = null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (this.locationData != null) {
			try {
				this.locationData.unregisterLocationUpdates();
			} catch (Exception e) {
				e.printStackTrace();
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

	//-------------------------------------------------------------------------
	// Other classes
	//-------------------------------------------------------------------------

	/**
	 * So, this method takes care of creating stuff. It is a long class, but
	 * inside this class because it makes things easier. Yes, I am lazy in that
	 * way.
	 * 
	 * @author Maria
	 * 
	 */
	private class BootStrapperTask extends AsyncTask<Void, String, Boolean> {

		private AsyncTask currentTask;
		private OBD2Service parent; // Not just activity, because some things
									// have to be passed to this one.

		private String error;
		
		/**
		 * 
		 * @param activity
		 */
		public BootStrapperTask(OBD2Service activity) {
			assert (activity != null);
			parent = activity;
			error = "";
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			boolean ret;
			// TODO bluetooth
			publishProgress(parent.getText(R.string.progress_bluetooth)
					.toString());
			ret = connectBluetooth();
//			if (this.isCancelled() || !ret) {
//				if (!ret) error = parent.getText(R.string.progress_err_no_bluetooth).toString();
//				else error = parent.getText(R.string.progress_err_cancelled).toString();
//				return false;
//			}
			// TODO get vin
			vin = "nicestring";
			
			publishProgress(parent.getText(R.string.progress_content).toString());
			scheduler = new Scheduler();
			scheduler.start();

			deviceID = createID();
			if (this.isCancelled()) {
				error = parent.getText(R.string.progress_err_cancelled).toString();
				return false;
			}

			// TODO Now, fetch from cloud that what to get
			NodeList list = fetchXMLSpecs();
			if (this.isCancelled()){
				error = parent.getText(R.string.progress_err_cancelled).toString();
				return false;
			} else if (list == null) {
				error = parent.getText(R.string.progress_err_no_filters).toString();
				return false;
			}
			
			// TODO Create OBD sources
			publishProgress(parent.getText(R.string.progress_starting).toString());
			createCloudValueProviders(scheduler); // TODO Create CloudValueProviders.

			if (this.isCancelled()) {
				error = parent.getText(R.string.progress_err_cancelled).toString();
				return false;
			}

			publishProgress("Connection done!");
			return true;
		}

		/**
		 * TODO real things
		 * 
		 * @param scheduler
		 */
		private void createCloudValueProviders(Scheduler scheduler) {
			locationData = new DeviceLocationDataSource(
					parent.getApplicationContext(), cloud, 10000, 10000);

			if (this.isCancelled())
				return;
			try {
				scheduler.registerFilter(locationData.getClass().getName(),locationData);
				scheduler.registerOutput(locationData.getClass().getName(),locationData);
			} catch (Exception e) {
			}
		}

		/**
		 * 
		 * @return
		 */
		private NodeList fetchXMLSpecs() {
			try {
				NodeList filterNodelist = cloud.getFilters();
				return filterNodelist;
			} catch (IllegalThreadUseException e) {
				e.printStackTrace();
			}
			return null;
		}

		/**
		 * TODO Bluetooth connection.
		 * 
		 * @return
		 */
		private boolean connectBluetooth() {
			btConManager = new TempBluetoothConnectionManager(getApplicationContext());
			boolean result = btConManager.connect();
			return result;
		}


		

		@Override
		public void onProgressUpdate(String... text) {
			// System.out.println("Progress update");
			if (text.length < 1)
				return;
			String newText = text[0];
			OBD2Service.this.broadcast(OBD2Service.MSG_PUB_STATUS_TXT, newText,
					null);
		}

		@Override
		public void onPostExecute(Boolean v) {
			if (v) { // Successful!
				try {
					locationData.registerForLocationUpdates(parent
							.getApplicationContext());
				} catch (IllegalThreadUseException e) {/* Should be no problem */
				}

				OBD2Service.this.broadcast(OBD2Service.MSG_PUB_STATUS_TXT,
						parent.getText(R.string.state_connection_established)
								.toString(), null);
				OBD2Service.this.broadcast(OBD2Service.MSG_PUB_STATUS,
						ProgramState.STARTED.name(), null);
			} else { // Not so successful
				OBD2Service.this.broadcast(OBD2Service.MSG_PUB_STATUS_TXT,
						error, null);
				OBD2Service.this.broadcast(OBD2Service.MSG_PUB_STATUS,
						ProgramState.IDLE.name(), null);
				OBD2Service.this.stopSelf();
			}
		}

		/**
		 * Creates some sort of hopefully unique ID for this device.
		 * 
		 * @return
		 */
		private String createID() {
			TelephonyManager tm = (TelephonyManager) parent
					.getSystemService(Context.TELEPHONY_SERVICE);
			String tohash = tm.getDeviceId();
			return tohash;
		}

		@Override
		protected void onCancelled(Boolean v) {
			// System.out.println("CANCEL!");
			new CancelBootStrapper().execute();
		}

		/*
		 * ----------------------------------------------------------------------- 
		 * Another class
		 * -----------------------------------------------------------------------
		 */

		/**
		 * This class only takes care of canceling everything what was done
		 * before. This is needed if in the middle of initialization (before the
		 * task is finished) the user decides to cancel whole the connection
		 * thing.
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
				return null;
			}

			@Override
			protected void onPostExecute(Void v) {
				OBD2Service.this.broadcast(MSG_PUB_STATUS,
						ProgramState.IDLE.name(), null);
				OBD2Service.this.broadcast(MSG_PUB_STATUS_TXT, OBD2Service.this
						.getText(R.string.state_connection_cancelled)
						.toString(), null);
				OBD2Service.this.stopSelf();
			}
		}
	}
}
