package soot.jimple.infoflow.test.junit.forward;

import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.Infoflow;

public class SingleJoinPointTests extends soot.jimple.infoflow.test.junit.SingleJoinPointTests {

	@Override
	protected AbstractInfoflow createInfoflowInstance() {
		return new Infoflow("", false, null);
	}

}
