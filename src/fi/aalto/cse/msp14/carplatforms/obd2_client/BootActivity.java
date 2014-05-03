package fi.aalto.cse.msp14.carplatforms.obd2_client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.widget.*;

/**
 * 
 * @author Maria
 *
 */
public class BootActivity extends Activity {
	private static final String KEY_PB_V = "obd2KeyPBvisibility";
	private static final String KEY_TW_V = "obd2KeyTWvisibility";
	private static final String KEY_TW_TXT = "obd2KeyTWText";
	private static final String KEY_STATE = "obd2KeyState";
	private static final String KEY_SHOULD_BIND = "obd2KeyShouldBind";
	
	private boolean isBound = false;

	private Intent cloud;
	private final Messenger messenger = new Messenger(new IncomingHandler());
	
	private Messenger serviceMessenger;
	private ProgramState progstate;

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
        public void onServiceConnected(ComponentName className, IBinder service) {
			System.out.println("SERVICE CONNECTED");
        	serviceMessenger = new Messenger(service);
            try {
                Message msg = Message.obtain(null, OBD2Service.MSG_REGISTER_AS_STATUS_LISTENER);
                msg.replyTo = messenger;
                serviceMessenger.send(msg);
            } catch (RemoteException e) {
            	System.out.println("Sending message " + e.toString());
            }
        }

		@Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
			System.out.println("SERVICE DISCONNECTED");
			serviceMessenger = null;
        }
    };
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.boot);

		if (savedInstanceState == null) {
			changeButtonAppearance(ProgramState.IDLE);
			progstate = ProgramState.IDLE;
			(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.INVISIBLE);
			(findViewById(R.id.textView3)).setVisibility(ProgressBar.INVISIBLE);
		} else {
			progstate = ProgramState.valueOf(savedInstanceState.getString(KEY_STATE));
			changeButtonAppearance(progstate);
			((TextView)(findViewById(R.id.textView3))).setText(savedInstanceState.getString(KEY_TW_TXT));
			(findViewById(R.id.progressBar1)).setVisibility(savedInstanceState.getInt(KEY_PB_V));
			(findViewById(R.id.textView3)).setVisibility(savedInstanceState.getInt(KEY_TW_V));
			if (savedInstanceState.getBoolean(KEY_SHOULD_BIND)) {
				this.startOwnService();
			}
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle output) {
		output.putInt(KEY_PB_V, (findViewById(R.id.progressBar1)).getVisibility());
		output.putInt(KEY_TW_V, (findViewById(R.id.textView3)).getVisibility());
		output.putString(KEY_TW_TXT, ((TextView)(findViewById(R.id.textView3))).getText().toString());
		output.putString(KEY_STATE, this.progstate.name());
		output.putBoolean(KEY_SHOULD_BIND, this.isBound);
	}
	
	/**
	 * 
	 * @param state
	 * @param text
	 */
	public void changeButtonAppearance(ProgramState state) {
		switch(state) {
			case IDLE:
				progstate = ProgramState.IDLE;
				this.unbind();
                cloud = null;

				((Button)(findViewById(R.id.button1))).setEnabled(true);
				((Button)(findViewById(R.id.button1))).setText(R.string.butt_connect);
				(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.GONE); // Do not show progress bar
	
				(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						progstate = ProgramState.STARTING;
						(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.VISIBLE); // Show progress bar
						(findViewById(R.id.textView3)).setVisibility(ProgressBar.VISIBLE); // Show status
						changeButtonAppearance(ProgramState.STARTING);
						((TextView)(findViewById(R.id.textView3))).setText("Trying to connect");

						startOwnService();
					}
				});
				break;
			case STARTING:
				((Button)(findViewById(R.id.button1))).setEnabled(true);
				((Button)(findViewById(R.id.button1))).setText(R.string.butt_cancel);
				(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						progstate = ProgramState.CANCELLING;
						TextView tw = (TextView) findViewById(R.id.textView3);
						tw.setText(R.string.state_cancelling);
						(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.VISIBLE); // Show progress bar
						(findViewById(R.id.textView3)).setVisibility(ProgressBar.VISIBLE); // Show status
						changeButtonAppearance(ProgramState.CANCELLING);
						
						cancelServiceStart();
					}
				});
				break;
			case STARTED:
				progstate = ProgramState.STARTED;
				((Button)(findViewById(R.id.button1))).setEnabled(true);
				((Button)(findViewById(R.id.button1))).setText(R.string.butt_disconnect);
				TextView tw = (TextView) findViewById(R.id.textView3);
				tw.setText(R.string.state_connection_established);
				(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.GONE); // Show progress bar
	
				(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						progstate = ProgramState.STOPPING;
						TextView tw = (TextView) findViewById(R.id.textView3);
						tw.setText(R.string.state_disconnecting);
						changeButtonAppearance(ProgramState.STOPPING);

						stopService();
					}
				});
				break;
				
			// The next two states mean that the button is disabled!
			case CANCELLING:
				// This 
				(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.VISIBLE); // Show progress bar
				(findViewById(R.id.textView3)).setVisibility(ProgressBar.VISIBLE); // Show status
	
				((Button)(findViewById(R.id.button1))).setEnabled(false);
				(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {/* Do nothing */}
				});
				break;
			case STOPPING:
				// Now the connection is about to be disconnected
				(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.VISIBLE); // Show progress bar
				(findViewById(R.id.textView3)).setVisibility(ProgressBar.VISIBLE); // Show status
				
				((Button)(findViewById(R.id.button1))).setEnabled(false);
				(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {/* Do nothing */}
				});
				break;
			default: return;
		}
	}
	
	/**
	 * Tries to start service.
	 */
	private void startOwnService() {
		if (cloud == null) {
			cloud = new Intent(this, OBD2Service.class);
			new Thread() {
				public void run() {
					startService(cloud);
					bindService(new Intent(BootActivity.this, OBD2Service.class), mConnection, Context.BIND_AUTO_CREATE);
				}
			}.start();
			isBound = true;
		}
	}
	
	/**
	 * 
	 */
	private void cancelServiceStart() {
        if (isBound) {
            if (this.serviceMessenger != null) {
                try {
                    Message msg = Message.obtain(null, OBD2Service.MSG_CANCEL);
                    msg.replyTo = messenger;
                    serviceMessenger.send(msg);
                } catch (RemoteException e) {}
            }
        }
	}

	/**
     * Tries to stop service.
     */
    private void stopService() {
        if (cloud != null) {
        	this.unbindService(mConnection);
        	stopService(cloud);
        	cloud = null;
        	isBound = false;
        }
    }
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbind();
	}

	/**
	 * 
	 */
	private void unbind() {
		System.out.println("UNBIND");
        if (cloud != null && this.isBound) {
        	try {
        		try {
                    Message msg = Message.obtain(null, OBD2Service.MSG_UNREGISTER_AS_STATUS_LISTENER);
                    msg.replyTo = messenger;
                    serviceMessenger.send(msg);
                } catch (RemoteException e) {
                	e.printStackTrace();
                }
            	this.unbindService(mConnection);
        	} catch (Exception e) { // Probably for some reason it has not been bound at the moment
        		e.printStackTrace();
        	}
        }
	}

	/**
	 * 
	 * @author Maria
	 *
	 */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case OBD2Service.MSG_PUB_STATUS:
                String str1 = msg.getData().getString("str1");
                ProgramState stat = ProgramState.valueOf(str1);
                changeButtonAppearance(stat);
                break;
            case OBD2Service.MSG_PUB_STATUS_TXT:
                String txt = msg.getData().getString("str1");
                ((TextView)(findViewById(R.id.textView3))).setText(txt);
            default:
                super.handleMessage(msg);
            }
        }
    }
}
