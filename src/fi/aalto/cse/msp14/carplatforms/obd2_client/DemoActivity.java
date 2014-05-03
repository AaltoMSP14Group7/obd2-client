package fi.aalto.cse.msp14.carplatforms.obd2_client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import fi.aalto.cse.msp14.carplatforms.obdlink.OBDLinkManager;

public class DemoActivity extends Activity {
	
	private Intent cloud;
	
    class LinkStateListener implements OBDLinkManager.StateEventListener {

        @Override
        public void onStateChange(final OBDLinkManager.LinkState state, final boolean powerState, final String reason) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) findViewById(R.id.txtConnectStatus)).setText("state = " + state.toString() + "\npower state = " + powerState + "\nreason = " + reason);

                    if (state == OBDLinkManager.LinkState.STATE_TURNING_ON)
                        (findViewById(R.id.progressConnection)).setVisibility(ProgressBar.VISIBLE);
                    else
                        (findViewById(R.id.progressConnection)).setVisibility(ProgressBar.INVISIBLE);
                }
            });
        }
    }

    private LinkStateListener m_linkListener;
    static private int m_ongoingDataQueries = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        cloud = new Intent(this, OBD2Service.class);
        startService(cloud);
        
        (findViewById(R.id.progressConnection)).setVisibility(ProgressBar.INVISIBLE);
        (findViewById(R.id.progressQuery)).setVisibility(ProgressBar.INVISIBLE);
        ((TextView)findViewById(R.id.txtConnectStatus)).setText(OBDLinkManager.getInstance(getApplicationContext()).getState().toString());

        (findViewById(R.id.btnConnect)).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                OBDLinkManager.getInstance(getApplicationContext()).startListening();
            }
        });

        (findViewById(R.id.btnDisconnect)).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                OBDLinkManager.getInstance(getApplicationContext()).stopListening();
            }
        });
        
        
        
        
        
        
		/**
		 * 
		 * @author 
		 *
		 */
        class RPMQuery extends OBDLinkManager.OBD2DataQuery {

            RPMQuery() {
                super(0x0C, 2);
            }

            @Override
            public void onResult(byte[] result, long queryDelayNanoSeconds) {
                onAnything();

                setText( Integer.toString((result[0] * 256 + result[1]) / 4) + ", query time = " + (queryDelayNanoSeconds/1000) + "us" );
            }

            @Override
            public void onError(ErrorType type) {
                onAnything();
                setText("Got error = " + type.toString());
            }

            private void onAnything()
            {
                if (--m_ongoingDataQueries == 0)
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            (findViewById(R.id.progressQuery)).setVisibility(ProgressBar.INVISIBLE);
                        }
                    });
            }

            private void setText(final String str) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView)findViewById(R.id.txtQuery)).setText("rpm: " + str);
                    }
                });
            }
        }

        (findViewById(R.id.btnQuery)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (++m_ongoingDataQueries == 1)
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            (findViewById(R.id.progressQuery)).setVisibility(ProgressBar.VISIBLE);
                        }
                    });

                OBDLinkManager.getInstance(getApplicationContext()).submitDataQuery(new RPMQuery());
            }
        });

        m_linkListener = new LinkStateListener();
        OBDLinkManager.getInstance(getApplicationContext()).addEventListener(m_linkListener);
    }

    @Override
    protected void onDestroy() {
        OBDLinkManager.getInstance(getApplicationContext()).removeEventListener(m_linkListener);
        stopService(cloud);
        super.onDestroy();
    }
}
