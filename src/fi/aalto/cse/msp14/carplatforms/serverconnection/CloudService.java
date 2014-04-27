package fi.aalto.cse.msp14.carplatforms.serverconnection;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;

/**
 * 
 * @author Maria
 */
public class CloudService extends Service implements ServerConnectionInterface {

	private static final String LOCK_TAG = "obd2datatocloud";
	private static final String URI = "http://82.130.19.148:8090/test.php";
	
	private PowerManager.WakeLock wakelock;
	
	private boolean keepalive;
	private boolean waitingForConnection;
	
	private LinkedBlockingQueue<SaveDataMessage> messages;
	
	private ConnectionListener conStateBCListener;
	
	private CloudConnection cloud;
	
	private SaveDataMessage current;
	
	private static CloudService instance;
	
	/**
	 * 
	 * @param context
	 */
	public CloudService() {
		current = null;
		keepalive = true;
	}
	
	/**
	 * 
	 * @return
	 * @throws NotCreatedYetException
	 */
	public static CloudService getInstance() throws NotCreatedYetException {
		return instance;
	}
	
	/**
	 * 
	 */
	private void requestWakeLock() {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG);
		wakelock.acquire();
	}	

	@Override
	public void onCreate() {
		instance = this;
		System.out.println("Joojoo");
		messages = new LinkedBlockingQueue<SaveDataMessage>();
	}

	  
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		requestWakeLock();

		cloud = new CloudConnection();
		Thread t = new Thread(cloud);
		t.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
		t.start();

		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		System.out.println("On destroy");
		stop();
	}

	/**
	 * 
	 */
	private void stop() {
		System.out.println("STOP CALLED!");
		keepalive = false;
		synchronized(cloud) {
			cloud.notify();
		}
		
		if (wakelock != null) {
			try {
				wakelock.release(); 
			} catch (Exception e) {
				System.out.println(e.toString());
				// Do nothing
			}
		}
		if (this.conStateBCListener != null) {
			try {
				this.unregisterReceiver(conStateBCListener);
			} catch (Exception e) {
				// It was not registered after all, so, ignore this.
				System.out.println(e.toString());
			}
		}
	}
	
	@Override
	public void sendMessage(SaveDataMessage message) {
		this.messages.offer(message);
		synchronized(cloud) {
			cloud.notify();
		}
	}

	@Override
	public void connectionAvailable() {
		System.out.println("CONNECTION AVAILABLE");
		if (this.waitingForConnection) {
			System.out.println("Connection available");
			this.waitingForConnection = false;
			this.unregisterReceiver(conStateBCListener);
			conStateBCListener = null;
			synchronized(cloud) {
				this.cloud.notify();
			}
		}
	}
	
	/**
	 * Dynamically register connectivity listener.
	 */
	private void waitForConnection() {
		if (!waitingForConnection) {
			System.out.println("Wait for connection");
			this.waitingForConnection = true;
			conStateBCListener = new ConnectionListener(this);
			this.registerReceiver(conStateBCListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		}
	}
	
	/**
	 * This is just a Runnable that takes care of the actual sending.
	 * @author Maria
	 *
	 */
	private final class CloudConnection implements Runnable {
		@Override
		public void run() {
			while (keepalive) {
				if (current == null) {
					current = CloudService.this.messages.poll();
				}
				if (current != null) { // If it is still null, then there is no use to send anything.
					try {
						System.out.println("Send ");
						HttpClient httpclient = new DefaultHttpClient();  
						HttpPost request = new HttpPost(URI);  
						request.setEntity(new StringEntity(current.toString()));
						HttpResponse response = httpclient.execute(request);
						if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
							current = null;
						} else {
							
							// Something happened. What TODO now? 
						}
					} catch (ClientProtocolException e) {
						// Protocol error. What can one do!
					} catch (IOException e) {
						// No connection!
						ConnectivityManager connMgr = (ConnectivityManager) CloudService.this.getSystemService(Context.CONNECTIVITY_SERVICE);
				        android.net.NetworkInfo wifi = connMgr
				                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
				        android.net.NetworkInfo mobile = connMgr
				                .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
			        	System.out.println(!isAvailable(wifi) + " and " + !isAvailable(mobile));
				        if (!isAvailable(wifi) && !isAvailable(mobile)) {
				        	waitForConnection();
				        	System.out.println("Registered!");
				        }
					} catch (Exception e) {
						// Sending failed
						// Other exception
					}
				}

				System.out.println(keepalive + " ja " + CloudService.this.messages.isEmpty() + " ja " + waitingForConnection);
				while(keepalive && (CloudService.this.messages.isEmpty() || waitingForConnection)) {
					System.out.println("Wait");
					synchronized(cloud) {
						try {
							cloud.wait();
							System.out.println("Continue");
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
			System.out.println("THREAD STOPPED");
		}

		/**
		 * 
		 * @param networkInfo
		 * @return
		 */
		private boolean isAvailable(NetworkInfo info) {
			if (info != null && info.isConnected()) {
				return true;
			}
			return false;
		}
	}
}
