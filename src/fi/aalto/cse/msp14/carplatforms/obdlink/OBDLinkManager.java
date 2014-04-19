package fi.aalto.cse.msp14.carplatforms.obdlink;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fi.aalto.cse.msp14.carplatforms.obdlink.OBDLinkManager.OBD2DataQuery.ErrorType;

public final class OBDLinkManager {
	private static final long			BT_ADAPTER_START_TIMEOUT		= 4000; // ms
	private static final long			BT_DATA_QUERY_TIMEOUT			= 400; // ms
	private static final long			BT_DATA_QUERY_LONG_TIMEOUT		= 5000; // ms
	private static final String			LOG_TAG							= "OBDLinkManager";

	private static OBDLinkManager		s_instance;

	private final BTStateChangeReceiver m_btStateChangeReceiver;
	private final Context				m_applicationContext;
	private final ExecutorService		m_ioExecutor					= Executors.newSingleThreadExecutor();
	private final ExecutorService		m_generalCallbackExecutor		= Executors.newSingleThreadExecutor();
	private final ExecutorService		m_resultCallbackExecutor		= Executors.newSingleThreadExecutor();
	private final Thread				m_serviceThread;
	private final Object				m_stateLock						= new Object();
	private final LinkedBlockingQueue<LinkThreadTask> m_serviceTasks	= new LinkedBlockingQueue<LinkThreadTask>();
	private final LinkedList<StateEventListener> m_listeners			= new LinkedList<StateEventListener>();
	private final OBDCapabilityBitSet	m_obd01CapabilityBitSet			= new OBDCapabilityBitSet();
	private final OBDCapabilityBitSet	m_obd09CapabilityBitSet			= new OBDCapabilityBitSet();

	private BluetoothSocket				m_socket;
	private LinkState					m_state							= LinkState.STATE_OFF;
	private String						m_vehicleID;
	private boolean						m_vehiclePowerState;

	public static enum LinkState {
		STATE_TURNING_ON,
		STATE_ON,
		STATE_TURNING_OFF,
		STATE_OFF,
	}

	private static enum ResponseVerification {
		AllowAnything,
		AllowSubstring,
		AllowMatch
	}

	@SuppressWarnings("serial")
	private static class CommandFailedException extends Exception {
		CommandFailedException(String detailMessage) {
			super(detailMessage);
		}
	}

	@SuppressWarnings("serial")
	private static class CommandNoResultDataException extends CommandFailedException {
		CommandNoResultDataException(String detailMessage) {
			super(detailMessage);
		}
	}

	@SuppressWarnings("serial")
	private static class OBDResponseErrorException extends CommandFailedException {
		OBDResponseErrorException(String detailMessage) {
			super(detailMessage);
		}
	}

	@SuppressWarnings("serial")
	private static final class VehiclePowerStateInterruptException extends Exception {
	}
	
	@SuppressWarnings("serial")
	private static final class TransportFailedException extends Exception {
	}
	
	@SuppressWarnings("serial")
	private static final class VehicleShutdownException extends Exception {
	}
	
	@SuppressWarnings("serial")
	private static final class OBDNegativeResponseException extends Exception {
	}
	
	private static class DataQueryErrorResponse implements Runnable {
		private final OBD2DataQuery m_query;
		private final OBD2DataQuery.ErrorType m_error;

		public DataQueryErrorResponse(final OBD2DataQuery query, final OBD2DataQuery.ErrorType error) {
			m_query = query;
			m_error = error;
		}

		@Override
		public void run() {
			try {
				m_query.onError(m_error);
			} catch (RuntimeException ex) {
				// log and rethrow
				Log.e(LOG_TAG, "Result callback unhandled exception", ex);
				throw ex;
			}
		}
	}
	
	private static class DataQueryResponse implements Runnable {
		private final OBD2DataQuery m_query;
		private final byte[]		m_result;
		private final long 			m_queryDelayNanoSeconds;

		public DataQueryResponse(final OBD2DataQuery query, final byte[] result, final long queryTime) {
			m_query = query;
			m_result = result;
			m_queryDelayNanoSeconds = queryTime;
		}

		@Override
		public void run() {
			try {
				m_query.onResult(m_result, m_queryDelayNanoSeconds);
			} catch (RuntimeException ex) {
				// log and rethrow
				Log.e(LOG_TAG, "Result callback unhandled exception", ex);
				throw ex;
			}
		}
	}

	private static interface LinkThreadTask {
		// nada~
	}

	private static final class DummyThreadTask implements LinkThreadTask {
		// nada~
	}

	/**
	 * After submitting OBD2DataQuery to the OBDLinkManager, OBD2DataQuery object
	 * should not be modified by the caller.
	 */
	public static abstract class OBD2DataQuery implements LinkThreadTask {
		public static enum ErrorType {
			/**
			 * Lost connection to the adapter
			 */
			ERROR_NO_CONNECTION,

			/**
			 * Device reported error, or query is not supported, or query was interrupted
			 * by the vehicle power state change.
			 */
			ERROR_QUERY_ERROR,
		}

		private final int m_pid;
		private final int m_expectedBytes;

		public OBD2DataQuery(final int pid, final int expectedBytes) {
			m_pid = pid;
			m_expectedBytes = expectedBytes;
		}

		public final int getPID() {
			return m_pid;
		}

		public final int getNumExpectedBytes() {
			return m_expectedBytes;
		}

		abstract public void onResult(final byte[] result, final long queryDelayNanoSeconds);

		abstract public void onError(final ErrorType type);
	}

	public static interface StateEventListener {
		abstract void onStateChange(final LinkState linkState, final boolean powerState, final String reason);
	}

	private class BTStateChangeReceiver extends BroadcastReceiver {
		BTStateChangeReceiver() {
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			// inform parent that state changed
			if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
				synchronized (this) {
					this.notifyAll();
				}
			}
		}
	}

	private OBDLinkManager(final Context applicationContext) {
		m_applicationContext = applicationContext;
		m_serviceThread = new Thread(new Runnable() {
			@Override
			public void run() {
				serviceLoop();
			}
		});

		// Register receiver
		{
			IntentFilter filter = new IntentFilter();
			filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

			m_btStateChangeReceiver = new BTStateChangeReceiver();
			m_applicationContext.registerReceiver(m_btStateChangeReceiver, filter);
		}

		// Set thread names for easier debugging

		m_serviceThread.setName("OBDLink-ServiceThread");
		m_serviceThread.start();

		class NameSetterTask implements Runnable {
			private final String m_name;

			public NameSetterTask(String name) {
				m_name = name;
			}

			@Override
			public void run() {
				Thread.currentThread().setName(m_name);
			}
		}

		m_ioExecutor.execute(new NameSetterTask("OBDLink-IOExecutor"));
		m_generalCallbackExecutor.execute(new NameSetterTask("OBDLink-StateCallbackExecutor"));
		m_resultCallbackExecutor.execute(new NameSetterTask("OBDLink-ResultCallbackExecutor"));
	}

	/**
	 * Get singleton
	 */
	public static OBDLinkManager getInstance(final Context applicationContext) {
		if (applicationContext == null)
			throw new IllegalArgumentException("context cannot be null");
		if (s_instance != null && !s_instance.m_applicationContext.equals(applicationContext))
			throw new IllegalArgumentException("context cannot change at runtime");

		if (s_instance == null)
			s_instance = new OBDLinkManager(applicationContext);

		return s_instance;
	}

	/**
	 * Starts OBD2 communication with the device. After a call to this method
	 * the state will be either LinkState.STATE_ON or LinkState.STATE_TURNING_ON
	 */
	public void startListening() {
		Log.i(LOG_TAG, "startListening() called");

		if (Thread.holdsLock(m_stateLock))
			throw new InternalError("startListening() should not be called in a callback");

		synchronized (m_stateLock) {
			// All is good?
			if (m_state == LinkState.STATE_ON || m_state == LinkState.STATE_TURNING_ON)
				return;

			// Send state changed event
			changeState(LinkState.STATE_TURNING_ON, "user action");

			// notify service thread if it is OFF
			m_stateLock.notifyAll();

			// notify service thread if it is ON
			try {
				m_serviceTasks.add(new DummyThreadTask());
			} catch (IllegalStateException ex) {
				// full?
				m_serviceTasks.clear();
				m_serviceTasks.add(new DummyThreadTask());
			}
		}
	}

	/**
	 * Ends OBD2 communication with the device. After a call to this method
	 * the state will be either LinkState.STATE_OFF or LinkState.STATE_TURNING_OFF
	 */
	public void stopListening() {
		Log.i(LOG_TAG, "stopListening() called");

		if (Thread.holdsLock(m_stateLock))
			throw new InternalError("stopListening() should not be called in a callback");

		synchronized (m_stateLock) {
			// All is good?
			if (m_state == LinkState.STATE_OFF || m_state == LinkState.STATE_TURNING_OFF)
				return;

			// Send state changed event
			changeState(LinkState.STATE_TURNING_OFF, "user action");

			// notify service thread if it is OFF
			m_stateLock.notifyAll();

			// notify service thread if it is ON
			try {
				m_serviceTasks.add(new DummyThreadTask());
			} catch (IllegalStateException ex) {
				// full?
				m_serviceTasks.clear();
				m_serviceTasks.add(new DummyThreadTask());
			}
		}
	}

	/**
	 * Returns current state
	 */
	public LinkState getState() {
		return m_state;
	}

	/**
	 * Inserts new state listener
	 */
	public void addEventListener(final StateEventListener listener) {
		m_listeners.add(listener);
	}

	/**
	 * Removes existing state listener or fails silently
	 */
	public void removeEventListener(final StateEventListener listener) {
		m_listeners.remove(listener);
	}

	/**
	 * Submits a new data query. A single response call
	 * to either onError() or onResult() is guaranteed.
	 * <p/>
	 * If state is not STATE_ON, all queries
	 * will be responded with a ERROR_NO_CONNECTION error.
	 * <p/>
	 * OBD2DataQuery callback functions will always be called from another
	 * thread, even if the state is not STATE_ON. This means a callback may
	 * be called during submitDataQuery() function call.
	 * <p/>
	 * Multiple jobs submitted by multiple calls to submitDataQuery
	 * are not required to finish in the call order.
	 */
	public void submitDataQuery(final OBD2DataQuery query) {
		synchronized (m_stateLock) {
			if (m_state != LinkState.STATE_ON)
				m_resultCallbackExecutor.execute(new DataQueryErrorResponse(query, OBD2DataQuery.ErrorType.ERROR_NO_CONNECTION));
			else if (!m_obd01CapabilityBitSet.queryBit(query.getPID()))
				m_resultCallbackExecutor.execute(new DataQueryErrorResponse(query, OBD2DataQuery.ErrorType.ERROR_QUERY_ERROR));
			else {
				try {
					m_serviceTasks.add(query);
				} catch (IllegalStateException ex) {
					// task list is boundless, never happens, ignore
					Log.wtf(LOG_TAG, "Unbounded task list, encountered upper bound");
				}
			}
		}
	}

	private void changeState(final LinkState state, final String reason) {
		class CallbackStateTask implements Runnable {
			private final StateEventListener m_listener;
			private final LinkState m_state;
			private final boolean m_powerState;
			private final String m_reason;

			CallbackStateTask(StateEventListener listener, LinkState state, boolean powerState, String reason) {
				m_listener = listener;
				m_state = state;
				m_powerState = powerState;
				m_reason = reason;
			}

			@Override
			public void run() {
				try {
					m_listener.onStateChange(m_state, m_powerState, m_reason);
				} catch (RuntimeException ex) {
					// log and rethrow
					Log.e(LOG_TAG, "State change listener unhandled exception", ex);
					throw ex;
				}
			}
		}

		synchronized (m_stateLock) {
			m_state = state;

			Log.i(LOG_TAG, "State changed to " + m_state.toString() + ", power state = " + m_vehiclePowerState + ", reason = " + reason);

			// Inform listeners. Callbacks might modify container, lets be extra careful
			@SuppressWarnings("unchecked")
			final LinkedList<StateEventListener> listeners = (LinkedList<StateEventListener>) m_listeners.clone();

			for (StateEventListener listener : listeners)
				m_generalCallbackExecutor.execute(new CallbackStateTask(listener, m_state, m_vehiclePowerState, reason));
		}
	}

	private void serviceLoop() {
		boolean communicating = false;

		mainServiceLoop:
		for (; ; ) {
			// If we are in OFF mode, wait for turn on
			if (!communicating) {
				synchronized (m_stateLock) {
					while (m_state == LinkState.STATE_OFF) {
						try {
							m_stateLock.wait();
						} catch (InterruptedException e) {
							// got interrupted, only happens at shutdown
							break mainServiceLoop;
						}
					}
				}

				// We are not inside monitor, m_state is modified concurrently
				final LinkState cachedState = m_state;

				if (cachedState == LinkState.STATE_TURNING_OFF) {
					// startListening() and stopListening() called in quick succession
					// we don't have to actually do anything, just inform listeners
					changeState(LinkState.STATE_OFF, "user action");
				} else if (cachedState == LinkState.STATE_TURNING_ON) {
					// Try to turn on. This might take a long time so don't call it inside the monitor
					Log.i(LOG_TAG, "Got request to start link, starting link");
					try {
						openOBD2Link();

						// no throw == success
						communicating = true;

						// clear existing commands, there are only DummyTasks to wake up the thread
						m_serviceTasks.clear();

						Log.i(LOG_TAG, "Connection established");

						// inform listeners
						changeState(LinkState.STATE_ON, "user action");
					} catch (RuntimeException ex) {
						Log.i(LOG_TAG, "Failed to establish bt connection, reason = " + ex.getMessage());

						// We have failed, inform listeners
						changeState(LinkState.STATE_OFF, ex.getMessage());
					}
				} else {
					// never happens
					Log.wtf(LOG_TAG, "m_state is not valid for this state");
				}
			}

			// If we are in ON mode, wait for tasks
			if (communicating) {
				final LinkThreadTask task;
				final LinkState cachedState;

				try {
					task = m_serviceTasks.take();
				} catch (InterruptedException e) {
					// got interrupted, only happens at shutdown
					break mainServiceLoop;
				}

				synchronized (m_stateLock) {
					cachedState = m_state;

					if (m_state == LinkState.STATE_TURNING_ON || m_state == LinkState.STATE_TURNING_OFF) {
						// STATE_TURNING_ON:
						//	stopListening() and startListening() called in quick succession
						//	pretend that connection was lost between calls to match expected behavior
						// STATE_TURNING_OFF:
						//	stopListening() called
						//	connection will be killed, kill pending tasks

						if (task instanceof OBD2DataQuery)
							m_resultCallbackExecutor.execute(new DataQueryErrorResponse((OBD2DataQuery) task, OBD2DataQuery.ErrorType.ERROR_NO_CONNECTION));
						while (!m_serviceTasks.isEmpty()) {
							final LinkThreadTask queuedTask = m_serviceTasks.poll();
							if (queuedTask instanceof OBD2DataQuery)
								m_resultCallbackExecutor.execute(new DataQueryErrorResponse((OBD2DataQuery) queuedTask, OBD2DataQuery.ErrorType.ERROR_NO_CONNECTION));
						}
					}
				}

				// Handle task. Tasks might take a long time to finish, don't lock the monitor
				// to prevent functions from blocking

				switch (cachedState) {
					case STATE_TURNING_ON: {
						// nothing to do, all is fine and dandy
						changeState(LinkState.STATE_ON, "user action");
						break;
					}
					case STATE_TURNING_OFF: {
						// Shut down the connection
						killOBD2Link();
						communicating = false;

						changeState(LinkState.STATE_OFF, "user action");
						break;
					}
					case STATE_ON: {
						// Data query
						if (task instanceof OBD2DataQuery) {
							try {
								processQuery((OBD2DataQuery) task);
							} catch (TransportFailedException ex) {
								if (m_socket.isConnected())
								{
									// Adapter is just weird, quick reset
									Log.i(LOG_TAG, "Transport failed, trying to recover");

									try {
										killOBD2Link();
										communicating = false;
										
										// no throw == success
										openOBD2Link();
										communicating = true;
										
										Log.i(LOG_TAG, "Recovery succeeded");
										
										// "change" state to inform listeners about possible vehicle power state
										// change
										changeState(LinkState.STATE_ON, "automatic quick reconnect");
									} catch (RuntimeException innerEx) {
										Log.i(LOG_TAG, "Recovery failed, ex = " + ex.getMessage());
										
										killOBD2Link();
										communicating = false;

										changeState(LinkState.STATE_OFF, "transport failure");
									}
								} else {
									// Connection died
									Log.i(LOG_TAG, "Transport failed, bt connection killed");

									killOBD2Link();
									communicating = false;
									
									changeState(LinkState.STATE_OFF, "transport failure");
								}
							} catch (VehicleShutdownException e) {
								// Vehicle was shut down
								m_vehiclePowerState = false;
								
								// Send power state event
								changeState(LinkState.STATE_ON, "Possible vehicle shutdown");
								
								// TODO: Wakeup beacons
							}
						}
						break;
					}

					default: {
						// never happens
						Log.wtf(LOG_TAG, "m_state is not valid for this state");
						break;
					}
				}
			}
		}

		// Shutdown

		// avoid "weird" state updates like STATE_OFF, ENGINE_ON
		m_vehiclePowerState = false;
		
		// Inform listeners, but don't duplicate messages
		synchronized (m_stateLock) {
			if (m_state != LinkState.STATE_OFF)
				changeState(LinkState.STATE_OFF, "shutdown");
		}

		// Destroy remaining tasks

		while (!m_serviceTasks.isEmpty()) {
			final LinkThreadTask queuedTask = m_serviceTasks.poll();
			if (queuedTask instanceof OBD2DataQuery)
				m_resultCallbackExecutor.execute(new DataQueryErrorResponse((OBD2DataQuery) queuedTask, OBD2DataQuery.ErrorType.ERROR_NO_CONNECTION));
		}

		// Kill connection if we had one
		killOBD2Link();
	}

	private void openOBD2Link() {
		Log.i(LOG_TAG, "openOBD2Link() called()");

		// Get adapter

		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null)
			throw new RuntimeException("Could not access adapter");

		// Turn it on

		if (!turnOnBluetoothAdapter(adapter))
			throw new RuntimeException("Could not turn bluetooth adapter on");

		// Just to make sure...

		if (adapter.getState() != BluetoothAdapter.STATE_ON)
			throw new RuntimeException("Could not enable bluetooth adapter");

		// Find a paired OBD2 device

		BluetoothDevice targetDevice = findTargetDevice(adapter);
		if (targetDevice == null)
			throw new InternalError("findTargetDevice returned null");

		// Connect to device using secure or insecure channel

		BluetoothSocket socket = connectToDevice(adapter, targetDevice);
		if (socket == null || !socket.isConnected())
			throw new RuntimeException("Could not connect to the device");

		try {
			// Handshakes and protocol negotiations
			final int numRecoveryAttempts = 3;

			for (int recoveryAttempt = 0; ; ++recoveryAttempt) {
				try {
					// Start connctiont with a hard reset
					if (recoveryAttempt == 0) {
						tryWriteToSocket(socket, "ATZ\r", BT_DATA_QUERY_LONG_TIMEOUT);
						clearBufferedMessages(socket, true);
					}
					
					if (configureConnection(socket))
						break;

					// Protocol configuration failed, this is sometimes
					// recoverable with reset. Try to recover.
					Log.e(LOG_TAG, "Protocol configuration failed, attempt number " + Integer.toString(recoveryAttempt));

					if (recoveryAttempt >= numRecoveryAttempts) {
						Log.e(LOG_TAG, "Protocol configuration failed, could not recover");
						throw new RuntimeException("Protocol configuration failed");
					}
				} catch (VehiclePowerStateInterruptException ex) {
					// Vehicle power fluctuated during query, this is expected to
					// happen when user enter car, enables the service and turns on
					// the vehicle. Reset and try to recover.
					Log.e(LOG_TAG, "Adapter power state change interrupted handshake, attempt number " + Integer.toString(recoveryAttempt));

					if (recoveryAttempt >= numRecoveryAttempts) {
						Log.e(LOG_TAG, "Adapter power state change interrupted handshake, could not recover");
						throw new RuntimeException("Failed to recover from interrupted handshake");
					}
				}

				// First soft reset 2 times, then hard
				final int recoverRetryDelay;

				if (recoveryAttempt < 2) {
					final int resetTimeout = 1000; // ms
					recoverRetryDelay = 300; // ms

					Log.i(LOG_TAG, "Protocol configuration failed, sending soft reset");
					
					tryWriteToSocket(socket, "ATWS\r", resetTimeout);
					clearBufferedMessages(socket, true);
				} else {
					final int resetTimeout = 2000; // ms
					recoverRetryDelay = 3000; // ms

					Log.i(LOG_TAG, "Protocol configuration failed, sending hard reset");

					tryWriteToSocket(socket, "ATZ\r", resetTimeout);
					clearBufferedMessages(socket, true);
				}

				try {
					Thread.sleep(recoverRetryDelay);
				} catch (InterruptedException e) {
					// ignore
				}
			}
		} catch (RuntimeException ex) {
			// Failed to recover, close socket
			try {
				socket.close();
			} catch (IOException e) {
				// ignore
			}

			throw ex;
		}

		// All is ok, set members
		m_socket = socket;
	}

	private void killOBD2Link() {
		Log.i(LOG_TAG, "killOBD2Link() called()");
		
		m_vehiclePowerState = false;
		
		// Shut down connection, existing jobs are now terminated
		try {
			if (m_socket != null)
				m_socket.close();
		} catch (IOException ex) {
			// suppress
		} finally {
			m_socket = null;
		}
	}

	private boolean turnOnBluetoothAdapter(final BluetoothAdapter adapter) {
		switch (adapter.getState()) {
			case BluetoothAdapter.STATE_ON: {
				// all ok
				break;
			}

			case BluetoothAdapter.STATE_TURNING_OFF:
			case BluetoothAdapter.STATE_OFF: {
				// turn it on

				Log.i(LOG_TAG, "Bluetooth device was not ON, turning it on");

				adapter.enable();

				// FALLTHROUGH
			}

			case BluetoothAdapter.STATE_TURNING_ON: {
				// wait for it to turn on

				Log.i(LOG_TAG, "Waiting for bluetooth device to turn on");

				synchronized (m_btStateChangeReceiver) {
					final long startTime = System.nanoTime();

					while (startTime + BT_ADAPTER_START_TIMEOUT * 1000000 > System.nanoTime()) {
						if (adapter.getState() == BluetoothAdapter.STATE_ON)
							break;

						try {
							m_btStateChangeReceiver.wait(BT_ADAPTER_START_TIMEOUT);
						} catch (InterruptedException ex) {
							// shutdown called
							return false;
						}
					}

					// wait timed out?

					if (adapter.getState() != BluetoothAdapter.STATE_ON) {
						Log.e(LOG_TAG, "Timeout while waitig for bluetooth adapter");
						return false;
					}

					Log.i(LOG_TAG, "Bluetooth device successfully turned on");
				}

				break;
			}

			default:
				Log.wtf(LOG_TAG, "BluetoothAdapter::getState() returned an invalid state");
				return false;
		}

		return true;
	}

	private BluetoothDevice findTargetDevice(final BluetoothAdapter adapter) {
		final Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

		if (pairedDevices == null)
			throw new RuntimeException("Could not query paired (bonded) bt devices");

		Log.i(LOG_TAG, "Finding OBD bluetooth adapter");

		for (BluetoothDevice device : pairedDevices) {
			// Bullet-proof logic...
			String name = device.getName();
			if (name != null && name.equals("OBDII"))
				return device;
		}

		throw new RuntimeException("No suitable bluetooth device found");
	}

	private BluetoothSocket connectToDevice(final BluetoothAdapter adapter, final BluetoothDevice device) {
		abstract class ISocketFactory {
			protected static final String SERIAL_UUID = "00001101-0000-1000-8000-00805F9B34FB";

			public abstract BluetoothSocket createSocket(BluetoothDevice device) throws IOException;

			public abstract String getDescription();
		}

		class SecureSocketFactory extends ISocketFactory {
			@Override
			public BluetoothSocket createSocket(BluetoothDevice device) throws IOException {
				return device.createRfcommSocketToServiceRecord(UUID.fromString(SERIAL_UUID));
			}

			@Override
			public String getDescription() {
				return "SecureSocketFactory";
			}
		}

		class InsecureSocketFactory extends ISocketFactory {
			@Override
			public BluetoothSocket createSocket(BluetoothDevice device) throws IOException {
				return device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(SERIAL_UUID));
			}

			@Override
			public String getDescription() {
				return "InsecureSocketFactory";
			}
		}

		// Android documentation says cancelDiscovery() should be done before connecting so let's do that
		if (!adapter.cancelDiscovery()) {
			// How can cancel even fail?
			Log.i(LOG_TAG, "BluetoothAdapter::cancelDiscovery() failed, ignoring");
		}

		// Try secure first, then insecure

		final ISocketFactory socketFactories[] = new ISocketFactory[]
				{
						new SecureSocketFactory(),
						new InsecureSocketFactory(),
				};

		for (ISocketFactory socketFactory : socketFactories) {
			try {
				BluetoothSocket socket = socketFactory.createSocket(device);

				if (socket == null)
					continue;

				Log.d(LOG_TAG, "Trying connection with a socket, factory = " + socketFactory.getDescription());
				socket.connect();

				if (socket.isConnected()) {
					Log.i(LOG_TAG, "Connection established with a bt socket, factory = " + socketFactory.getDescription());
					return socket;
				}

				// socket connection lost between connect() and isConnceted()
				Log.e(LOG_TAG, "BluetoothSocket::connect(), lost connection immediately after return");
			} catch (IOException e) {
				// suppress
				Log.d(LOG_TAG, "Got exception while connecting to the bluetooth device, supressing, ex = " + e.toString());
			}
		}

		// no matching socket found
		return null;
	}

	private boolean configureConnection(final BluetoothSocket socket) throws VehiclePowerStateInterruptException {
		// Handshake
		if (!protocolHandshake(socket)) {
			Log.e(LOG_TAG, "Protocol handshake failed");
			return false;
		}

		try {
			// Echo OFF, expect anything since Echo might be already on or off
			// and this changes the result (either "AT E0\nOK" or just "OK")
			writeHandshakeAndExpectResponse(socket, "ATE0\r", "OK", ResponseVerification.AllowSubstring);

			// Disable linefeed after each result, we get \r anyway so it doesn't matter anyway
			writeHandshakeAndExpectResponse(socket, "ATL0\r", "OK", ResponseVerification.AllowSubstring);

			// Disable whitespace between result values, we split them ourselves
			// This is a v1.3 command, "?" result is fine too
			writeHandshakeAndExpectResponse(socket, "ATS0\r", null, ResponseVerification.AllowAnything);

			// Allow Long (>7 byte) messages
			writeHandshakeAndExpectResponse(socket, "ATAL\r", "OK", ResponseVerification.AllowMatch);

			// Set default timeout to 25 x 4ms = 100ms.
			writeHandshakeAndExpectResponse(socket, "ATST19\r", "OK", ResponseVerification.AllowMatch);
			
			// Ask device to identify itself
			final String adapterType = queryAdapterString(socket, "ATI\r");
			Log.i(LOG_TAG, "Connected to adapter \"" + adapterType + "\"");
		} catch (CommandFailedException ex) {
			Log.e(LOG_TAG, "Protocol configuration failed, error = " + ex.getMessage());
			return false;
		}
		
		Log.i(LOG_TAG, "Adapter link protocol configured, configuring adapter-vehicle OBD protocol.");
		
		// configure protocol by trying them all in order
		// if all protocols fail, assume that the ECU is offline
		// i.e. the engine power is off
		
		final boolean adapterClaimsVehicleOn;
		
		try {
			adapterClaimsVehicleOn = queryVehiclePowerState(socket);
		} catch (CommandFailedException ex) {
			Log.e(LOG_TAG, "Power query failed, error = " + ex.getMessage());
			return false;
		}
		
		boolean protocolConfigured = false;
		if (adapterClaimsVehicleOn)
		{
			final class ProtocolInfo {
				final public boolean m_auto;
				final public String m_code;
				final public String m_description;
				
				ProtocolInfo(final boolean automatic, final String code, final String description) {
					m_auto = automatic;
					m_code = code;
					m_description = description;
				}
			};
			
			final ProtocolInfo testProtocols[] =
			{
				new ProtocolInfo(true,  "0", "Automatic"),
				new ProtocolInfo(false, "1", "SAE J1850 PWM"),
				new ProtocolInfo(false, "2", "SAE J1850 VPW"),
				new ProtocolInfo(false, "3", "ISO 9141-2"),
				new ProtocolInfo(false, "4", "ISO 14230-4 KWP"),
				new ProtocolInfo(false, "5", "ISO 14230-4 KWP (fast init)"),
				new ProtocolInfo(false, "6", "ISO 15765-4 CAN (11bit, 500kbaud)"),
				new ProtocolInfo(false, "7", "ISO 15765-4 CAN (29bit, 500kbaud)"),
				new ProtocolInfo(false, "8", "ISO 15765-4 CAN (11bit, 250kbaud)"),
				new ProtocolInfo(false, "9", "ISO 15765-4 CAN (29bit, 250kbaud)"),
				new ProtocolInfo(false, "A", "SAE J939 CAN"),
			};
			
			for (final ProtocolInfo protocol : testProtocols)
			{
				Log.i(LOG_TAG, "Trying protocol " + protocol.m_description);
				
				try {
					// Set protocol
					writeHandshakeAndExpectResponse(socket, "ATPC\r", null, ResponseVerification.AllowAnything);
					writeHandshakeAndExpectResponse(socket, "ATSP" + protocol.m_code + "\r", "OK", ResponseVerification.AllowSubstring);
					
					// Just for logs
					writeHandshakeAndExpectResponse(socket, "ATDP\r", null, ResponseVerification.AllowAnything);
				} catch (CommandFailedException ex) {
					Log.i(LOG_TAG, "Adapter protocol set failed,");
					return false;
				}
				
				// Test if protocol works
				
				try {
					// Answer is not interesting, only if the queries succeeded or not.
					// First query might take longer in automatic as the adapter iterates
					// all protocols
					final long firstTimeout = (protocol.m_auto) ? (3*BT_DATA_QUERY_LONG_TIMEOUT) : (BT_DATA_QUERY_LONG_TIMEOUT);
					queryAdapterString(socket, "0100\r", firstTimeout);
					queryAdapterString(socket, "0900\r", BT_DATA_QUERY_LONG_TIMEOUT);
				} catch (CommandFailedException ex) {
					// Protocol failed, clear garbage
					clearBufferedMessages(socket, false);
					Log.i(LOG_TAG, "Protocol failed, trying next");
					continue;
				}
				
				// protocol works, the vehicle is on
				Log.i(LOG_TAG, "Protocol found, using protocol " + protocol.m_description);
				protocolConfigured = true;
				break;
			}
		}

		// Could not find working protocol, assume the vehicle is asleep
		
		Log.i(LOG_TAG, "Protocol configured, requesting implementation information.");
		Log.i(LOG_TAG, "Vehicle power state = " + protocolConfigured);
		
		m_vehiclePowerState = false;
		
		if (protocolConfigured) {
			// Try to activate OBD
			try {
				activateOBD(socket);

				// Note: m_vehiclePowerState is by default false. If activateOBD throws, it will stay false
				m_vehiclePowerState = true;
			} catch (CommandFailedException ex) {
				Log.e(LOG_TAG, "Target vehicle OBD activation error, error = " + ex.getMessage());
				return false;
			}
		}
		
		return true;
	}

	private boolean protocolHandshake(final BluetoothSocket socket) {
		final int handshakeTimout = 1000; // ms
		final int hardResetTimout = 3000; // ms

		Log.i(LOG_TAG, "Protocol handshake, clearing buffers");

		if (!clearBufferedMessages(socket, true))
			return false;

		Log.i(LOG_TAG, "Protocol handshake, poking connection");

		// write AT and wait for prompt
		if (tryWriteToSocket(socket, "AT\r", handshakeTimout) &&
			tryWaitForProtocolPrompt(socket, handshakeTimout) &&
			clearBufferedMessages(socket, true))
			return true;

		Log.i(LOG_TAG, "Protocol handshake, reset(soft) connection");

		// too slow? Reset it softly and wait again
		if (tryWriteToSocket(socket, "ATWS\r", handshakeTimout) &&
			tryWaitForProtocolPrompt(socket, handshakeTimout) &&
			clearBufferedMessages(socket, true))
			return true;

		Log.i(LOG_TAG, "Protocol handshake, reset(hard) connection");
		
		// too slow again? Reset it hard and wait again
		if (tryWriteToSocket(socket, "ATZ\r", handshakeTimout) &&
			tryWaitForProtocolPrompt(socket, hardResetTimout) &&
			clearBufferedMessages(socket, true))
			return true;

		Log.i(LOG_TAG, "Protocol handshake, reset bt connection");
		
		// too slow again? Reset bt and wait again
		try
		{
			socket.close();
			socket.connect();
			if (tryWriteToSocket(socket, "AT\r", handshakeTimout) &&
				tryWaitForProtocolPrompt(socket, hardResetTimout) &&
				clearBufferedMessages(socket, true))
				return true;
		} catch (IOException ex) {
			Log.i(LOG_TAG, "Protocol handshake, socket reset failed", ex);			
		}

		Log.i(LOG_TAG, "Protocol handshake failed");

		return false;
	}

	private boolean tryWaitForProtocolPrompt(final BluetoothSocket socket, final long timeout) {
		// Buffer _might_ contain information (like version) of the
		// device. Emptying it prevents previous data from interfering
		// with the following requests.

		return tryReadUntilPrompt(socket, timeout) != null;
	}

	private boolean clearBufferedMessages(final BluetoothSocket socket, final boolean clearUnprocessedCommands) {
		// Wait 500 ms to allow device to finish any potentially pending operation and
		// then clear all buffered commands. 500 ms should be enough. If it isn't, our
		// protocol setup will fail (probably).

		if (clearUnprocessedCommands) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// ignore
			}
		}

		final StringBuilder buf = new StringBuilder();

		try {
			InputStream istream = socket.getInputStream();
			while (istream.available() > 0)
				buf.append((char)istream.read());

			Log.d(LOG_TAG, "Cleared from buffer: " + buf);
			return true;
		} catch (IOException ex) {
			Log.d(LOG_TAG, "Got IOException while clearing a buffer, cleared thus far: " + buf);
			return false;
		}
	}

	private boolean tryWriteToSocket(final BluetoothSocket socket, final String command, final long timeout) {
		class SocketWriteJob implements Callable<Boolean> {
			private final BluetoothSocket m_socket;
			private final byte[] m_bytes;

			SocketWriteJob(BluetoothSocket socket, byte[] bytes) {
				m_socket = socket;
				m_bytes = bytes;
			}

			@Override
			public Boolean call() {
				try {
					final OutputStream ostream = m_socket.getOutputStream();

					ostream.write(m_bytes);

					return true;
				} catch (IOException ex) {
					// socket died during write
					return false;
				}
			}
		}

		Log.d(LOG_TAG, "Write: " + command);

		final byte[] bytes = command.getBytes();
		final boolean writeSuccessful;
		Future<Boolean> writeFuture = m_ioExecutor.submit(new SocketWriteJob(socket, bytes));

		try {
			writeSuccessful = writeFuture.get(timeout, TimeUnit.MILLISECONDS);
		} catch (TimeoutException ex) {
			// timed out while writing
			Log.d(LOG_TAG, "Timed out while writing, timeout = " + timeout + "ms");
			return false;
		} catch (Exception ex) {
			// unexpected
			Log.wtf(LOG_TAG, "Unexpected exception while getting a Future", ex);
			return false;
		}

		return writeSuccessful;
	}

	private String tryReadUntilPrompt(final BluetoothSocket socket, final long timeout) {
		final StringBuilder resultBuffer = new StringBuilder();
		final InputStream istream;

		try {
			istream = socket.getInputStream();
		} catch (IOException ex) {
			Log.wtf(LOG_TAG, "BluetoothSocket.getInputStream(), undocumented behavior");
			return null;
		}

		// Find prompt char in received buffer

		try {
			while (istream.available() > 0) {
				final int charValue = istream.read();
				if (charValue == '>') {
					Log.d(LOG_TAG, "Read(buf): " + resultBuffer);
					return resultBuffer.toString();
				}

				// getting EOF with available > 0 is weird but check it anyway
				if (charValue == -1)
					return null;

				// a valid char? (ELM327 spec says it might emit zero bytes erroneously, ignore them)
				if (charValue != 0x00)
					resultBuffer.append((char)charValue);
			}
		} catch (IOException ex) {
			// Lost connection while waiting for prompt
			return null;
		}

		// Wait for prompt for timeout milliseconds

		class SocketReadPromptJob implements Callable<Boolean> {
			@Override
			public Boolean call() {
				try {
					for (; ; ) {
						final int charValue = istream.read();

						if (charValue == '>')
							return true;

						// EOF
						if (charValue == -1)
							return false;

						// Valid
						if (charValue != 0x00)
							resultBuffer.append((char) charValue);
					}
				} catch (IOException ex) {
					return false;
				}
			}
		}

		final Future<Boolean> readFuture = m_ioExecutor.submit(new SocketReadPromptJob());
		final boolean promptFoundInFuture;

		try {
			promptFoundInFuture = readFuture.get(timeout, TimeUnit.MILLISECONDS);
		} catch (TimeoutException ex) {
			// timed out while waiting for prompt
			Log.d(LOG_TAG, "Timeout while waiting prompt, timeout = " + timeout + "ms, read thus far = " + resultBuffer);
			return null;
		} catch (Exception ex) {
			// unexpected
			Log.wtf(LOG_TAG, "Unexpected exception while getting a Future", ex);
			return null;
		}

		if (promptFoundInFuture) {
			Log.d(LOG_TAG, "Read(wait): " + resultBuffer);
			return resultBuffer.toString();
		}

		Log.e(LOG_TAG, "Got read error while waiting for prompt");
		return null;
	}

	private void writeHandshakeAndExpectResponse(final BluetoothSocket socket, final String command, final String match, final ResponseVerification matchCompare) throws CommandFailedException, VehiclePowerStateInterruptException {
		// Query
		final String response = queryAdapterString(socket, command);

		// Validate response

		switch (matchCompare) {
			case AllowAnything:
				return;

			case AllowSubstring:
				if (!response.contains(match))
					throw new CommandFailedException("Failed to execute " + command + ": response \"" + response + "\" does not contain \"" + match + "\".");
				return;

			case AllowMatch:
				if (!response.equals(match))
					throw new CommandFailedException("Failed to execute " + command + ": got response value \"" + response + "\", expected \"" + match + "\".");
				return;

			default:
				Log.wtf(LOG_TAG, "Invalid matchCompare value");
				return;
		}
	}

	private String queryAdapterString(final BluetoothSocket socket, final String command) throws CommandFailedException, VehiclePowerStateInterruptException {
		return queryAdapterString(socket, command, BT_DATA_QUERY_TIMEOUT);
	}

	private String queryAdapterString(final BluetoothSocket socket, final String command, final long timeout) throws CommandFailedException, VehiclePowerStateInterruptException {
		// Write command
		if (!tryWriteToSocket(socket, command, timeout))
			throw new CommandFailedException("Failed to execute " + command + ": write failed.");

		// Read to result until prompt
		final String response = tryReadUntilPrompt(socket, timeout);
		if (response == null)
			throw new CommandFailedException("Failed to execute " + command + ": read failed.");

		// Error
		if (response.contains("ERROR") || response.contains("UNABLE TO CONNECT"))
			throw new CommandFailedException("Failed to execute " + command + ": adapter error.");
		
		// No data?
		if (response.contains("NO DATA"))
			throw new CommandNoResultDataException("Failed to execute " + command + ": vehicle returned no data.");

		// Changes to the pin 15 interrupted operation (Vehicle turned on/off)
		if (response.contains("STOPPED"))
			throw new VehiclePowerStateInterruptException();

		// We may have trailing line feeds, remove them
		return response.trim();
	}

	/**
	 * Does the adapter claim the vehicle is on, WILL PRODUCE
	 * FALSE-POSITIVES! Use checkVehiclePowerState to find out
	 * the real state
	 */
	private boolean queryVehiclePowerState(final BluetoothSocket socket) throws CommandFailedException {
		final int numAttempts = 3;
		for (int attempt = 0; attempt < numAttempts; ++attempt) {
			try {
				final String powerState = queryAdapterString(socket, "ATIGN\r");

				if (powerState.equals("ON"))
					return true;
				else if (powerState.equals("OFF"))
					return false;
				else
					throw new CommandFailedException("Sent ATIGN, expected ON or OFF, got \"" + powerState + "\"");
			} catch (VehiclePowerStateInterruptException ex) {
				clearBufferedMessages(socket, false);
				Log.i(LOG_TAG, "Adapter power state change interrupted power state query, attempt number " + Integer.toString(attempt));
			}
		}
		
		throw new CommandFailedException("Too many power state changes during power state query");
	}
	
	/**
	 * Is the vehicle online. NOTE! Requires configured protocol
	 */
	private boolean checkVehiclePowerState(final BluetoothSocket socket) throws CommandFailedException {
		
		// When adapter says NO, it means NO
		if (queryVehiclePowerState(socket) == false)
			return false;
		
		// When adapter says Yes, it means Maybe
		// => Workaround for lying adapters

		final int numAttempts = 3;
		for (int attempt = 0; attempt < numAttempts; ++attempt) {
			// Adapters sometimes lie, test query supported pids (this might be the first query, increse timeout")
			try {
				queryAdapterString(socket, "0100\r", BT_DATA_QUERY_LONG_TIMEOUT);
				return true;
			} catch (CommandFailedException ex) {
				// IGN is ON, but ECU is not on
				Log.i(LOG_TAG, "Adapter claimed power is on, but queries still fail. Assuming no power. ex = " + ex.getMessage());
				return false;
			} catch (VehiclePowerStateInterruptException ex) {
				clearBufferedMessages(socket, false);
				Log.i(LOG_TAG, "Adapter power state change interrupted power state verification, attempt number " + Integer.toString(attempt));
			}
		}
		
		throw new CommandFailedException("Too many power state changes during power state query");
	}

	private void warmupOBDQuery(final BluetoothSocket socket, final String command) throws CommandFailedException, VehiclePowerStateInterruptException {
		// First queries may fail, just ignore
		Log.i(LOG_TAG, "Warming up query, type = " + command);
		try {
			clearBufferedMessages(socket, false);
			queryAdapterString(socket, command, BT_DATA_QUERY_LONG_TIMEOUT);
			clearBufferedMessages(socket, true);
		} catch (CommandNoResultDataException ex) {
			// all ok
			Log.i(LOG_TAG, "Ignoring errors no data");
		} catch (CommandFailedException ex) {
			// log error, rethrow
			Log.i(LOG_TAG, "Got error during warmup, ex = " + ex.getMessage());
			throw ex;
		}
	}

	private byte[] queryOBDData(final BluetoothSocket socket, final String command) throws CommandFailedException, VehiclePowerStateInterruptException, OBDNegativeResponseException {
		return queryOBDData(socket, command, BT_DATA_QUERY_TIMEOUT);
	}

	private byte[] queryOBDData(final BluetoothSocket socket, final String command, final long timeout) throws CommandFailedException, VehiclePowerStateInterruptException, OBDNegativeResponseException {
		// Validate data before sending
		final byte[] commandData = OBDHexStringToData(command);
		if (commandData == null || commandData.length == 0)
			throw new CommandFailedException("Command is of invalid form.");

		return queryOBDData(socket, commandData, timeout);
	}

	private byte[] queryOBDData(final BluetoothSocket socket, final byte[] commandData, final long timeout) throws CommandFailedException, VehiclePowerStateInterruptException, OBDNegativeResponseException {
		final String response = queryAdapterString(socket, OBDDataToOBDCommandString(commandData), timeout);

		// Data is hexadecimal?
		final byte[] responseData = OBDHexStringToData(response);
		if (responseData == null || responseData.length == 0)
			throw new CommandFailedException("Failed to execute " + OBDDataToString(commandData) + ": result data is not valid hex string. Response " + response);

		return getOBDResponseData(commandData, responseData);
	}

	private byte[] OBDHexStringToData(final String hexString) {
		final ArrayList<Byte> buf = new ArrayList<Byte>();
		int ptr = 0;

		// whitespace at start
		for (; ptr < hexString.length() && Character.isWhitespace(hexString.charAt(ptr)); ++ptr) {
			// nothing
		}
		if (ptr >= hexString.length())
			return null;

		for (; ; ) {
			final char firstChar;
			final char secondChar;

			// first char
			if ((hexString.charAt(ptr) >= 'A' && hexString.charAt(ptr) <= 'F') ||
				(hexString.charAt(ptr) >= '0' && hexString.charAt(ptr) <= '9'))
				firstChar = hexString.charAt(ptr++);
			else
				return null;

			// whitespace between chars
			for (; ptr < hexString.length() && Character.isWhitespace(hexString.charAt(ptr)); ++ptr) {
				// nothing
			}
			if (ptr >= hexString.length())
				return null;

			// second char
			if ((hexString.charAt(ptr) >= 'A' && hexString.charAt(ptr) <= 'F') ||
				(hexString.charAt(ptr) >= '0' && hexString.charAt(ptr) <= '9'))
				secondChar = hexString.charAt(ptr++);
			else
				return null;

			buf.add((byte) Integer.parseInt((new String() + firstChar) + secondChar, 16));

			// that was last byte?
			for (; ptr < hexString.length() && Character.isWhitespace(hexString.charAt(ptr)); ++ptr) {
				// nothing
			}
			if (ptr >= hexString.length())
				break;
		}

		// to byte array
		byte[] retVal = new byte[buf.size()];
		for (int ndx = 0; ndx < buf.size(); ++ndx)
			retVal[ndx] = buf.get(ndx);

		return retVal;
	}

	private String OBDDataToString(final byte[] data) {
		StringBuilder buf = new StringBuilder();

		if (data.length >= 1)
			buf.append(String.format("%02X", data[0]));

		for (int i = 1; i < data.length; ++i)
			buf.append(String.format(" %02X", data[i]));

		return buf.toString();
	}

	private String OBDDataToOBDCommandString(final byte[] data) {
		StringBuilder buf = new StringBuilder();

		if (data.length >= 1)
			buf.append(String.format("%02X", data[0]));

		for (int i = 1; i < data.length; ++i)
			buf.append(String.format("%02X", data[i]));

		buf.append('\r');
		return buf.toString();
	}

	private byte[] getOBDResponseData(final byte[] commandData, final byte[] responseData) throws CommandFailedException, OBDNegativeResponseException {
		// Data is a response to our query?

		final int queryHeaderLength;

		// Magical 7fXXYY response = negative response
		if (responseData.length == 3 && responseData[0] == 0x7f)
		{
			if (responseData[1] == commandData[0])
				throw new OBDNegativeResponseException();
			
			throw new OBDResponseErrorException("Got illegal negative response for a query. Response " + OBDDataToString(responseData));
		}
		
		// Assume normal response
		if (commandData[0] == 0x01 && commandData.length == 2) {
			// Validate
			if (responseData[0] != 0x41 || commandData[1] != responseData[1])
				throw new OBDResponseErrorException("Got illegal response for a query. Response " + OBDDataToString(responseData));

			// Remove query header
			queryHeaderLength = 2;
		} else if (commandData[0] == 0x02 && commandData.length == 2) {
			// Validate
			if (responseData[0] != 0x42 || commandData[1] != responseData[1])
				throw new OBDResponseErrorException("Got illegal response for a query. Response " + OBDDataToString(responseData));

			// Remove query header
			queryHeaderLength = 2;
		} else if (commandData[0] == 0x03) {
			// Validate
			if (responseData[0] != 0x43)
				throw new OBDResponseErrorException("Got illegal response for a query. Response " + OBDDataToString(responseData));

			// Remove query header
			queryHeaderLength = 1;
		} else if (commandData[0] == 0x05 && commandData.length == 3) {
			// Validate
			if (responseData[0] != 0x43 || commandData[1] != responseData[1] || commandData[2] != responseData[2])
				throw new OBDResponseErrorException("Got illegal response for a query. Response " + OBDDataToString(responseData));

			// Remove query header
			queryHeaderLength = 3;
		} else if (commandData[0] == 0x09 && commandData.length == 2) {
			// Validate
			if (responseData[0] != 0x49 || commandData[1] != responseData[1])
				throw new OBDResponseErrorException("Got illegal response for a query. Response " + OBDDataToString(responseData));

			// Remove query header
			queryHeaderLength = 2;
		} else {
			throw new CommandFailedException("Illegal query.");
		}

		// Remove header
		final byte[] returnData = new byte[responseData.length - queryHeaderLength];
		System.arraycopy(responseData, queryHeaderLength, returnData, 0, returnData.length);
		return returnData;
	}

	private void warmupOBD(final BluetoothSocket socket) throws CommandFailedException, VehiclePowerStateInterruptException {
		Log.i(LOG_TAG, "Warming up vehicle OBD");

		try {
			// First queries always contain "SEARCHING...", and it takes a long time.
			// just query something and don't worry about results. Warmup all read pids

			warmupOBDQuery(socket, "0100\r");
			warmupOBDQuery(socket, "0900\r");
		} catch (CommandFailedException ex) {
			Log.e(LOG_TAG, "Target vehicle OBD warmup failed, error = " + ex.getMessage());
			throw ex;
		}
	}
	
	private byte[] queryOBDCapabilityData(final BluetoothSocket socket, final String command) throws CommandFailedException, VehiclePowerStateInterruptException {
		final byte[] fakeResult = { 0x00, 0x00, 0x00, 0x00 };
		
		try {
			return queryOBDData(socket, command);
		} catch (OBDNegativeResponseException ex) {
			// Magic
			Log.e(LOG_TAG, "Target vehicle capability query failed, got negative response.");
			return fakeResult;
		} catch (OBDResponseErrorException ex) {
			// Capability query failed, fake it
			Log.e(LOG_TAG, "Target vehicle capability query failed, ignoring. Ex = " + ex.getMessage());
			return fakeResult;
		}
	}

	private void activateOBD(final BluetoothSocket socket) throws CommandFailedException, VehiclePowerStateInterruptException {
		// Vehicle seems to be ON, try query data
		Log.i(LOG_TAG, "Checking vehicle data");

		try {
			// Query supported 01-type IDs
			final byte[] support01Bits[] = {
					queryOBDCapabilityData(socket, "0100\r"),
					queryOBDCapabilityData(socket, "0120\r"),
					queryOBDCapabilityData(socket, "0140\r"),
					queryOBDCapabilityData(socket, "0160\r"),
					queryOBDCapabilityData(socket, "0180\r"),
					queryOBDCapabilityData(socket, "01A0\r"),
					queryOBDCapabilityData(socket, "01C0\r"),
			};

			// Query supported 09-type IDs
			final byte[] support09Bits[] = {
					queryOBDCapabilityData(socket, "0900\r"),
			};

			try {
				m_obd01CapabilityBitSet.setValue(support01Bits);
				m_obd09CapabilityBitSet.setValue(support09Bits);
			} catch (OBDCapabilityBitSet.InvalidBitSetException ex) {
				Log.e(LOG_TAG, "Could not apply capability bits, error = " + ex.getMessage());
				throw new CommandFailedException("Could not apply capability bits, got error = " + ex.getMessage());
			}

			// Read VehicleID
			try {
				m_vehicleID = convertOBDDataToString(queryOBDData(socket, "0902\r"));
			} catch (OBDNegativeResponseException ex) {
				Log.e(LOG_TAG, "Could not query VIN.");
				m_vehicleID = "unknown";
			}

			Log.i(LOG_TAG, "Implementation information query complete.");
			Log.i(LOG_TAG, "Connected to vehicle ID \"" + m_vehicleID + "\"");
			Log.i(LOG_TAG, "Number of supported 01 PIDs = \"" + m_obd01CapabilityBitSet.getNumBitsSet() + "\"");
			Log.i(LOG_TAG, "Number of supported 09 PIDs = \"" + m_obd09CapabilityBitSet.getNumBitsSet() + "\"");
		} catch (CommandFailedException ex) {
			Log.e(LOG_TAG, "Target vehicle power state query failed, error = " + ex.getMessage());
			throw ex;
		}
	}
	
	private String convertOBDDataToString(final byte[] data) {
		final StringBuilder buf = new StringBuilder();
		for (int ndx = 0; ndx < data.length; ++ndx)
			buf.append((char) data[ndx]);
		return buf.toString();
	}

	private void processQuery(final OBD2DataQuery query) throws TransportFailedException, VehicleShutdownException {
		final String commandString = String.format("01%02X\r", query.getPID());
		final byte[] commandData = new byte[] { 0x01, (byte) query.getPID() };
		final long queryStartTime = System.nanoTime();
		
		try
		{
			// Write to the socket
			if (!tryWriteToSocket(m_socket, commandString, BT_DATA_QUERY_TIMEOUT))
				throw new TransportFailedException();
			
			// Read to result until prompt
			final String response = tryReadUntilPrompt(m_socket, BT_DATA_QUERY_TIMEOUT);
			if (response == null)
				throw new TransportFailedException();

			final long queryTime = System.nanoTime() - queryStartTime;
			
			// Magic values
			
			// No data?
			if (response.contains("NO DATA"))
				throw new CommandNoResultDataException("Failed to execute " + commandString + ": vehicle returned no data.");
	
			// Changes to the pin 15 interrupted operation (Vehicle turned on/off)
			if (response.contains("STOPPED"))
				throw new VehiclePowerStateInterruptException();

			// Data is hexadecimal?
			final byte[] responseData = OBDHexStringToData(response);
			if (responseData == null || responseData.length == 0)
				throw new CommandFailedException("Failed to execute " + commandString + ": result data is not valid hex string. Response " + response);

			// Data is a response?
			final byte[] obdData = getOBDResponseData(commandData, responseData);
			
			// wrong number of bytes
			if (obdData.length != query.getNumExpectedBytes()) {
				Log.i(LOG_TAG, "OBD data query returned unexpected number of bytes for query pid " + query.getPID() + "Expected " + query.getNumExpectedBytes() + ", got" + obdData.length);
				throw new CommandFailedException("Got invalid number of response bytes");
			}
			
			m_resultCallbackExecutor.execute(new DataQueryResponse(query, obdData, queryTime));
		} catch (TransportFailedException ex) {
			// Failure means the connection was lost
			m_resultCallbackExecutor.execute(new DataQueryErrorResponse(query, ErrorType.ERROR_NO_CONNECTION));
			throw ex;
		} catch (CommandNoResultDataException ex) {
			// no data
			Log.i(LOG_TAG, "OBD data query returned NO DATA, verifying vehicle power state");
			m_resultCallbackExecutor.execute(new DataQueryErrorResponse(query, ErrorType.ERROR_QUERY_ERROR));
			
			// this might be caused by poweroff
			verifyVehiclePowerOn();
		} catch (OBDNegativeResponseException ex) {
			// no data
			Log.i(LOG_TAG, "OBD data query returned negative response");
			m_resultCallbackExecutor.execute(new DataQueryErrorResponse(query, ErrorType.ERROR_QUERY_ERROR));
		} catch (VehiclePowerStateInterruptException ex) {
			// interrupted, check if power state is changed?
			Log.i(LOG_TAG, "OBD data query was interrupted by a power state change, querying new power state");
			m_resultCallbackExecutor.execute(new DataQueryErrorResponse(query, ErrorType.ERROR_QUERY_ERROR));
			
			// Test power state
			verifyVehiclePowerOn();
		}
		catch (CommandFailedException ex) {
			// something went wrong. Check connection
			m_resultCallbackExecutor.execute(new DataQueryErrorResponse(query, ErrorType.ERROR_QUERY_ERROR));
			
			Log.i(LOG_TAG, "OBD data query failed, unknown reason, ex = " + ex.getMessage());
			
			if (!m_socket.isConnected()) {
				Log.i(LOG_TAG, "Connection was lost");
				throw new TransportFailedException();
			}
		}
	}
	
	private void verifyVehiclePowerOn() throws VehicleShutdownException, TransportFailedException {
		try
		{
			final boolean newPowerState = checkVehiclePowerState(m_socket);
			Log.i(LOG_TAG, "Vehicle new power state = " + newPowerState);
			
			// power was on when this function was called
			if (newPowerState == false)
				throw new VehicleShutdownException();
			
			// all ok, just something weird going on
		} catch (CommandFailedException innerEx) {
			Log.i(LOG_TAG, "Power state query failed, ex = " + innerEx.getMessage());
			
			// data is broken lost connection
			throw new TransportFailedException();
		}
	}
}
