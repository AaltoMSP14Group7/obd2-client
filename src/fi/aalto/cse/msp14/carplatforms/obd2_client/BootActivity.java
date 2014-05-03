package fi.aalto.cse.msp14.carplatforms.obd2_client;

import fi.aalto.cse.msp14.carplatforms.serverconnection.CloudService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
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
	
	private boolean isBound = false;
	private Intent cloud;
	private final Messenger messenger = new Messenger(new IncomingHandler());
	
	private Messenger serviceMessenger;

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
        public void onServiceConnected(ComponentName className, IBinder service) {
        	serviceMessenger = new Messenger(service);
            try {
                Message msg = Message.obtain(null, CloudService.MSG_REGISTER_AS_STATUS_LISTENER);
                msg.replyTo = messenger;
                serviceMessenger.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

		@Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
			serviceMessenger = null;
        }
    };
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.boot);

		if (savedInstanceState == null) {
			(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.INVISIBLE);
			(findViewById(R.id.textView3)).setVisibility(ProgressBar.INVISIBLE);
			changeButtonAppearance(Session.getSession().getState());
			
		} else {
			changeButtonAppearance(Session.getSession().getState());
			((TextView)(findViewById(R.id.textView3))).setText(savedInstanceState.getString(KEY_TW_TXT));
			(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.INVISIBLE);
			(findViewById(R.id.textView3)).setVisibility(ProgressBar.INVISIBLE);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle output) {
		output.putInt(KEY_PB_V, (findViewById(R.id.progressBar1)).getVisibility());
		output.putInt(KEY_TW_V, (findViewById(R.id.textView3)).getVisibility());
		output.putString(KEY_TW_TXT, ((TextView)(findViewById(R.id.textView3))).getText().toString());
	}
	
	/**
	 * 
	 * @param state
	 * @param text
	 */
	public void changeButtonAppearance(ProgramState state) {
		System.out.println("SETTING STATE: " + state.name());
		switch(state) {
			case IDLE:
				((Button)(findViewById(R.id.button1))).setEnabled(true);
				((Button)(findViewById(R.id.button1))).setText(R.string.butt_connect);
				(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.GONE); // Do not show progress bar
	
				(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.VISIBLE); // Show progress bar
						(findViewById(R.id.textView3)).setVisibility(ProgressBar.VISIBLE); // Show status
						changeButtonAppearance(ProgramState.STARTING);
						((TextView)(findViewById(R.id.textView3))).setText("Trying to connect");

						
						//task = new BootStrapperTask(BootActivity.this);
						//task.execute();
						// TODO Send message to service
					}
				});
				break;
			case STARTING:
				((Button)(findViewById(R.id.button1))).setEnabled(true);
				((Button)(findViewById(R.id.button1))).setText(R.string.butt_cancel);
				(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						TextView tw = (TextView) findViewById(R.id.textView3);
						tw.setText(R.string.state_cancelling);
						(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.VISIBLE); // Show progress bar
						(findViewById(R.id.textView3)).setVisibility(ProgressBar.VISIBLE); // Show status
						changeButtonAppearance(ProgramState.CANCELLING);
						
						startOwnService();
						//if (task != null) {
						//	task.cancel(true);
						//	task = null;
						//}
						// TODO cancel
					}

				});
				break;
			case STARTED:
				((Button)(findViewById(R.id.button1))).setEnabled(true);
				((Button)(findViewById(R.id.button1))).setText(R.string.butt_disconnect);
				TextView tw = (TextView) findViewById(R.id.textView3);
				tw.setText(R.string.state_connection_established);
				(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.GONE); // Show progress bar
	
				(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						TextView tw = (TextView) findViewById(R.id.textView3);
						tw.setText(R.string.state_disconnecting);
						changeButtonAppearance(ProgramState.STOPPING);

						stopService();
						//new StopAllTask().execute();
						// TODO Stop
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
			cloud = new Intent(this, CloudService.class);
			startService(cloud);
			bindService(new Intent(this, CloudService.class), mConnection, Context.BIND_AUTO_CREATE);
		}
	}
	
    /**
     * Tries to stop service.
     */
    private void stopService() {
        if (cloud != null) {
        	stopService(cloud);
        	cloud = null;
        }
    }
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
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
            case CloudService.MSG_PUB_STATUS:
                String str1 = msg.getData().getString("state");
                ProgramState stat = ProgramState.valueOf(str1);
                changeButtonAppearance(stat);
                break;
            case CloudService.MSG_PUB_STATUS_TXT:
                String txt = msg.getData().getString("state");
                ((TextView)(findViewById(R.id.textView3))).setText(txt);
            default:
                super.handleMessage(msg);
            }
        }
    }
}
