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
	private Timer timer;
	private boolean running;

	/**
	 * Create a new scheduler. Note that this does not start it yet.
	 */
	public Scheduler() {
		filters = new HashMap<String, ValueProviderTask>();
		running = false;
	}
	
	/**
	 * Using this method one (BootStrapper) can add new CloudValueProviders that should be executed.
	 * One can register filters even if the scheduler is not running yet.
	 * @throws Exception 
	 */
	public void registerFilter(String name, TempCloudValueProviderInterface valueProvider/* TODO params? */) 
			throws ValueProviderAlreadyExistsException {
		// TODO?
		if (filters.containsKey(name)) { // This exists already!
			throw new ValueProviderAlreadyExistsException("Given value provider already exists!");
		}
		ValueProviderTask task = new ValueProviderTask(valueProvider);
		synchronized(filters) {
			filters.put(name, task);
		}
		if (running) {
			timer.schedule(task, valueProvider.getQueryTickInterval(), valueProvider.getQueryTickInterval());
		}
	}

	/**
	 * Unregister the given CloudValueProvider
	 * @param name
	 */
	public void unregisterFilter(String name) {
		// TODO?
		TimerTask task;
		synchronized(filters) {
			task = filters.remove(name);
		}
		if (task == null) return;
		task.cancel();
	}
	
	/**
	 * Unregister all CloudValueProviders.
	 */
	public void unregisterAll() {
		synchronized(filters) {
			TimerTask task;
			for (String key : filters.keySet()) {
				task = filters.remove(key);
				task.cancel();
			}
		}
	}
	
	/**
	 * Pauses the timer temporarily. Though, this in fact cancels whole the timer because of Timer class' available methods.
	 */
	public void pause() {
		if (running) {
			running = false;
			timer.cancel();
		}
	}
	
	/**
	 * This method is actually both start and resume.
	 * Calling it multiple times should cause no problems.
	 */
	public void start() {
		if (!running) {
			timer = new Timer();
			synchronized(filters) {
				ValueProviderTask task;
				for (String key : filters.keySet()) {
					task = filters.get(key);
					timer.schedule(task, task.valueProvider.getQueryTickInterval(), task.valueProvider.getQueryTickInterval());
				}
			}
			running = true;
		}
	}
	
	// TODO results. Or of course they could be sent to cloud service directly, too.
	
	/**
	 * 
	 * @author Maria
	 *
	 */
	private class ValueProviderTask extends TimerTask {

		private TempCloudValueProviderInterface valueProvider;
		
		/**
		 * Constructor.
		 * @param provider
		 */
		ValueProviderTask(TempCloudValueProviderInterface provider) {
			this.valueProvider = provider;
		}
		
		@Override
		public void run() {
			this.valueProvider.tick();
		}
		
		/**
		 * 
		 * @return
		 */
		TempCloudValueProviderInterface getValueProvider() {
			return this.valueProvider;
		}
	}
	
}
