package soot.jimple.infoflow.test.junit.forward;

import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.Infoflow;

public class OperationSemanticTests extends soot.jimple.infoflow.test.junit.OperationSemanticTests {

	@Override
	protected AbstractInfoflow createInfoflowInstance() {
		return new Infoflow("", false, null);
	}

}
