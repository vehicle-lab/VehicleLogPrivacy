package soot.jimple.infoflow.test.junit.forward;

import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.Infoflow;

public class ReflectionTests extends soot.jimple.infoflow.test.junit.ReflectionTests {

	@Override
	protected AbstractInfoflow createInfoflowInstance() {
		return new Infoflow("", false, null);
	}

}
