package fi.aalto.cse.msp14.carplatforms.serverconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * 
 * @author Maria
 *
 */
public class ConnectionListener extends BroadcastReceiver {

	private ServerConnectionInterface connection;
	
	/**
	 * 
	 * @param conn
	 */
	public ConnectionListener(ServerConnectionInterface conn) {
		this.connection = conn;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		final ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        final android.net.NetworkInfo wifi = connMgr
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        final android.net.NetworkInfo mobile = connMgr
                .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (isAvailable(wifi) || isAvailable(mobile)) {
    		connection.connectionAvailable();
        }
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
