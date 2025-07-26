package droid.hack;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

import java.util.*;

public class TraceParam {
    private int paramPos;
    private SootMethod method;
    private int funcDepth;
    private Map<String, List<String>> result;

    public TraceParam(int paramPos, SootMethod method, int funcDepth) {
        this.paramPos = paramPos;
        this.method = method;
        this.funcDepth = funcDepth;
        this.result = new HashMap<>();
    }

    public void traceCrossFunctions() {
        CallGraphNode node = CallGraph.nodes.get(method.toString());
        HashSet<CallGraphNode> callBy = node.callBy;
        if (callBy.isEmpty()) System.out.println("This Method Has Not Been Called. ");
        for (CallGraphNode callGraphNode : callBy) {
            SootMethod callByMethod = callGraphNode.smthd;

            if (callByMethod.isConcrete()) {
                try {
                    Body body = callByMethod.retrieveActiveBody();
                    UnitGraph graph = new ExceptionalUnitGraph(body);
                    SimpleLocalDefs defs = new SimpleLocalDefs(graph);

                    List<Local> inputParamList = new ArrayList<>();
                    for (int i = 0; i < callByMethod.getParameterCount(); i++) {
                        Local param = body.getParameterLocal(i);
                        inputParamList.add(param);
                    }

                    for (Unit unit : graph) {
                        for (ValueBox box : unit.getUseAndDefBoxes()) {
                            Value value = box.getValue();

                            if (value instanceof InvokeExpr invokeExpr) {
                                SootMethod invokeMethod = invokeExpr.getMethod();

                                if (invokeMethod.equals(method)) {

                                    TraceLog traceLog = new TraceLog(invokeExpr, defs, graph, unit, new HashSet<>(), inputParamList, paramPos, funcDepth, callByMethod);
                                    traceLog.getTrace();
                                    if (!traceLog.getLogParameterLocalSources().isEmpty()) {
                                        System.out.println("==== Trace Parameter Among Function Sources ==== Func Depth: " + funcDepth + " ==== Call By: " + callByMethod.getSignature() + " ==== Size: " + traceLog.getLogParameterLocalSources().size());
                                        for (Map.Entry<String, List<String>> entry : traceLog.getLogParameterLocalSources().entrySet()) {
                                            List<String> sources = entry.getValue();
                                            if (!sources.isEmpty()) {
                                                result.put(callByMethod.toString(), sources);
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Error processing trace among functions " + method.getSignature() + ": " + e.getMessage());
                    throw e;
                }
            }
        }
    }

    public int getParamPos() {
        return paramPos;
    }

    public void setParamPos(int paramPos) {
        this.paramPos = paramPos;
    }

    public SootMethod getMethod() {
        return method;
    }

    public void setMethod(SootMethod method) {
        this.method = method;
    }

    public int getFuncDepth() {
        return funcDepth;
    }

    public void setFuncDepth(int funcDepth) {
        this.funcDepth = funcDepth;
    }

    public Map<String, List<String>> getResult() {
        return result;
    }

    public void setResult(Map<String, List<String>> result) {
        this.result = result;
    }
}
