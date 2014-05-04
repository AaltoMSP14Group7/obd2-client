package fi.aalto.cse.msp14.carplatforms.obdfilter;

import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;
import fi.aalto.cse.msp14.carplatforms.obd2_client.CloudValueProvider;
import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDDataSource;
import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDDataSource.IResultListener;
import fi.aalto.cse.msp14.carplatforms.serverconnection.JSONSaveDataMessage;
import fi.aalto.cse.msp14.carplatforms.serverconnection.SaveDataMessage;
import fi.aalto.cse.msp14.carplatforms.serverconnection.ServerConnectionInterface;


public class Filter implements IResultListener, CloudValueProvider {

	private ServerConnectionInterface server;
	private OBDDataSource source;
	private long queryTickInterval;
	private long outputTickInterval;
	private ArrayList<FilterOutput> outputs;

	public Filter(Element e, ServerConnectionInterface server, OBDDataSource source, long queryTickInterval, long outputTickInterval) {
		this.server = server;
		this.queryTickInterval = queryTickInterval;
		this.outputTickInterval = outputTickInterval;
		this.source = source;
	}
	
	public void setOutputs(ArrayList<FilterOutput> outputs) {
		this.outputs = outputs;
	}
	
	public void onQueryResult(float value) {
		for(FilterOutput f : outputs) {
			Long timestamp = System.currentTimeMillis() / 1000L;
			f.getFunction().addSample(value, timestamp);
		}
	}

	public void onQueryFailure() {
		
	}

	@Override
	public long getQueryTickInterval() {
		return this.queryTickInterval;
	}

	@Override
	public long getOutputTickInterval() {
		return this.outputTickInterval;
	}

	@Override
	public void tickQuery() {
		source.update();
	}

	@Override
	public void tickOutput() throws NoValueException {
		for(FilterOutput output: outputs) {
			FilterAggregate result = output.flushResult();
			SaveDataMessage msg = new JSONSaveDataMessage("", null, 0, null, output); //TODO
			server.sendMessage(msg);
		}
	}

}