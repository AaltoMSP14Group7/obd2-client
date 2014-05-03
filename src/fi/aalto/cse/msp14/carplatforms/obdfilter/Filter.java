package fi.aalto.cse.msp14.carplatforms.obdfilter;

import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import fi.aalto.cse.msp14.carplatforms.obd2_client.CloudValueProvider;
import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDDataSource;
import fi.aalto.cse.msp14.carplatforms.odbvalue.OBDDataSource.IResultListener;


public class Filter implements IResultListener, CloudValueProvider {

	private OBDDataSource source;
	private float updateRate;
	private float outputRate;
	private ArrayList<FilterOutput> outputs;

	public Filter(Element e) {
		this.updateRate = Float.valueOf(e.getAttribute("updateRate"));
		String sourceName = e.getAttribute("source");
		//this.source = getSourceByName(sourceName); TODO
		
		NodeList list = e.getChildNodes();
		for(int i=0; i < list.getLength(); i++) {
			Element child = (Element)list.item(i);
			FilterOutput out = new FilterOutput(source, child);
			outputs.add(out);
			this.outputRate = Float.valueOf(e.getAttribute("outputRate"));
		}
	}

	public void tick() {
		source.update();
	}
	
	public void onQueryResult(float value) {
		
		for(FilterOutput f : outputs) {
			Long timestamp = System.currentTimeMillis() / 1000L;
			f.getFunction().addSample(value, timestamp);
		}
	}

	public void onQueryFailure() {
		//tee jotain
	}

	@Override
	public long getQueryTickInterval() {
		return (long)updateRate;
	}

	@Override
	public long getOutputTickInterval() {
		return (long)outputRate;
	}

	@Override
	public void tickQuery() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickOutput() {
		// TODO Auto-generated method stub
		
	}

}