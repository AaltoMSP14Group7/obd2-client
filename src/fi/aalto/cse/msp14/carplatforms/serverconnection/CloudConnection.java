package fi.aalto.cse.msp14.carplatforms.serverconnection;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import fi.aalto.cse.msp14.carplatforms.exceptions.IllegalThreadUseException;
import android.app.Service;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

public class CloudConnection implements Runnable, ServerConnectionInterface {
//	private static final String URI = "http://82.130.19.148:8090/test.php";
	private static final String URI = "http://10.0.10.11:8090/test.php";
	private static final String URI_SERVER = "http://ec2-54-186-67-231.us-west-2.compute.amazonaws.com:9000/addDataPoint";

	private boolean keepalive;
	private LinkedBlockingQueue<SaveDataMessage> messages;
	private SaveDataMessage current;
	private boolean waitingForConnection;

	private ConnectionListener conStateBCListener;

	private Service parent;
	
	/**
	 * 
	 */
	public CloudConnection(Service parent) {
		messages = new LinkedBlockingQueue<SaveDataMessage>();
		keepalive = true;
		this.parent = parent;
	}
	
	@Override
	public synchronized void run() {
		while (keepalive) {
			if (current == null) {
				current = messages.poll();
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
					ConnectivityManager connMgr = (ConnectivityManager) parent.getSystemService(Context.CONNECTIVITY_SERVICE);
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
			while(keepalive && (messages.isEmpty() || waitingForConnection)) {
				System.out.println("Wait");
				try {
					wait();
					System.out.println("Continue");
				} catch (InterruptedException e) {
					e.printStackTrace();
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
	

	/**
	 * Note! This method MUST be called from a separate thread!
	 * Otherwise it might block if network is slow or not available.
	 * @throws IllegalThreadUseException If this method is called for any reason from main thread.
	 */
	public void getXML() throws IllegalThreadUseException {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			throw new IllegalThreadUseException("This method must NOT be called from UI thread!");
		}
		// Connect server and fetch XML specs
		HttpClient httpclient = new DefaultHttpClient();  
		HttpGet request = new HttpGet(URI + "");  
		try {
			HttpResponse response = httpclient.execute(request);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				// Yay! Now we got the wanted XML specs!
				// So. What exactly TODO now?
			} else {
				// Something happened. What TODO now? 
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
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
			parent.registerReceiver(conStateBCListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		}
	}
	
	public synchronized void stop() {
		try {
			if (this.conStateBCListener != null) {
				try {
					parent.unregisterReceiver(conStateBCListener);
				} catch (Exception e) {
					// It was not registered after all, so, ignore this.
					System.out.println(e.toString());
				}
			}
			keepalive = false;
			notify();
		} catch (Exception e) {
			// TODO ?
			e.printStackTrace();
		}
	}
	
	@Override
	public synchronized void sendMessage(SaveDataMessage message) {
		this.messages.offer(message);
		notify();
	}
	
	@Override
	public synchronized void connectionAvailable() {
		System.out.println("CONNECTION AVAILABLE");
		if (this.waitingForConnection) {
			System.out.println("Connection available");
			this.waitingForConnection = false;
			parent.unregisterReceiver(conStateBCListener);
			conStateBCListener = null;
			notify();
		}
	}
}