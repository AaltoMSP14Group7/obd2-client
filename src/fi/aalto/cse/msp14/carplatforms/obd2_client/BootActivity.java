package fi.aalto.cse.msp14.carplatforms.obd2_client;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class BootActivity extends Activity {
	
	private BootStrapper task;
	private Session session = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.boot);

		(findViewById(R.id.progressBar1)).setVisibility(ProgressBar.INVISIBLE);
		(findViewById(R.id.textView3)).setVisibility(ProgressBar.INVISIBLE);

		changeButtonAppearance(ProgramState.IDLE);
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
					task = new BootStrapper(BootActivity.this);
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
					// TODO Stop
					// How to stop?
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
			
			((Button)(findViewById(R.id.button1))).setText(R.string.butt_disconnect);
			((Button)(findViewById(R.id.button1))).setEnabled(false);
			(findViewById(R.id.button1)).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {/* Do nothing */}
			});
			break;
			default: return;
		}
	}
	
	@Override
	protected void onDestroy() {
		// TODO Do not cancel everything?
		super.onDestroy();
	}
}
