package soot.jimple.infoflow.test.junit.backward;

import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.BackwardsInfoflow;

public class InFunctionTests extends soot.jimple.infoflow.test.junit.InFunctionTests {

	@Override
	protected AbstractInfoflow createInfoflowInstance() {
		return new BackwardsInfoflow("", false, null);
	}

}
