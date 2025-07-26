package droid.hack;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.HashSet;

public class MethodUtility {

    public static boolean hash(String args) {
        try {
            return (Scene.v().getMethod(args) != null);
        } catch (Exception e) {
        }
        return false;
    }

    public static String dex2SootMthd(String sig) {
        String[] rest = sig.split("->");
        String cls = dex2SootClass(rest[0]);

        rest = rest[1].split("\\(");
        String mname = rest[0];

        rest = rest[1].split("\\)");
        String ret = rest[1];
        if (ret.equals("V"))
            ret = "void";
        else
            ret = dex2SootClass(ret);

        rest = rest[0].split(";");
        String pas = "";
        for (String str : rest) {
            pas += dex2SootClass(str) + ",";
        }
        pas = pas.replace(",,", ",");
        while (pas.endsWith(","))
            pas = pas.substring(0, pas.length() - 1);
        return String.format("<%s: %s %s(%s)>", cls, ret, mname, pas);
    }

    public static String dex2SootClass(String sig) {
        sig = sig.trim();
        if (sig.endsWith(";")) {
            sig = sig.substring(0, sig.length() - 1);
        }
        if (sig.startsWith("L")) {
            sig = sig.substring(1, sig.length());
        }

        return sig.replace('/', '.');
    }

    public static void findAllPointerOfThisMethod(HashSet<SootMethod> ms, String subSig, SootClass sc) {
        try {
            if (sc.getMethod(subSig) != null) {
                ms.add(sc.getMethod(subSig));
            }
        } catch (Exception e) {
        }

        if (sc.toString().equals("java.lang.Object")) {
            return;
        }

        if (sc.getSuperclass() != sc && sc.getSuperclass() != null) {
            findAllPointerOfThisMethod(ms, subSig, sc.getSuperclass());
        }
        for (SootClass itf : sc.getInterfaces()) {
            findAllPointerOfThisMethod(ms, subSig, itf);
        }

    }

}
