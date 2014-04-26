package fi.aalto.cse.msp14.carplatforms.odbvalue;

public final class OBDSourceDefinition {
	public final String name;
	public final int pid;
	public final float limitMin;
	public final float limitMax;
	public final int nBytes;
	
	public OBDSourceDefinition(final String name_, final int pid_, final float limitMin_, final float limitMax_, final int nBytes_) {
		name = name_;
		pid = pid_;
		limitMin = limitMin_;
		limitMax = limitMax_;
		nBytes = nBytes_;
	}
}
