package fi.aalto.cse.msp14.carplatforms.serverconnection;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import fi.aalto.cse.msp14.carplatforms.exceptions.IllegalThreadUseException;
import android.app.Service;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

public class CloudConnection implements Runnable, ServerConnectionInterface {
//	private static final String URI = "http://82.130.19.148:8090/";
//	private static final String URI = "http://10.0.10.11:81/";
//	private static final String POST = "test.php";
//	private static final String XML = "xmlspecs.xml";
	private static final String URI = "http://ec2-54-186-67-231.us-west-2.compute.amazonaws.com:9000/";
	private static final String POST = "addDataPoint";
	private static final String XML = "xmlspecs";

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
	public void run() {
		while (keepalive) {
			try {
				current = messages.take();
	
				if (current != null) { // If it is still null, then there is no use to send anything.
					try {
						System.out.println("Sending actually " + current.toMessage());
						HttpClient httpclient = new DefaultHttpClient();  
						HttpPost request = new HttpPost(URI + POST);
						request.addHeader("Content-Type", "application/json");
						request.addHeader("Connection", "close");
						request.setEntity(new StringEntity(current.toMessage()));
						HttpResponse response = httpclient.execute(request);

						current = null;
						System.out.println("Sending actually DONE " + response.getStatusLine().getStatusCode());
					} catch (ClientProtocolException e) {
						// Protocol error. What can one do!
					} catch (IOException e) {
						// No connection!
						System.out.println("NO CONNECTION");
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
				
				while(keepalive && waitingForConnection) {
					System.out.println("Wait for connection!");
					synchronized(this) {
						try {
							wait();
							System.out.println("Continue after new connection!");
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
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
	public Document getFilters() throws IllegalThreadUseException {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			throw new IllegalThreadUseException("This method must NOT be called from UI thread!");
		}
		// Connect server and fetch XML specs
		HttpClient httpclient = new DefaultHttpClient();  
		HttpGet request = new HttpGet(URI + XML);  
		try {
			HttpResponse response = httpclient.execute(request);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				// Yay! Now we got the wanted XML specs!
				try {
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder = factory.newDocumentBuilder();
					Document doc = builder.parse(response.getEntity().getContent());
					
					
					NodeList list = doc.getElementsByTagName("filter");
					for (int i = 0; i < list.getLength(); i++) {
						System.out.println(list.item(i).getNodeName() + ": " + list.item(i).getAttributes().getNamedItem("source").getNodeValue());
					}

					return doc;
				} catch (ParserConfigurationException e) {
					e.printStackTrace();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				} catch (SAXException e) {
					e.printStackTrace();
				}
			} else {
				// Something happened. Returning null is enough for now. 
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
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
	
	public void stop() {
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
			synchronized(this) {
				notify();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void sendMessage(SaveDataMessage message) {
		System.out.println("Sending message " + message.toMessage());
		this.messages.offer(message);
	}
	
	@Override
	public void connectionAvailable() {
		System.out.println("CONNECTION AVAILABLE");
		if (this.waitingForConnection) {
			System.out.println("Connection available");
			this.waitingForConnection = false;
			parent.unregisterReceiver(conStateBCListener);
			conStateBCListener = null;
			synchronized(this) {
				notify();
			}
		}
	}
}