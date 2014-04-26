package fi.aalto.cse.msp14.carplatforms.odbvalue;

import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import fi.aalto.cse.msp14.carplatforms.obdlink.OBDLinkManager;
import fi.aalto.cse.msp14.carplatforms.obdlink.OBDLinkManager.OBD2DataQuery;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.IValueEvaluator;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.IValueEvaluator.DecodeFailure;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.IValueEvaluator.EvaluationContext;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.ValueEvaluatorFactory;
import fi.aalto.cse.msp14.carplatforms.odbvalue.decode.IValueEvaluator.Type;

public class OBDDataSource {
	private final OBDSourceDefinition 			m_definition;
	private final IValueEvaluator 				m_decoder;
	private final OBDLinkManager				m_linkManager;
	private final Query 						m_query;
	private final ArrayList<IResultListener>	m_listeners;
	
	private class Query extends OBD2DataQuery {
		public boolean m_active;
		
		public Query(int pid, int expectedBytes) {
			super(pid, expectedBytes);
			m_active = false;
		}

		@Override
		public void onResult(byte[] result, long queryDelayNanoSeconds) {
			m_active = false;
			
			final EvaluationContext context = new EvaluationContext(result);
			final float decodedValue;
			
			try {
				if (m_decoder.getReturnType() == Type.FLOAT)
					decodedValue = m_decoder.evaluateFloat(context);
				else
					decodedValue = (float)m_decoder.evaluateInteger(context);
			} catch (DecodeFailure ex) {
				emitQueryFailure();
				return;
			}
			
			emitQueryResult(decodedValue);
		}

		@Override
		public void onError(ErrorType type) {
			m_active = false;
			
			emitQueryFailure();
		}
		
		private void emitQueryResult(float result) {
			synchronized (m_listeners) {
				for (IResultListener listener : m_listeners)
					listener.onQueryResult(result);
			}
		}
		
		private void emitQueryFailure() {
			synchronized (m_listeners) {
				for (IResultListener listener : m_listeners)
					listener.onQueryFailure();
			}
		}
	}
	
	public static interface IResultListener {
		public abstract void onQueryResult(float value);
		public abstract void onQueryFailure();
	}
	
	public OBDDataSource (Element e, OBDLinkManager linkManager) throws ParseError {
		m_definition = parseSourceDefinition(e);
		m_decoder = ValueEvaluatorFactory.parse(getDecoderElement(e), m_definition);
		m_linkManager = linkManager;
		m_query = new Query(m_definition.pid, m_definition.nBytes);
		m_listeners = new ArrayList<IResultListener>();
	}
	
	/**
	 * Sends query to the OBD
	 */
	public synchronized void update () {
		// Only one active query. This effectively
		// allows filters to "spam" update and still
		// have the link to serve data in round-robin
		// order.
		if (m_query.m_active)
			 return;
		
		m_query.m_active = true;
		m_linkManager.submitDataQuery(m_query);
	}
	
	/**
	 * Adds result listener
	 */
	public void addListener(IResultListener listener) {
		synchronized (m_listeners) {
			m_listeners.add(listener);
		}
	}
	
	static private OBDSourceDefinition parseSourceDefinition(Element e) throws ParseError {
		final String name;
		final int pid;
		final int nBytes;
		final float min;
		final float max;
		
		if (e.getAttributeNode("name") == null)
			throw new ParseError(e.getTagName() + ": missing 'name' attribute");
		if (e.getAttributeNode("pid") == null)
			throw new ParseError(e.getTagName() + ": missing 'pid' attribute");
		if (e.getAttributeNode("nBytes") == null)
			throw new ParseError(e.getTagName() + ": missing 'nBytes' attribute");
		if (e.getAttributeNode("min") == null)
			throw new ParseError(e.getTagName() + ": missing 'min' attribute");
		if (e.getAttributeNode("max") == null)
			throw new ParseError(e.getTagName() + ": missing 'max' attribute");
		
		name = e.getAttributeNode("name").getNodeValue();
		if (name.isEmpty())
			throw new ParseError(e.getTagName() + ": attribute 'name' cannot be empty");
		
		pid = parseToInt(e.getAttributeNode("pid").getNodeValue(), "pid");
		if (pid < 0 || pid > 255)
			throw new ParseError(e.getTagName() + ": attribute 'pid' valid value range is [0, 255]");

		nBytes = parseToInt(e.getAttributeNode("nBytes").getNodeValue(), "nBytes");
		if (nBytes < 1)
			throw new ParseError(e.getTagName() + ": attribute 'nBytes' valid value range is [1, inf)");
		
		min = parseToFloat(e.getAttributeNode("min").getNodeValue(), "min");
		max = parseToFloat(e.getAttributeNode("max").getNodeValue(), "max");
		if (min > max)
			throw new ParseError(e.getTagName() + ": attributes 'min' and 'max' must define a valid range, got min > max.");
		
		return new OBDSourceDefinition(name, pid, min, max, nBytes);
	}
	
	static private int parseToInt(final String value, final String attrName) throws ParseError {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			throw new ParseError("attribute '" + attrName + "', value '" + value + "' is not a valid integer");
		}
	}
	
	static private float parseToFloat(final String value, final String attrName) throws ParseError {
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException ex) {
			throw new ParseError("attribute '" + attrName + "', value '" + value + "' is not a valid float");
		}
	}
	
	static private Element getDecoderElement (Element sourceElement) throws ParseError {
		final ArrayList<Element> sourceChildren = getChildElements(sourceElement);
		if (sourceChildren.size() != 1)
			throw new ParseError(sourceElement.getTagName() + ": expected 1 child, got " + sourceChildren.size());
		if ("decode".equals(sourceChildren.get(0).getTagName()))
			throw new ParseError(sourceElement.getTagName() + ": could not find 'decode' element");
		
		final ArrayList<Element> decodeChildren = getChildElements(sourceChildren.get(0));
		if (decodeChildren.size() != 1)
			throw new ParseError(sourceElement.getTagName() + ".decode: expected 1 child, got " + decodeChildren.size());
		
		return decodeChildren.get(0);
	}
	
	static protected ArrayList<Element> getChildElements (final Element e) {
		final ArrayList<Element> 	returnArray	= new ArrayList<Element>();
		final NodeList 				children 	= e.getChildNodes();
		
		for (int ndx = 0; ndx < children.getLength(); ++ndx)
			if (children.item(ndx).getNodeType() == Node.ELEMENT_NODE)
				returnArray.add((Element)children.item(ndx));
		
		return returnArray;
	}
}
