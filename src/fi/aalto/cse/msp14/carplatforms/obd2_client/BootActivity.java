package fi.aalto.cse.msp14.carplatforms.obd2_client;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
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
	
	private BootStrapperTask task;

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
						task = new BootStrapperTask(BootActivity.this);
						task.execute();
						// TODO on click
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
						if (task != null) {
							task.cancel(true);
							task = null;
						}
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
						new StopAllTask().execute();
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
	 * This method should take care that all possible connections are closed and
	 * whatever there is to close or stop, is closed or stopped.
	 * TODO Cancel all
	 */
	public void stopAll() {
		if (Session.getSession().isActive()) { // There is something to be cancelled
			Session session = Session.getSession();
			stopService(session.getCloud());
			session.getScheduler().pause();
			session.getScheduler().unregisterAll();
		}
	}
	
	@Override
	protected void onDestroy() {
		stopAll();
		super.onDestroy();
	}
	
	
	/**
	 * 
	 * @author Maria
	 *
	 */
	private class StopAllTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			stopAll();
			return null;
		}

		@Override
		public void onPostExecute(Void v) {
			changeButtonAppearance(ProgramState.IDLE);
			TextView tw = (TextView) findViewById(R.id.textView3);
			tw.setText(R.string.state_connection_closed);
		}
	}
}
