package droid.hack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootField;

import java.util.*;

public class HeapObject implements IDGNode {

    private final static Logger logger = LoggerFactory.getLogger(HeapObject.class);
    DGraph dg;

    SootField sootField;
    boolean inited = false;
    boolean solved = false;
    ArrayList<ValuePoint> vps;
    HashSet<ValuePoint> solvedVps = new HashSet<ValuePoint>();

    ArrayList<HashMap<Integer, HashSet<String>>> result = new ArrayList<HashMap<Integer, HashSet<String>>>();

    private HeapObject(DGraph dg, SootField sootField) {
        this.dg = dg;
        this.sootField = sootField;
    }

    @Override
    public Set<IDGNode> getDependents() {
        // TODO Auto-generated method stub

        HashSet<IDGNode> dps = new HashSet<IDGNode>();
        for (ValuePoint vp : vps) {
            dps.add(vp);
        }
        return dps;

    }

    @Override
    public int getUnsovledDependentsCount() {
        // TODO Auto-generated method stub
        int count = 0;
        for (IDGNode vp : getDependents()) {
            if (!vp.hasSolved()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean hasSolved() {
        // TODO Auto-generated method stub
        return solved;
    }

    @Override
    public void solve() {
        // TODO Auto-generated method stub
        solved = true;
        logger.info("[HEAP SOLVE]" + sootField);

        for (ValuePoint vp : vps) {
            ArrayList<HashMap<Integer, HashSet<String>>> vpResult = vp.getResult();
            for (HashMap<Integer, HashSet<String>> res : vpResult) {
                if (res.containsKey(-1)) {
                    result.add(res);
                }
            }
        }
    }

    @Override
    public boolean canBePartiallySolve() {
        boolean can = false;
        for (ValuePoint vp : vps) {
            if (!solvedVps.contains(vp) && vp.hasSolved()) {
                solvedVps.add(vp);
                can = true;
                for (HashMap<Integer, HashSet<String>> res : vp.getResult()) {
                    if (res.containsKey(-1)) {
                        result.add(res);
                    }
                }
            }
        }
        if (can) {
            solved = true;
        }
        return can;
    }

    @Override
    public void initIfHavenot() {
        // TODO Auto-generated method stub
        vps = new ArrayList<ValuePoint>();
        ValuePoint tmp;
        List<StmtPoint> sps = StmtPoint.findSetter(sootField);
        for (StmtPoint sp : sps) {
            tmp = new ValuePoint(dg, sp.getMethod_location(), sp.getBlock_location(), sp.getInstruction_location(), Collections.singletonList(-1));
            vps.add(tmp);
        }
        //logger.info("[HEAP INIT]" + sootField + " " + StmtPoint.findSetter(sootField).size());
        inited = true;

    }

    @Override
    public boolean inited() {
        // TODO Auto-generated method stub
        return inited;
    }

    @Override
    public ArrayList<HashMap<Integer, HashSet<String>>> getResult() {
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((sootField == null) ? 0 : sootField.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        HeapObject other = (HeapObject) obj;
        if (sootField == null) {
            if (other.sootField != null)
                return false;
        } else if (!sootField.equals(other.sootField))
            return false;
        return true;
    }

    @Override
    public String toString() {
        // TODO Auto-generated method stub
        if (!inited)
            return super.toString();
        StringBuilder sb = new StringBuilder();
        sb.append("Field: " + sootField + "\n");
        sb.append("Solved: " + hasSolved() + "\n");
        sb.append("Depend: ");
        for (IDGNode var : this.getDependents()) {
            sb.append(var.hashCode());
            sb.append(", ");
        }
        sb.append("\n");
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

    static HashMap<String, HeapObject> hos = new HashMap<String, HeapObject>();

    public static HeapObject getInstance(DGraph dg, SootField sootField) {
        String str = sootField.toString();
        if (!hos.containsKey(str)) {
            hos.put(str, new HeapObject(dg, sootField));
        }
        return hos.get(str);
    }

}
