package droid.hack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.IntConstant;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.toolkits.graph.Block;

import java.util.*;

public class ValuePoint implements IDGNode {
    private static final Logger logger = LoggerFactory.getLogger(ValuePoint.class);

    DGraph dg;

    SootMethod method_location;
    Block block_location;
    Unit instruction_location;
    HashSet<Integer> target_regs = new HashSet<Integer>();
    List<BackwardContext> bcs = null;
    HashSet<BackwardContext> solvedBCs = new HashSet<BackwardContext>();

    public String getAppendix() {
        return appendix;
    }

    String appendix = "";

    ArrayList<HashMap<Integer, HashSet<String>>> result = new ArrayList<HashMap<Integer, HashSet<String>>>();

    boolean inited = false;
    boolean solved = false;

    public ValuePoint(DGraph dg, SootMethod method_location, Block block_location, Unit instruction_location, List<Integer> regIndex) {
        this.dg = dg;
        this.method_location = method_location;
        this.block_location = block_location;
        this.instruction_location = instruction_location;
        for (double i : regIndex) {
            target_regs.add((int) i);
        }
        dg.addNode(this);
    }

    public DGraph getDg() {
        return dg;
    }

    public List<BackwardContext> getBcs() {
        return bcs;
    }

    public SootMethod getMethod_location() {
        return method_location;
    }

    public Block getBlock_location() {
        return block_location;
    }

    public Unit getInstruction_location() {
        return instruction_location;
    }

    public Set<Integer> getTargetRgsIndexes() {
        return target_regs;
    }

    public void setAppendix(String str) {
        appendix = str;
    }

    @Override
    public Set<IDGNode> getDependents() {
        // TODO Auto-generated method stub

        HashSet<IDGNode> dps = new HashSet<IDGNode>();
        for (BackwardContext bc : bcs) {
            for (IDGNode node : bc.getDependentHeapObjects()) {
                dps.add(node);
            }
        }
        return dps;
    }

    @Override
    public int getUnsovledDependentsCount() {
        // TODO Auto-generated method stub
        int count = 0;
        for (IDGNode node : getDependents()) {
            if (!node.hasSolved()) {
                count++;
            }
        }
        //logger.info(this.hashCode() + "[]" + count + " " + bcs.size());
        return count;
    }

    @Override
    public boolean hasSolved() {

        return solved;
    }

    @Override
    public boolean canBePartiallySolve() {
        boolean can = false;
        boolean dsolved;
        SimulateEngine tmp;
        for (BackwardContext bc : bcs) {
            if (!solvedBCs.contains(bc)) {
                dsolved = true;
                for (HeapObject ho : bc.getDependentHeapObjects()) {
                    if (!ho.hasSolved()) {
                        dsolved = false;
                        break;
                    }
                }
                if (dsolved) {
                    solvedBCs.add(bc);
                    can = true;
                    tmp = new SimulateEngine(dg, bc);
                    tmp.simulate();
                    mergeResult(bc, tmp);
                }
            }
        }
        if (can) {
            solved = true;
        }

        return can;
    }

    @Override
    public void solve() {
        solved = true;
        //logger.info(["solve: " + this.method_location);
        SimulateEngine tmp;
        for (BackwardContext var : this.getBcs()) {
            tmp = new SimulateEngine(dg, var);
            tmp.simulate();
            mergeResult(var, tmp);
        }

    }

    public void mergeResult(BackwardContext var, SimulateEngine tmp) {
        HashMap<Value, HashSet<String>> sval = tmp.getCurrentValues();
        HashMap<Integer, HashSet<String>> resl = new HashMap<Integer, HashSet<String>>();
        Value reg;
        for (int i : target_regs) {
            if (i == -1) {
                reg = ((AssignStmt) var.getStmtPathTail()).getRightOp();
            } else {
                reg = ((Stmt) var.getStmtPathTail()).getInvokeExpr().getArg(i);
            }

            if (sval.containsKey(reg)) {
                resl.put(i, sval.get(reg));
            } else if (reg instanceof StringConstant) {
                resl.put(i, new HashSet<String>());
                resl.get(i).add(((StringConstant) reg).value);
            } else if (reg instanceof IntConstant) {
                resl.put(i, new HashSet<String>());
                resl.get(i).add(((IntConstant) reg).value + "");
            }
        }
        result.add(resl);
    }

    @Override
    public boolean inited() {
        return inited;
    }

    @Override
    public void initIfHavenot() {
        inited = true;
        bcs = BackwardController.getInstance().doBackWard(this, dg);

    }

    @Override
    public ArrayList<HashMap<Integer, HashSet<String>>> getResult() {
        return result;
    }

    public static List<ValuePoint> find(DGraph dg, String signature, List<Integer> regIndex) {
        List<ValuePoint> vps = new ArrayList<ValuePoint>();
//        System.out.println("signature:" + signature);
        List<StmtPoint> sps = StmtPoint.findCaller(signature);
        for (StmtPoint sp : sps) {
            ValuePoint valuePoint = new ValuePoint(dg, sp.getMethod_location(), sp.getBlock_location(), sp.getInstruction_location(), regIndex);
            valuePoint.setAppendix(signature);
            vps.add(valuePoint);
        }
        return vps;
    }

    public String toString() {
        if (!inited)
            return super.toString();
        StringBuilder sb = new StringBuilder();
        sb.append("Class: " + method_location.getDeclaringClass().toString() + "\n");
        sb.append("Method: " + method_location.toString() + "\n");
        sb.append("Target: " + instruction_location.toString() + "\n");
        sb.append("Solved: " + hasSolved() + "\n");
        sb.append("Depend: ");
        for (IDGNode var : this.getDependents()) {
            sb.append(var.hashCode());
            sb.append(", ");
        }
        sb.append("\n");
        sb.append("BackwardContexts: \n");
        BackwardContext tmp;
//		for (int i = 0; i < this.bcs.size(); i++) {
//			tmp = this.bcs.get(i);
//			sb.append("  " + i + "\n");
//			for (Stmt stmt : tmp.getExecTrace()) {
//				sb.append("    " + stmt + "\n");
//			}
//		}
        sb.append("ValueSet: \n");
        for (HashMap<Integer, HashSet<String>> resl : result) {
            sb.append("  ");
            for (int i : resl.keySet()) {
                sb.append(" |" + i + ":");
                for (String str : resl.get(i)) {
                    sb.append(str + ",");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public ValuePointResult retrieveResult() {
        ValuePointResult result = new ValuePointResult();
        for (HashMap<Integer, HashSet<String>> var : this.getResult()) {
            for (HashSet<String> valueSet : var.values()) {
                result.addPossibleValue(valueSet);
            }
        }
        return result;
    }

    public JsonObject toJson() {
        JsonObject js = new JsonObject();
        JsonObject tmp;
        for (HashMap<Integer, HashSet<String>> var : this.getResult()) {
            tmp = new JsonObject();
            for (int i : var.keySet()) {
                for (String str : var.get(i)) {
                    tmp.addProperty(i + "", str);
                }
            }
            if (tmp.size() > 0) {
                JsonArray valueSetArray;
                if (js.has("ValueSet")) {
                    valueSetArray = js.getAsJsonArray("ValueSet");
                } else {
                    valueSetArray = new JsonArray();
                }
                valueSetArray.add(tmp);
                js.add("ValueSet", valueSetArray);
            }
        }
        if (bcs != null)
            for (BackwardContext bc : bcs) {
                JsonArray bcArray;
                if (js.has("BackwardContexts")) {
                    bcArray = js.getAsJsonArray("BackwardContexts");
                } else {
                    bcArray = new JsonArray();
                }
                bcArray.add(bc.toJson());
                js.add("BackwardContexts", bcArray);
            }
        js.addProperty("location", this.getMethod_location().toString());
        js.addProperty("target", appendix);

        return js;
    }
}
