package fi.aalto.cse.msp14.carplatforms.obdfilter;

import java.util.ArrayList;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import fi.aalto.cse.msp14.carplatforms.exceptions.NoValueException;
import fi.aalto.cse.msp14.carplatforms.obd2_client.CloudValueProvider;
import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDDataSource;
import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDDataSource.IResultListener;
import fi.aalto.cse.msp14.carplatforms.serverconnection.JSONSaveDataMessage;
import fi.aalto.cse.msp14.carplatforms.serverconnection.SaveDataMessage;
import fi.aalto.cse.msp14.carplatforms.serverconnection.ServerConnectionInterface;


public class Filter implements IResultListener, CloudValueProvider {

	private final ServerConnectionInterface server;
	private final OBDDataSource source;
	private final long queryTickInterval;
	private final long outputTickInterval;
	private final ArrayList<FilterOutput> outputs;
	private String device;
	private String vin;

	public Filter(Element e, ServerConnectionInterface server, Map<String, OBDDataSource> sources,
			String deviceID, String vin) throws FilterParseError {
		this.server = server;
		this.device = deviceID;
		this.vin = vin;
		
		// @updateRate
		if (e.getAttributeNode("updateRate") == null)
			throw new FilterParseError("filter, missing updateRate attribute");

		try {
			queryTickInterval = Integer.parseInt(e.getAttributeNode("updateRate").getNodeValue());
		} catch (NumberFormatException ex) {
			throw new FilterParseError("filter @updateRate, @updateRate was not integer");
		}

		// @outputRate
		if (e.getAttributeNode("outputRate") == null)
			throw new FilterParseError("filter, missing outputRate attribute");

		try {
			outputTickInterval = Integer.parseInt(e.getAttributeNode("outputRate").getNodeValue());
		} catch (NumberFormatException ex) {
			throw new FilterParseError("filter @outputRate, @outputRate was not integer");
		}
		
		// @source
		if (e.getAttributeNode("source") == null)
			throw new FilterParseError("filter, missing source attribute");
		
		source = sources.get(e.getAttributeNode("source").getNodeValue());
		if (source == null)
			throw new FilterParseError("filter @source, no such data source");
		
		// child elements
		outputs = new ArrayList<>();
		for (int ndx = 0; ndx < e.getChildNodes().getLength(); ++ndx) {
			if (e.getChildNodes().item(ndx).getNodeType() == Node.ELEMENT_NODE)
			{
				Element childElement = (Element)e.getChildNodes().item(ndx);
				
				if (!"output".equals(childElement.getTagName()))
					throw new FilterParseError("filter, got unexpected child, expected 'output'");
				
				outputs.add(new FilterOutput(childElement));
			}
		}
		
		// listen to results
		source.addListener(this);
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
			SaveDataMessage msg = new JSONSaveDataMessage(device, 
					                                      vin, 
					                                      result.getTimestamp(), 
					                                      output.getName(), 
					                                      result.getValue()); 
			server.sendMessage(msg);
		}
	}

}