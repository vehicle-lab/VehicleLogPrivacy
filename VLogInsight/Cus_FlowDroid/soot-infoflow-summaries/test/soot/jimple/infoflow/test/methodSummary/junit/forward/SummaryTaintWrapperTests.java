package soot.jimple.infoflow.test.methodSummary.junit.forward;

import soot.jimple.infoflow.AbstractInfoflow;
import soot.jimple.infoflow.Infoflow;

public class SummaryTaintWrapperTests extends soot.jimple.infoflow.test.methodSummary.junit.SummaryTaintWrapperTests {
    @Override
    protected AbstractInfoflow createInfoflowInstance() {
        return new Infoflow("", false, null);
    }
}
