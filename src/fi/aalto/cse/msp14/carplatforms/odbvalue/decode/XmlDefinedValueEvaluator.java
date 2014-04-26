package fi.aalto.cse.msp14.carplatforms.odbvalue.decode;

import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class XmlDefinedValueEvaluator implements IValueEvaluator {
	
	/**
	 * @return new list containing child 'Element' nodes
	 */
	protected ArrayList<Element> getChildElements (final Element e) {
		final ArrayList<Element> 	returnArray	= new ArrayList<Element>();
		final NodeList 				children 	= e.getChildNodes();
		
		for (int ndx = 0; ndx < children.getLength(); ++ndx)
			if (children.item(ndx).getNodeType() == Node.ELEMENT_NODE)
				returnArray.add((Element)children.item(ndx));
		
		return returnArray;
	}
}
