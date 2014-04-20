package fi.aalto.cse.msp14.carplatforms.serverconnection;

public interface ServerConnectionInterface {
	public void sendMessage(String type/* This should be changed into some enum? */, 
			Object o /* And maybe this should be changed into some interface? */);
	public void connectionAvailable();
}
