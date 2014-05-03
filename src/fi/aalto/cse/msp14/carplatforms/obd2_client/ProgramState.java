package fi.aalto.cse.msp14.carplatforms.obd2_client;


/**
 * Enum to represent whole the program's state.
 * Normal case:
 * IDLE -> STARTING -> STARTED -> STOPPING -> IDLE
 * Special case when user starts the process, but decides to cancel it then:
 * IDLE -> STARTING -> CANCELLING -> IDLE
 * 
 * @author Maria
 *
 */
public enum ProgramState {
	IDLE,		// Currently not doing anything but waiting user's input
	STARTING,	// User has pressed Start
	STARTED,	// All initializing has been done
	CANCELLING,	// Starting has been cancelled
	STOPPING,	// Service was running, but the user has called stop
}
