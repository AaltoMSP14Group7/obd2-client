package fi.aalto.cse.msp14.carplatforms.serverconnection;

public interface ServerConnectionInterface {
	public void sendMessage(SaveDataMessage messageToSend);
	public void connectionAvailable();
}
