package droid.hack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;

import java.util.HashSet;
import java.util.Hashtable;

public class CallGraph {

    private static final Logger logger = LoggerFactory.getLogger(CallGraph.class);

    static Hashtable<String, CallGraphNode> nodes = new Hashtable<String, CallGraphNode>();

    static Hashtable<String, HashSet<SootMethod>> fieldSetters = new Hashtable<String, HashSet<SootMethod>>();

    public static void init() {
        long startTime = System.currentTimeMillis();
        CallGraphNode tmp;
        Value tv;
        FieldRef fr;
        String str;
        for (SootClass sclas : Scene.v().getClasses()) {
            for (SootMethod smthd : sclas.getMethods()) {
                tmp = new CallGraphNode(smthd);
                nodes.put(smthd.toString(), tmp);
            }
        }
        logger.info("start constructing cg for backward analysis");
        for (SootClass sclas : Scene.v().getClasses()) {
            for (SootMethod smthd : ListUtils.clone(sclas.getMethods())) {
                if (!smthd.isConcrete())
                    continue;
                Body body = smthd.retrieveActiveBody();
                if (body == null)
                    continue;
                for (Unit unit : body.getUnits()) {
                    if (unit instanceof Stmt) {
                        if (((Stmt) unit).containsInvokeExpr()) {
                            try {
                                addCall(smthd, ((Stmt) unit).getInvokeExpr().getMethod());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        for (ValueBox var : ((Stmt) unit).getDefBoxes()) {
                            tv = var.getValue();
                            if (tv instanceof FieldRef) {
                                fr = (FieldRef) tv;
                                if (fr.getField().getDeclaringClass().isApplicationClass()) {
                                    str = fr.getField().toString();
                                    if (!fieldSetters.containsKey(str)) {
                                        fieldSetters.put(str, new HashSet<SootMethod>());
                                    }
                                    fieldSetters.get(str).add(smthd);
                                }
                            }
                        }
                    }
                }
            }
        }
        logger.info("[CG time]: " + (System.currentTimeMillis() - startTime) + "s");
    }

    private static void addCall(SootMethod from, SootMethod to) {
        CallGraphNode fn, tn;
        fn = getNode(from);
        tn = getNode(to);
        if (fn == null || tn == null) {
            return;
        }

        fn.addCallTo(tn);
        tn.addCallBy(fn);

    }

    public static CallGraphNode getNode(SootMethod from) {
        return getNode(from.toString());
    }

    public static CallGraphNode getNode(String from) {
        return nodes.get(from);
    }

    public static HashSet<SootMethod> getSetter(SootField sootField) {
        return fieldSetters.get(sootField.toString());
    }
}
