package droid.hack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JCastExpr;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.Pair;
import soot.toolkits.scalar.SimpleLocalDefs;

import java.util.*;

public class TraceLog {
    private static final Logger log = LoggerFactory.getLogger(TraceLog.class);
    private int funcDepth;
    private InvokeExpr invokeExpr;
    private Unit unit;
    private SimpleLocalDefs defs;
    private UnitGraph graph;
    private SootMethod invokeMethod;
    private Set<Unit> previousLogUnit;
    private List<Local> inputParamList;
    private int paramPos;
    private SootMethod callByMethod;
    private Map<String, List<String>> logParameterLocalSources = new HashMap<>();

    public TraceLog(InvokeExpr invokeExpr, SimpleLocalDefs defs, UnitGraph graph, Unit unit, Set<Unit> previousLogUnit, List<Local> inputParamList, int paramPos, int funcDepth, SootMethod callByMethod) {
        this.invokeExpr = invokeExpr;
        this.unit = unit;
        this.defs = defs;
        this.graph = graph;
        this.previousLogUnit = previousLogUnit;
        this.invokeMethod = invokeExpr.getMethod();
        this.inputParamList = inputParamList;
        this.paramPos = paramPos;
        this.funcDepth = funcDepth;
        this.callByMethod = callByMethod;
    }

    public void getTrace() {
        for (int i = 0; i < invokeExpr.getArgs().size(); i++) {
            if (paramPos != -1 && paramPos != i) continue;
            Value arg = invokeExpr.getArg(i);
            if (arg instanceof InvokeExpr) {
                InvokeExpr invokeValue = (InvokeExpr) arg;
                if (invokeExpr.getMethod().getName().equals("toString")) {
                    Value base = ((InstanceInvokeExpr) invokeValue).getBase();
                    extractVariables(base, unit);
                }
            } else if (arg instanceof BinopExpr) {
                extractVariables(arg, unit);
            } else if (arg instanceof Local) {
                String argName = arg.toString();
                logParameterLocalSources.putIfAbsent(argName, new ArrayList<>());
                traceVariableSource((Local) arg, unit, logParameterLocalSources.get(argName), new HashSet<>(), 0);
            } else if (arg instanceof Constant) {
                System.out.println("extract constant string: " + arg);
            }
        }
    }

    public void extractVariables(Value value, Unit unit) {
        if (value instanceof InvokeExpr) {
            InvokeExpr invokeValue = (InvokeExpr) value;
            if (invokeExpr.getMethod().getName().equals("toString")) {
                Value base = ((InstanceInvokeExpr) invokeValue).getBase();
                extractVariables(base, unit);
            }
        } else if (value instanceof Local) {
            Local localExpr = (Local) value;
            String localExprName = localExpr.getName();
            logParameterLocalSources.putIfAbsent(localExprName, new ArrayList<>());
            int index = inputParamList.indexOf(localExpr);
            if (index != -1) {
                if (logParameterLocalSources.get(localExprName).isEmpty()) {
                    String res = traceVariablesCrossFunctions(index);
                    if (!res.isEmpty())
                        logParameterLocalSources.get(localExprName).add(res);
                }
            } else {
                traceVariableSource(localExpr, unit, logParameterLocalSources.get(localExprName), new HashSet<>(), 0);
            }

        } else if (value instanceof BinopExpr) {
            BinopExpr binopExpr = (BinopExpr) value;
            extractVariables(binopExpr.getOp1(), unit);
            extractVariables(binopExpr.getOp2(), unit);
        } else if (value instanceof Constant) {
            System.out.println("extract constant string: " + value);
        } else {
            System.out.println("other expression:" + value);
        }
    }

    public String traceVariablesCrossFunctions(int index) {
        if (this.funcDepth < 5) {
            TraceParam traceParam = new TraceParam(index, this.callByMethod, this.funcDepth + 1);
            traceParam.traceCrossFunctions();
            Map<String, List<String>> result = traceParam.getResult();
            if (!result.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                StringBuilder tmp = new StringBuilder();
                StringBuilder res = new StringBuilder();
                for (int k = 0; k < funcDepth; k++)
                    tmp.append("-");
                for (String callByMethod : result.keySet()) {
                    sb.append(" -").append(tmp).append("Call By: ").append(callByMethod).append("\n");
                    for (String source : result.get(callByMethod)) {
                        sb.append("  --").append(tmp).append(source).append("\n");
                        res.append(source).append("|");
                    }
                }
                if (this.funcDepth == 0)
                    res.deleteCharAt(res.length() - 1);
                return res.toString();
            }
        } else {
            System.out.println("Function Call Stack Depth Above 5, Stop Tracing");
        }
        return "";
    }

    public void traceVariableSource(Local variable, Unit currentUnit, List<String> sources, Set<Local> visitedLocal, int depth) {
        if (depth > 10) {
            return;
        }
        List<Unit> defUnits = defs.getDefsOfAt(variable, currentUnit);
        for (Unit defUnit : defUnits) {
            if (defUnit instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) defUnit;
                Value rightOp = assignStmt.getRightOp();
//                System.out.println("right op: " + rightOp.getClass().getName());
                if (rightOp instanceof InvokeExpr) {
                    InvokeExpr invokeExpr = (InvokeExpr) rightOp;
//                    System.out.println("Function call: " + invokeExpr);

                    if (invokeExpr.getMethod().getName().equals("toString")) {
                        Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
                        Set<Pair<Local, Unit>> visitedUnits = new HashSet<>();
                        analyzeStringBuilder(base, visitedUnits, defUnit);
                    } else {
//                        sources.add("Function call: " + invokeExpr);
                        for (Value arg : invokeExpr.getArgs()) {
                            System.out.println("arg in method: " + arg);
                            if (arg instanceof Local) {
//                                sources.add("Argument: " + arg);
                                System.out.println("Argument in method: " + arg);
                                traceVariableSource((Local) arg, defUnit, sources, visitedLocal, depth + 1);
                            } else {
                                System.out.println("Direct argument in method: :" + arg);
                            }
                        }
                    }

                } else if (rightOp instanceof Local) {
                    if (visitedLocal.contains(rightOp)) continue;
                    visitedLocal.add((Local) rightOp);
                    traceVariableSource((Local) rightOp, defUnit, sources, visitedLocal, 0);
                } else if (rightOp instanceof JCastExpr) {
                    JCastExpr castExpr = (JCastExpr) rightOp;
                    Value base = castExpr.getOp();
                    traceVariableSource((Local) base, defUnit, sources, visitedLocal, 0);
                } else {
                    System.out.println("add direct argument:" + rightOp);
                }
            }
        }
        int index = inputParamList.indexOf(variable);
        if (index != -1) {
            if (logParameterLocalSources.containsKey(variable.getName())) {
                if (logParameterLocalSources.get(variable.getName()).isEmpty()) {
                    String res = traceVariablesCrossFunctions(index);
                    if (!res.isEmpty())
                        sources.add(res);
                } else
                    sources.addAll(logParameterLocalSources.get(variable.getName()));
            } else {
                String res = traceVariablesCrossFunctions(index);
                if (!res.isEmpty())
                    sources.add(res);
            }
        }
    }

    public void analyzeStringBuilder(Value base, Set<Pair<Local, Unit>> visitedUnits, Unit currentLogUnit) {
        if (base instanceof Local) {
            Local local = (Local) base;
            Set<Unit> collectUnitsInRange = collectUnitsInRange(currentLogUnit);
            for (Unit unit : collectUnitsInRange) {
                Pair<Local, Unit> currentPair = new Pair<>(local, unit);

                if (visitedUnits.contains(currentPair)) continue;
                visitedUnits.add(currentPair);

//                System.out.println(unit);

                if (unit instanceof AssignStmt) {
                    AssignStmt assignStmt = (AssignStmt) unit;
                    Value leftOp = assignStmt.getLeftOp();
                    Value rightOp = assignStmt.getRightOp();
//                    System.out.println("left op: " + leftOp);
//                    System.out.println("right op: " + rightOp);

                    if (rightOp instanceof InvokeExpr) {
                        InvokeExpr expr = (InvokeExpr) rightOp;
                        if (expr.getMethod().getName().equals("append") && leftOp.equals(base)) {
                            for (Value arg : expr.getArgs()) {
//                                System.out.println("extract argument from append method: " + arg);
                                extractVariables(arg, unit);
                            }
                        }
                    }
                }
            }
        }
    }

    private Set<Unit> collectUnitsInRange(Unit currentLogUnit) {
        Set<Unit> inRangeUnits = new HashSet<>();
        Queue<Unit> toVisit = new LinkedList<>();
        Set<Unit> visited = new HashSet<>();

//        System.out.println("previous log unit: " + previousLogUnit);
//        System.out.println("current log unit: " + currentLogUnit);
        toVisit.add(currentLogUnit);

        while (!toVisit.isEmpty()) {
            Unit unit = toVisit.poll();

            if (visited.contains(unit)) continue;
            visited.add(unit);

            if (previousLogUnit.contains(unit)) break;

            inRangeUnits.add(unit);

            toVisit.addAll(graph.getPredsOf(unit));
        }

        return inRangeUnits;
    }

    public int getFuncDepth() {
        return funcDepth;
    }

    public void setFuncDepth(int funcDepth) {
        this.funcDepth = funcDepth;
    }

    public InvokeExpr getInvokeExpr() {
        return invokeExpr;
    }

    public void setInvokeExpr(InvokeExpr invokeExpr) {
        this.invokeExpr = invokeExpr;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public SimpleLocalDefs getDefs() {
        return defs;
    }

    public void setDefs(SimpleLocalDefs defs) {
        this.defs = defs;
    }

    public UnitGraph getGraph() {
        return graph;
    }

    public void setGraph(UnitGraph graph) {
        this.graph = graph;
    }

    public SootMethod getInvokeMethod() {
        return invokeMethod;
    }

    public void setInvokeMethod(SootMethod invokeMethod) {
        this.invokeMethod = invokeMethod;
    }

    public Set<Unit> getPreviousLogUnit() {
        return previousLogUnit;
    }

    public void setPreviousLogUnit(Set<Unit> previousLogUnit) {
        this.previousLogUnit = previousLogUnit;
    }

    public List<Local> getInputParamList() {
        return inputParamList;
    }

    public void setInputParamList(List<Local> inputParamList) {
        this.inputParamList = inputParamList;
    }

    public int getParamPos() {
        return paramPos;
    }

    public void setParamPos(int paramPos) {
        this.paramPos = paramPos;
    }

    public SootMethod getCallByMethod() {
        return callByMethod;
    }

    public void setCallByMethod(SootMethod callByMethod) {
        this.callByMethod = callByMethod;
    }

    public Map<String, List<String>> getLogParameterLocalSources() {
        return logParameterLocalSources;
    }

    public void setLogParameterLocalSources(Map<String, List<String>> logParameterLocalSources) {
        this.logParameterLocalSources = logParameterLocalSources;
    }
}