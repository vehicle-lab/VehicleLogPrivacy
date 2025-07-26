package droid.hack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import heros.solver.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.CompleteBlockGraph;

import java.util.*;

public class BackwardContext extends AbstractStmtSwitch implements StmtPath, ICollecter {
    private static final Logger logger = LoggerFactory.getLogger(BackwardContext.class);

    ValuePoint startPoint;
    DGraph dg;
    ArrayList<Block> blockes;
    Unit currentInstruction;

    HashSet<Value> intrestedVariable;
    ArrayList<Stmt> execTrace;
    ArrayList<SootMethod> methodes;
    ArrayList<Pair<SootMethod, Stmt>> pathList;

    HashSet<HeapObject> dependentHeapObjects;
    Stack<CallStackItem> callStack;

    boolean finished = false;

    @SuppressWarnings("unchecked")
    public BackwardContext(BackwardContext oldBc) {
        startPoint = oldBc.getStartPoint();
        dg = oldBc.getDg();
        methodes = (ArrayList<SootMethod>) oldBc.getMethodes().clone();
        blockes = (ArrayList<Block>) oldBc.getBlockes().clone();
        currentInstruction = oldBc.getCurrentInstruction();
        intrestedVariable = (HashSet<Value>) oldBc.getIntrestedVariable().clone();
        execTrace = (ArrayList<Stmt>) oldBc.getExecTrace().clone();
        pathList = (ArrayList<Pair<SootMethod, Stmt>>) oldBc.getPathList().clone();
        dependentHeapObjects = (HashSet<HeapObject>) oldBc.getDependentHeapObjects().clone();
        callStack = (Stack<CallStackItem>) oldBc.getCallStack().clone();
    }

    public BackwardContext(ValuePoint startPoint, DGraph dg) {
        this.startPoint = startPoint;
        this.dg = dg;

        methodes = new ArrayList<SootMethod>();
        methodes.add(0, startPoint.getMethod_location());

        blockes = new ArrayList<Block>();
        blockes.add(0, startPoint.getBlock_location());

        intrestedVariable = new HashSet<Value>();
        execTrace = new ArrayList<Stmt>();
        dependentHeapObjects = new HashSet<HeapObject>();
        callStack = new Stack<CallStackItem>();

        currentInstruction = startPoint.getInstruction_location();

        execTrace.add(0, (Stmt) currentInstruction);
        pathList = new ArrayList<>();
        Pair<SootMethod, Stmt> path = new Pair<>(startPoint.getMethod_location(), (Stmt) currentInstruction);
        pathList.add(0, path);

        // init
        Value tmp;
        for (int index : startPoint.getTargetRgsIndexes()) {
            if (index == -1) {// set heap object
                tmp = ((JAssignStmt) currentInstruction).getRightOp();
            } else {
                tmp = ((Stmt) currentInstruction).getInvokeExpr().getArg(index);
            }

            if (tmp instanceof JimpleLocal) {
                //logger.info("Target Variable is" + tmp.getClass() + " " + currentInstruction);
                this.addIntrestedVariable(tmp);
            } else if (tmp instanceof StringConstant || tmp instanceof IntConstant) {
            } else {
                //logger.info("Target Variable is" + tmp.getClass() + " " + currentInstruction);
            }
        }
    }

    public ArrayList<Pair<SootMethod, Stmt>> getPathList() {
        return pathList;
    }

    public void setPathList(ArrayList<Pair<SootMethod, Stmt>> pathList) {
        this.pathList = pathList;
    }

    public boolean backWardHasFinished() {
        return finished || intrestedVariable.size() == 0 || this.getPathList().size() > 100;
    }

    public List<BackwardContext> oneStepBackWard() {

        Unit nextInstrct = this.getCurrentBlock().getPredOf(currentInstruction);
//		logger.info("Current Method: " + this.getCurrentMethod().toString());
//		if (this.getCurrentMethod().toString().contains("<com.tencent.kt.qimei.p.m: java.lang.String a(java.lang.String,java.lang.String,com.tencent.kt.qimei.sdk.Qimei,long,java.lang.String)>")) {
//			System.out.println("Current Inst: " + currentInstruction.toString());
//			if (nextInstrct != null) {
//				System.out.println("Next Inst: " + nextInstrct.toString());
//			}
//		} else {
//			System.out.println("Current Method: " + this.getCurrentMethod().toString());
//		}
        // logger.info(this.hashCode() + "oneStepBackWard");
        if (nextInstrct != null) {
            return oneStepBackWard(nextInstrct);
        } else {
            List<BackwardContext> newBc = new ArrayList<BackwardContext>();
            BackwardContext tmp;
            CompleteBlockGraph cbg = BlockGenerator.getInstance().generate(this.getCurrentMethod().retrieveActiveBody());
            if (cbg.getHeads().contains(this.getCurrentBlock())) {
                if (this.getCallStack().isEmpty()) {
                    // logger.info("111111");
                    boolean allisParameterRef = true;
                    String ostr = "";
                    for (Value var : this.getIntrestedVariable()) {
                        ostr += var + ",";
                        if (!(var instanceof ParameterRef)) {
                            allisParameterRef = false;
                        }
                    }
                    if (!allisParameterRef) {
                        //logger.info(String.format("[%s] [Not all the intresteds are ParameterRef]: %s", this.hashCode(), ostr));
                        finished = true;
                        return newBc;
                    }

                    return oneStepBackWard2Caller();
                } else {// back call
                    // logger.info("22222");
                    getBackFromACall();
                    return newBc;
                }
            } else {
                // logger.info("33333");
                List<Block> bs = new ArrayList<Block>();
                bs.addAll(cbg.getPredsOf(this.getCurrentBlock()));
                BlockGenerator.removeCircleBlocks(bs, this.getCurrentBlock(), cbg);

                if (bs.size() == 0) {
                    //logger.info(String.format("[%s] [No PredsOf]: %s", this.hashCode(), this.getCurrentInstruction()));
                    finished = true;
                    return newBc;
                }

                this.setCurrentBlock(bs.get(0));

                for (Block tb : bs) {
                    if (tb == this.getCurrentBlock())
                        continue;

                    tmp = this.clone();
                    tmp.setCurrentBlock(tb);
                    newBc.addAll(tmp.oneStepBackWard(tb.getTail()));
                    newBc.add(tmp);
                }

                newBc.addAll(this.oneStepBackWard(this.getCurrentBlock().getTail()));
                return newBc;
            }
        }
    }

    public List<BackwardContext> oneStepBackWard(Unit nextInstrct) {
        List<BackwardContext> newBc = new ArrayList<BackwardContext>();
        currentInstruction = nextInstrct;

        boolean containsIntrestedThings = false;
        for (ValueBox box : currentInstruction.getUseAndDefBoxes()) {
            if (intrestedVariable.contains(box.getValue())) {
                containsIntrestedThings = true;
                break;
            } else if (box.getValue() instanceof ArrayRef && intrestedVariable.contains(((ArrayRef) box.getValue()).getBase())) {
                containsIntrestedThings = true;
                break;
            }
        }
        String ostr = this.getIntrestedVariableString();
        //logger.info(String.format("[%s] [Next Ins]: %s (%s)", this.hashCode(), currentInstruction, containsIntrestedThings ? "Y" : "N"));

        if (!containsIntrestedThings) {
            return newBc;
        }

        Stmt stmt = (Stmt) currentInstruction;
        this.getExecTrace().add(0, stmt);
        Pair<SootMethod, Stmt> pair = new Pair<>(this.getCurrentMethod(), stmt);
        this.setCurrentPath(pair);

        this.clear();
        stmt.apply(this);
        newBc.addAll(this.retrieve());
        this.clear();

        String nstr = this.getIntrestedVariableString();
        //logger.info(String.format("                 %s -> %s ", ostr, nstr));

        return newBc;
    }

    public List<BackwardContext> oneStepBackWard2Caller() {
        List<BackwardContext> newBc = new ArrayList<BackwardContext>();
        List<StmtPoint> sps = StmtPoint.findCaller(this.getCurrentMethod().toString());

        // chaoyang.lin modify begin
        // Prevent infinite loop when recursive call
        // To-Do:  Not consider the situation like A -> B -> A
        List<StmtPoint> filteredSps = new ArrayList<>();
        for (StmtPoint tmpSp: sps) {
            if (tmpSp.getMethod_location() != this.getCurrentMethod()) {
                filteredSps.add(tmpSp);
            }
        }
        sps = filteredSps;
        // chaoyang.lin modify end

        if (sps.size() <= 0) {
            //logger.info(String.format("[%s] [No Caller]: %s ", this.hashCode(), this.getCurrentMethod().toString()));
            finished = true;
            return newBc;
        }

        int len = sps.size();
        for (int i = 1; i < len; i++) {
            newBc.add(0, this.clone());
        }
        newBc.add(0, this);

        BackwardContext tmpBC;
        StmtPoint tmpSP;
        for (int i = 0; i < len; i++) {
            tmpBC = newBc.get(i);
            tmpSP = sps.get(i);

            tmpBC.oneStepBackWard2Caller(tmpSP);
        }
        newBc.remove(0);

        return newBc;
    }

    public void oneStepBackWard2Caller(StmtPoint tmpSP) {

        this.setCurrentMethod(tmpSP.getMethod_location());
        this.setCurrentBlock(tmpSP.getBlock_location());
        this.setCurrentInstruction(tmpSP.getInstruction_location());

        String ostr = this.getIntrestedVariableString();
        //logger.info(String.format("[%s] [Next Ins]: %s (caller:%s)", this.hashCode(), this.getCurrentInstruction(), this.getCurrentMethod()));

        HashMap<Integer, Value> regs = new HashMap<Integer, Value>();
        for (Value var : this.getIntrestedVariable()) {
            regs.put(((ParameterRef) var).getIndex(), var);
        }
        this.getIntrestedVariable().clear();

        InvokeExpr inve = ((Stmt) tmpSP.getInstruction_location()).getInvokeExpr();
        ParameterTransferStmt tmp;
        for (int j : regs.keySet()) {
            if (inve.getArg(j) instanceof StringConstant || inve.getArg(j) instanceof IntConstant) {
                // do not have to taint
            } else {
                this.addIntrestedVariable(inve.getArg(j));
            }
            tmp = new ParameterTransferStmt(regs.get(j), inve.getArg(j));
            Pair<SootMethod, Stmt> pair = new Pair<>(tmpSP.getMethod_location(), tmp);
            this.setCurrentPath(pair);
            this.getExecTrace().add(0, tmp);
        }

        String nstr = this.getIntrestedVariableString();
        //logger.info(String.format("                 %s -> %s ", ostr, nstr));
    }

    public void getBackFromACall() {
        CallStackItem citem = this.getCallStack().pop();

        Stmt retStmt = (Stmt) citem.getCurrentInstruction();

        Value opsite;
        for (Value param : this.getCurrentMethod().getActiveBody().getParameterRefs()) {
            if (this.getIntrestedVariable().contains(param)) {
                if (((ParameterRef) param).getIndex() >= retStmt.getInvokeExpr().getArgCount()) {
                    continue;
                }

                opsite = retStmt.getInvokeExpr().getArg(((ParameterRef) param).getIndex());

                this.removeIntrestedVariable(param);
                if (opsite instanceof Local) {
                    this.addIntrestedVariable(opsite);
                }
                Pair<SootMethod, Stmt> pair = new Pair<>(citem.getSmethd(), new ParameterTransferStmt(param, opsite));
                this.setCurrentPath(pair);
                this.getExecTrace().add(0, new ParameterTransferStmt(param, opsite));
            }
        }

        this.setCurrentMethod(citem.getSmethd());
        // logger.info(this.hashCode() + "back to " + citem.getSmethd());
        this.setCurrentBlock(citem.getBlcok());
        this.setCurrentInstruction(citem.getCurrentInstruction());

    }

    public ValuePoint getStartPoint() {
        return startPoint;
    }

    public void setStartPoint(ValuePoint startPoint) {
        this.startPoint = startPoint;
    }

    public DGraph getDg() {
        return dg;
    }

    public void setDg(DGraph dg) {
        this.dg = dg;
    }

    public SootMethod getCurrentMethod() {
        return getMethodes().get(0);
    }

    public void setCurrentPath(Pair<SootMethod, Stmt> pair) {
        this.pathList.add(0, pair);
    }

    public void setCurrentMethod(SootMethod currentMethod) {
        this.getMethodes().add(0, currentMethod);
    }

    public Block getCurrentBlock() {
        return getBlockes().get(0);
    }

    public void setCurrentBlock(Block currentBlock) {
        getBlockes().add(0, currentBlock);
    }

    public ArrayList<SootMethod> getMethodes() {
        return methodes;
    }

    public ArrayList<Block> getBlockes() {
        return blockes;
    }

    public Unit getCurrentInstruction() {
        return currentInstruction;
    }

    public void setCurrentInstruction(Unit currentInstruction) {
        this.currentInstruction = currentInstruction;
    }

    public String getIntrestedVariableString() {
        String ostr = "";
        for (Value var : this.getIntrestedVariable()) {
            ostr += var + ",";
        }
        return ostr;
    }

    public HashSet<Value> getIntrestedVariable() {
        return intrestedVariable;
    }

    public void addIntrestedVariable(Value v) {
        intrestedVariable.add(v);
    }

    public void removeIntrestedVariable(Value v) {
        intrestedVariable.remove(v);
    }
    public void addIntrestedVariableIfNotConstant(Value v) {
        if (v instanceof Local) {
            intrestedVariable.add(v);
        } else if (v instanceof NullConstant) {
            //logger.info("Variable is null no need to taint ");
        } else {
            //logger.info(String.format("[%s] [unknow addIntrestedVariableIfNotConstant] %s(%s)", this.hashCode(), v, v.getClass()));
        }
    }
    public void setIntrestedVariable(HashSet<Value> intrestedVariable) {
        this.intrestedVariable = intrestedVariable;
    }

    public ArrayList<Stmt> getExecTrace() {
        return execTrace;
    }

    public void setExecTrace(ArrayList<Stmt> execTrace) {
        this.execTrace = execTrace;
    }

    public void setDependentHeapObjects(HashSet<HeapObject> dependentHeapObjects) {
        this.dependentHeapObjects = dependentHeapObjects;

    }

    public HashSet<HeapObject> getDependentHeapObjects() {
        return dependentHeapObjects;
    }

    public Stack<CallStackItem> getCallStack() {
        return callStack;
    }

    public BackwardContext clone() {
        BackwardContext tmp = new BackwardContext(this);
        return tmp;
    }

    ////////////////////////////////////////////////////////
    //////////////////////// StmtSwitch/////////////////////

    @Override
    public void caseAssignStmt(AssignStmt stmt) {
        // TODO Auto-generated method stub
        // logger.info("[caseAssignStmt]");
        boolean leftisIntrested = this.getIntrestedVariable().contains(stmt.getLeftOp());
        this.removeIntrestedVariable(stmt.getLeftOp());
        Value value = stmt.getRightOp();
        if (value instanceof InvokeExpr) {// 11.6_VirtualInvokeExpr->InvokeExpr
            // logger.info("[VirtualInvokeExpr]");
            InvokeExpr tmp = (InvokeExpr) value;
            //String mthSig = tmp.getMethod().toString();
            // logger.info(String.format("[%s]",mthSig));
            handleInvokeExpr(stmt.getLeftOp(), leftisIntrested, tmp);

        } else if (value instanceof JNewExpr) {
            JNewExpr tjne = (JNewExpr) value;
            String clasName = tjne.getBaseType().toString();
            if (clasName.equals("java.lang.StringBuilder")) {

            } else {
                //logger.info(String.format("[%s] [Can't Handle caseAssignStmt->JNewExpr]: %s (%s)", this.hashCode(), stmt, value.getClass()));
            }

        } else if (value instanceof NewArrayExpr) {
            NewArrayExpr arraye = (NewArrayExpr) value;
            if (arraye.getBaseType().toString().equals("java.lang.Object")) {

            } else {
                //logger.info(String.format("[%s] [Can't Handle caseAssignStmt->NewArrayExpr]: %s (%s)", this.hashCode(), stmt, value.getClass()));
            }

        } else if (value instanceof FieldRef) {// dependent

            HeapObject ho = HeapObject.getInstance(dg, ((FieldRef) value).getField());
            if (!this.getDependentHeapObjects().contains(ho)) {
                this.getDependentHeapObjects().add(ho);
                dg.addNode(ho);
            }

        } else if (value instanceof JimpleLocal) {
            this.getIntrestedVariable().add(value);
        } else if (value instanceof CastExpr) {
            this.getIntrestedVariable().add(((CastExpr) value).getOp());
        } else if (value instanceof StringConstant) {

        } else {
            //logger.info(String.format("[%s] [Can't Handle caseAssignStmt->RightOp]: %s (%s)", this.hashCode(), stmt, value.getClass()));
        }

    }

    @Override
    public void caseInvokeStmt(InvokeStmt stmt) {
        // TODO Auto-generated method stub
        handleInvokeExpr(null, false, stmt.getInvokeExpr());
        // super.caseInvokeStmt(stmt);
    }

    public void handleInvokeExpr(Value restAssignTo, boolean leftisIntrested, InvokeExpr invokExp) {
        String mthSig = invokExp.getMethod().toString();

        if (mthSig.equals("<java.lang.StringBuilder: java.lang.String toString()>")) {
            // tmp.getBase()
            this.addIntrestedVariable(((VirtualInvokeExpr) invokExp).getBase());
        } else if (mthSig.equals("<java.lang.String: java.lang.String trim()>")) {
            // tmp.getBase()
            this.addIntrestedVariable(((VirtualInvokeExpr) invokExp).getBase());
        } else if (mthSig.equals("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>")) {
            this.addIntrestedVariable(((VirtualInvokeExpr) invokExp).getBase());
            if (invokExp.getArg(0) instanceof Local) {
                this.addIntrestedVariable(invokExp.getArg(0));
            }
        } else if (mthSig.equals("<android.content.Context: java.lang.String getString(int)>")) {
            if (invokExp.getArg(0) instanceof Local) {
                this.addIntrestedVariable(invokExp.getArg(0));
            }
        } else if (mthSig.equals("<android.content.res.Resources: java.lang.String getString(int)>")) {
            if (invokExp.getArg(0) instanceof Local) {
                this.addIntrestedVariable(invokExp.getArg(0));
            }
        } else if (mthSig.equals("<java.lang.String: java.lang.String format(java.lang.String,java.lang.Object[])>")) {
            for (Value arg : invokExp.getArgs()) {
                if (arg instanceof Local) {
                    this.addIntrestedVariable(arg);
                }
            }
        } else if (mthSig.equals("<android.content.res.Resources: int getIdentifier(java.lang.String,java.lang.String,java.lang.String)>")) {
            for (Value arg : invokExp.getArgs()) {
                if (arg instanceof Local) {
                    this.addIntrestedVariable(arg);
                }
            }
        } else if (mthSig.equals("<java.lang.String: byte[] getBytes()>")
                || mthSig.equals("<java.lang.String: byte[] getBytes(java.nio.charset.Charset)>")
                || mthSig.equals("<java.lang.String: byte[] getBytes(java.lang.String)>")) {
            assert invokExp instanceof JVirtualInvokeExpr;
            this.addIntrestedVariable(((JVirtualInvokeExpr) invokExp).getBase());
        }
        else {
            if (!diveIntoMethodCall(restAssignTo, leftisIntrested, invokExp)) {
                //logger.info(String.format("[%s] [Can't Handle handleInvokeExpr->VirtualInvokeExpr]: %s (%s)", this.hashCode(), invokExp, invokExp.getClass()));
            }
        }
    }

    public boolean equals(BackwardContext right) {
        Stmt rightStmt = (Stmt) right.currentInstruction;
        Stmt stmt = (Stmt) this.currentInstruction;
        if (rightStmt.containsInvokeExpr() && stmt.containsInvokeExpr()) {
            String sig = stmt.getInvokeExpr().getMethod().getSignature();
            String rightSig = rightStmt.getInvokeExpr().getMethod().getSignature();
            if (sig.equals("<java.util.function.Function: java.lang.Object apply(java.lang.Object)>")
                    && rightSig.equals("<java.util.function.Function: java.lang.Object apply(java.lang.Object)>")){
                return true;
            }
        }
        return this.currentInstruction.toString().equals(right.currentInstruction.toString())
                && this.execTrace.size() == right.execTrace.size();
    }

    // chaoyang.lin modify begin
    public String getIdentifier() {
        Stmt stmt = (Stmt) this.currentInstruction;
        if (stmt.containsInvokeExpr()) {
            String sig = stmt.getInvokeExpr().getMethod().getSignature();
            return this.getCurrentMethod().toString() + sig;
        }
        return this.getCurrentMethod().toString() + this.currentInstruction.toString() + String.valueOf(this.execTrace.size());
    }
    // chaoyang.lin modify end

    @Override
    public void caseIdentityStmt(IdentityStmt stmt) {
        // TODO Auto-generated method stub
        if (this.getIntrestedVariable().contains(stmt.getLeftOp())) {
            this.removeIntrestedVariable(stmt.getLeftOp());
            if (stmt.getRightOp() instanceof ParameterRef) {
                this.addIntrestedVariable(stmt.getRightOp());
            } else {
                //logger.info(String.format("[%s] [Can't Handle caseIdentityStmt->RightOpUnrecognized]: %s (%s)", this.hashCode(), stmt, stmt.getLeftOp().getClass()));
            }
        } else {
            ////logger.info(String.format("[%s] [Can't Handle caseIdentityStmt->LeftOpNotIntrested]: %s (%s)", this.hashCode(), stmt, stmt.getLeftOp().getClass()));
        }
    }

    @Override
    public void defaultCase(Object obj) {
        //logger.info(String.format("[%s] [Can't Handle]: %s (%s)", this.hashCode(), obj, obj.getClass()));
    }

    public boolean diveIntoMethodCall(Value leftOp, boolean leftisIntrested, InvokeExpr ive) {
        //GlobalStatistics.getInstance().countDiveIntoMethodCall();
        // logger.info(this.hashCode() + "diveIntoMethodCall");
        if (!ive.getMethod().getDeclaringClass().isApplicationClass() || !ive.getMethod().isConcrete())
            return false;

        this.getExecTrace().remove(this.getCurrentInstruction());
        CallStackItem citem = new CallStackItem(this.getCurrentMethod(), this.getCurrentBlock(), this.getCurrentInstruction(), leftOp);
        this.getCallStack().push(citem);
        //GlobalStatistics.getInstance().updateMaxCallStack(this.getCallStack().size());

        CompleteBlockGraph cbg = BlockGenerator.getInstance().generate(ive.getMethod().retrieveActiveBody());
        List<Block> tails = new ArrayList<Block>();
        for (Block block : cbg.getTails()) {
            if (block.getTail() instanceof ReturnStmt) {
                tails.add(block);
            }
        }
        if (tails.size() == 0) {
            //logger.info(String.format("[%s] [All Tail not ReturnStmt]: %s (%s)", this.hashCode(), this.getCurrentInstruction(), this.getCurrentInstruction().getClass()));
        }

        List<BackwardContext> bcs = new ArrayList<BackwardContext>();
        int len = tails.size();
        // logger.info(this.hashCode() + "tails.size" + len);

        for (int i = 1; i < len; i++) {
            bcs.add(this.clone());
        }
        bcs.add(0, this);

        BackwardContext tbc;
        Block tblock;
        Stmt rets = null;
        ParameterTransferStmt tmp;
        for (int i = 0; i < len; i++) {
            tbc = bcs.get(i);
            tblock = tails.get(i);
/*
			if (!(tblock.getTail() instanceof ReturnStmt)) {
				logger.info(String.format("[%s] [Tail not ReturnStmt]: %s (%s)", this.hashCode(), tblock.getTail(), tblock.getTail().getClass()));
			}
			rets = (ReturnStmt) tblock.getTail();

			tmp = new ParameterTransferStmt(leftOp, rets.getOp());
			tbc.getExecTrace().add(0, tmp);

			if (rets.getOp() instanceof Local) {// ?? parameter
				tbc.addIntrestedVariable(rets.getOp());
			}
*/
            rets = (Stmt) tblock.getTail();
            if (leftOp != null && leftisIntrested) {

                if (!(tblock.getTail() instanceof ReturnStmt)) {
                    //logger.info(String.format("[%s] [Tail not ReturnStmt]: %s (%s)", this.hashCode(), tblock.getTail(), tblock.getTail().getClass()));
                }

                tmp = new ParameterTransferStmt(leftOp, ((ReturnStmt) rets).getOp());
                Pair<SootMethod, Stmt> pair = new Pair<>(ive.getMethod(), tmp);
                this.setCurrentPath(pair);
                tbc.getExecTrace().add(0, tmp);

                tbc.addIntrestedVariableIfNotConstant(((ReturnStmt) rets).getOp());// ??
                // parameter
            }

            tbc.setCurrentMethod(ive.getMethod());
            tbc.setCurrentBlock(tblock);
            tbc.setCurrentInstruction(rets);
        }
        bcs.remove(0);

        bcs.forEach(bc -> {
            this.put(bc);
        });
        bcs.clear();

        return true;
    }

    @Override
    public Unit getStmtPathTail() {
        return this.getExecTrace().get(this.getExecTrace().size() - 1);
    }

    @Override
    public List<Stmt> getStmtPath() {
        return this.getExecTrace();
    }

    List<BackwardContext> newGeneratedContext = new ArrayList<BackwardContext>();

    @Override
    public void clear() {
        newGeneratedContext.clear();
    }

    @Override
    public void put(BackwardContext bc) {
        newGeneratedContext.add(bc);
    }

    @Override
    public List<BackwardContext> retrieve() {
        return newGeneratedContext;
    }

    ////////////////////////////////////////////////////////

    public JsonArray toJson() {
        JsonArray result = new JsonArray();
        for (Pair<SootMethod, Stmt> pair : this.pathList) {
            JsonObject path = new JsonObject();
            path.addProperty("method", pair.getO1().getSignature());
            path.addProperty("exec_trace", pair.getO2().toString());
            result.add(path);
        }

        return result;
    }
}
