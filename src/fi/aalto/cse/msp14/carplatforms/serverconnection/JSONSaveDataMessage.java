package fi.aalto.cse.msp14.carplatforms.serverconnection;

import java.util.Map;
import java.util.HashMap;

import org.json.JSONObject;

public class JSONSaveDataMessage implements SaveDataMessage {
	JSONObject json;
	
	public JSONSaveDataMessage(String deviceId, String vin, long timestamp, String type, Object value) {
		Map<String, Object> copyFrom = new HashMap<String, Object>();
		copyFrom.put("deviceId", deviceId);
		copyFrom.put("vin", vin);
		copyFrom.put("timestamp", timestamp);
		copyFrom.put("type", type);
		copyFrom.put("value", value);
		
		json = new JSONObject(copyFrom);
	}
	
	@Override
	public String toMessage() {
		return json.toString();
	}
}
