package fi.aalto.cse.msp14.carplatforms.location;

import java.util.Date;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import fi.aalto.cse.msp14.carplatforms.exceptions.IllegalThreadUseException;
import fi.aalto.cse.msp14.carplatforms.obd2_client.CloudValueProvider;
import fi.aalto.cse.msp14.carplatforms.serverconnection.JSONSaveDataMessage;
import fi.aalto.cse.msp14.carplatforms.serverconnection.SaveDataMessage;
import fi.aalto.cse.msp14.carplatforms.serverconnection.ServerConnectionInterface;

public class DeviceLocationDataSource implements CloudValueProvider {
	private ServerConnectionInterface server;
	private long queryTickInterval;
	private long outputTickInterval;
	private Location location;
	private LocationListener locationListener;
	private LocationManager lm;
	private String deviceID;
	private String vin;
	
	/**
	 * 
	 * @param applicationContext
	 * @param server
	 * @param queryTickInterval
	 * @param outputTickInterval
	 * @param deviceID
	 * @param vin
	 */
	public DeviceLocationDataSource(Context applicationContext, ServerConnectionInterface server, 
			long queryTickInterval, long outputTickInterval, String deviceID, String vin) {
		if(applicationContext == null) {
			throw new IllegalArgumentException("Application context can't be null");
		}
		if(server == null) {
			throw new IllegalArgumentException("Server can't be null");
		}
		if(queryTickInterval < 1000 || outputTickInterval < 1000) {
			throw new IllegalArgumentException("The query and output intervals need to be at least 1000 ms");
		}
		
		this.server = server;
		this.queryTickInterval = queryTickInterval;
		this.outputTickInterval = outputTickInterval;
		this.deviceID = deviceID;
		this.vin = vin;
	}

	/**
	 * 
	 * 
	 * @throws IllegalThreadUseException 
	 */
	public void registerForLocationUpdates(Context applicationContext) throws IllegalThreadUseException {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			throw new IllegalThreadUseException("This method can only be called from UI thread!");
		}
		lm = (LocationManager) applicationContext.getSystemService(Context.LOCATION_SERVICE);
		locationListener = new LocationUpdater();
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
	}
	
	/**
	 * Basically, stop this data source.
	 */
	public void unregisterLocationUpdates() {
		if (locationListener != null && lm != null) {
			try {
				lm.removeUpdates(locationListener);
				lm = null;
				locationListener = null;
			} catch(Exception e) {
				e.printStackTrace();
			}		
		}
	}

	@Override
	public void tickQuery() {
		// Not needed; updated by the location updater as often as possible
	}

	@Override
	public void tickOutput() {
		if (location != null) {
			double[] coords = { location.getLatitude(), location.getLongitude() };
			long timestamp = new Date().getTime() / 1000;
			SaveDataMessage message = new JSONSaveDataMessage(deviceID, vin, timestamp, "location", coords);
			server.sendMessage(message);
		}
	}
	
	@Override
	public long getQueryTickInterval() {
		return this.queryTickInterval;
	}

	@Override
	public long getOutputTickInterval() {
		return this.outputTickInterval;
	}
	
	private class LocationUpdater implements LocationListener {

		@Override
		public void onLocationChanged(Location location) {
			DeviceLocationDataSource.this.location = location;
			Log.v("DeviceLocationDataSource", location.toString());
		}

		@Override
		public void onProviderDisabled(String provider) { }

		@Override
		public void onProviderEnabled(String provider) { }

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) { }
		
	}

}
