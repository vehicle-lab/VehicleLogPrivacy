package soot.jimple.infoflow.test.junit.forward;

import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.Infoflow;

public class OverwriteTests extends soot.jimple.infoflow.test.junit.OverwriteTests {

	@Override
	protected AbstractInfoflow createInfoflowInstance() {
		return new Infoflow("", false, null);
	}

}
