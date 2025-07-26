package droid.hack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.CompleteBlockGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class StmtPoint {
    SootMethod method_location;
    Block block_location;
    Unit instruction_location;
    private final static Logger logger = LoggerFactory.getLogger(StmtPoint.class);

    public StmtPoint(SootMethod method_location, Block block_location, Unit instruction_location) {
        super();
        this.method_location = method_location;
        this.block_location = block_location;
        this.instruction_location = instruction_location;
    }

    public SootMethod getMethod_location() {
        return method_location;
    }

    public void setMethod_location(SootMethod method_location) {
        this.method_location = method_location;
    }

    public Block getBlock_location() {
        return block_location;
    }

    public void setBlock_location(Block block_location) {
        this.block_location = block_location;
    }

    public Unit getInstruction_location() {
        return instruction_location;
    }

    public void setInstruction_location(Unit instruction_location) {
        this.instruction_location = instruction_location;
    }

    public static List<StmtPoint> findCaller(String signature) {

        SootMethod sm = Scene.v().getMethod(signature);
        HashSet<SootMethod> ms = new HashSet<SootMethod>();
        ms.add(sm);
        if (sm.getName().charAt(0) != '<') {
            MethodUtility.findAllPointerOfThisMethod(ms, sm.getSubSignature(), sm.getDeclaringClass());
        }
        List<StmtPoint> sps = new ArrayList<StmtPoint>();
        CallGraphNode node;
        CompleteBlockGraph cbg;
        Block block;
        for (SootMethod tmpm : ms) {
            node = CallGraph.getNode(tmpm.toString());
            if (node == null) continue;
            for (CallGraphNode bn : node.getCallBy()) {
                PatchingChain<Unit> us = bn.getSmthd().retrieveActiveBody().getUnits();
                for (Unit unit : us) {
                    if (unit instanceof Stmt) {
                        if (((Stmt) unit).containsInvokeExpr()) {
                            if (((Stmt) unit).getInvokeExpr().getMethod() == node.getSmthd()) {
                                cbg = BlockGenerator.getInstance().generate(bn.getSmthd().retrieveActiveBody());
                                block = BlockUtility.findLocatedBlock(cbg, unit);
                                sps.add(new StmtPoint(bn.getSmthd(), block, unit));
                                // Logger.print(block.toString());
                                // Logger.print("==================");
                            }
                        }
                    }
                }
            }
        }
        return sps;
    }

    public static List<StmtPoint> findSetter(SootField sootField) {
        List<StmtPoint> sps = new ArrayList<StmtPoint>();

        HashSet<SootMethod> mthdes = CallGraph.getSetter(sootField);
        CompleteBlockGraph cbg;
        Block block;
        if (mthdes != null) {
            for (SootMethod mthd : mthdes) {
                PatchingChain<Unit> us = mthd.retrieveActiveBody().getUnits();
                for (Unit unit : us) {
                    if (unit instanceof Stmt) {
                        for (ValueBox vbox : ((Stmt) unit).getDefBoxes()) {
                            if (vbox.getValue() instanceof FieldRef && ((FieldRef) vbox.getValue()).getField() == sootField) {
                                cbg = BlockGenerator.getInstance().generate(mthd.retrieveActiveBody());
                                block = BlockUtility.findLocatedBlock(cbg, unit);
                                sps.add(new StmtPoint(mthd, block, unit));
                            }
                        }
                    }
                }
            }
        } else {
            logger.info("no Setter " + sootField);
        }

        return sps;
    }

}
