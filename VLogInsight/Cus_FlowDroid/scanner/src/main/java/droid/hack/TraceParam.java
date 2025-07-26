package droid.hack;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

import java.util.*;


public class TraceParam {
    public int paramPos;
    public SootMethod method;
    public int funcDepth;
    public Map<String, List<String>> result;
    public Set<SootMethod> visitedMethod;

    public TraceParam(int paramPos, SootMethod method, int funcDepth, Set<SootMethod> visitedMethod) {
        this.paramPos = paramPos;
        this.method = method;
        this.funcDepth = funcDepth;
        this.result = new HashMap<>();
        this.visitedMethod = visitedMethod;
    }

    public void traceCrossFunctions() {
        CallGraphNode node = CallGraph.nodes.get(method.toString());
        HashSet<CallGraphNode> callBy = node.callBy;
        if (callBy.isEmpty()) System.out.println("This Method Has Not Been Called. ");
        for (CallGraphNode callGraphNode : callBy) {
            SootMethod callByMethod = callGraphNode.smthd;

            if (visitedMethod.contains(callByMethod)) continue;
            visitedMethod.add(callByMethod);

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

                                    TraceLog traceLog = new TraceLog(invokeExpr, defs, graph, unit, new HashSet<>(), inputParamList, paramPos, funcDepth, callByMethod, visitedMethod);
                                    traceLog.getTrace();
                                    if (!traceLog.getLogParameterLocalSources().isEmpty()) {
                                        System.out.println("==== Trace Parameter Among Function Sources ==== Func Depth: " + funcDepth + " ==== Call By: " + callByMethod.getSignature() + " ==== Size: " + traceLog.getLogParameterLocalSources().size());
                                        for (Map.Entry<String, Set<String>> entry : traceLog.getLogParameterLocalSources().entrySet()) {
                                            Set<String> sources = entry.getValue();
                                            if (!sources.isEmpty()) {
                                                result.put(callByMethod.toString(), sources.stream().toList());
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
}
