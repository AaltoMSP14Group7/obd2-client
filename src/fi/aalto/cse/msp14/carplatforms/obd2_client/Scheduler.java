package fi.aalto.cse.msp14.carplatforms.obd2_client;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import fi.aalto.cse.msp14.carplatforms.exceptions.ValueProviderAlreadyExistsException;

/**
 * 
 * @author Maria
 *
 */
public class Scheduler {
	
	private final HashMap<String, ValueProviderTask> filters;
	private final HashMap<String, ValueOutputTask> outputs;
	private Timer timer;
	private Timer timerOutput;
	private boolean running;

	/**
	 * Create a new scheduler. Note that this does not start it yet.
	 */
	public Scheduler() {
		filters = new HashMap<String, ValueProviderTask>();
		outputs = new HashMap<String, ValueOutputTask>();
		running = false;
	}
	
	/**
	 * Using this method one (BootStrapper) can add new CloudValueProviders that should be executed.
	 * One can register filters even if the scheduler is not running yet.
	 * @throws Exception 
	 */
	public void registerFilter(String name, CloudValueProvider valueProvider/* TODO params? */) 
			throws ValueProviderAlreadyExistsException {
		// TODO?
		if (filters.containsKey(name)) { // This exists already!
			throw new ValueProviderAlreadyExistsException("Given value provider already exists!");
		}
		ValueProviderTask task = new ValueProviderTask(valueProvider);
		synchronized(filters) {
			filters.put(name, task);
		}
		ValueOutputTask output = new ValueOutputTask(valueProvider);
		synchronized(outputs) {
			outputs.put(name, output);
		}
		if (running) {
			timer.schedule(task, valueProvider.getQueryTickInterval(), valueProvider.getQueryTickInterval());
			timerOutput.schedule(output, valueProvider.getOutputTickInterval(), valueProvider.getOutputTickInterval());
		}
	}

	/**
	 * Unregister the given CloudValueProvider
	 * @param name
	 */
	public void unregisterFilter(String name) {
		// TODO?
		TimerTask task;
		TimerTask output;
		synchronized(filters) {
			task = filters.remove(name);
		}
		synchronized(outputs) {
			output = outputs.remove(name);
		}
		if (task != null) task.cancel();
		if (output != null) output.cancel();
	}
	
	/**
	 * Unregister all CloudValueProviders.
	 */
	public void unregisterAll() {
		try {
			synchronized(filters) {
				TimerTask task;
				for (String key : filters.keySet()) {
					task = filters.remove(key);
					if (running) {
						task.cancel();
					}
				}
			}
			synchronized(outputs) {
				TimerTask task;
				for (String key : outputs.keySet()) {
					task = outputs.remove(key);
					if (running) {
						task.cancel();
					}
				}
			}
		} catch (Exception e) { // TODO
			e.printStackTrace();
		}
	}
	
	/**
	 * Pauses the timer temporarily. Though, this in fact cancels whole the timer because of Timer class' available methods.
	 */
	public void pause() {
		if (running) {
			running = false;
			timer.cancel();
			timerOutput.cancel();
		}
	}
	
	/**
	 * This method is actually both start and resume.
	 * Calling it multiple times should cause no problems.
	 */
	public void start() {
		if (!running) {
			running = true;
			timer = new Timer();
			timerOutput = new Timer();
			
			synchronized(filters) {
				ValueProviderTask task;
				for (String key : filters.keySet()) {
					task = filters.get(key);
					timer.schedule(task, task.valueProvider.getQueryTickInterval(), task.valueProvider.getQueryTickInterval());
				}
			}
			synchronized(outputs) {
				ValueOutputTask task;
				for (String key : outputs.keySet()) {
					task = outputs.get(key);
					timerOutput.schedule(task, task.valueProvider.getOutputTickInterval(), task.valueProvider.getOutputTickInterval());
				}
			}
		}
	}
	
	// TODO results. Or of course they could be sent to cloud service directly, too.
	
	/**
	 * 
	 * @author Maria
	 *
	 */
	private class ValueProviderTask extends TimerTask {

		private CloudValueProvider valueProvider;
		
		/**
		 * Constructor.
		 * @param provider
		 */
		ValueProviderTask(CloudValueProvider provider) {
			this.valueProvider = provider;
		}
		
		@Override
		public void run() {
			this.valueProvider.tickQuery();
		}
		
		/**
		 * 
		 * @return
		 */
		CloudValueProvider getValueProvider() {
			return this.valueProvider;
		}
	}
	/**
	 * 
	 * @author Maria
	 *
	 */
	private class ValueOutputTask extends TimerTask {

		private CloudValueProvider valueProvider;
		
		/**
		 * Constructor.
		 * @param provider
		 */
		ValueOutputTask(CloudValueProvider provider) {
			this.valueProvider = provider;
		}
		
		@Override
		public void run() {
			this.valueProvider.tickOutput();
		}
		
		/**
		 * 
		 * @return
		 */
		CloudValueProvider getValueProvider() {
			return this.valueProvider;
		}
	}
	
}
