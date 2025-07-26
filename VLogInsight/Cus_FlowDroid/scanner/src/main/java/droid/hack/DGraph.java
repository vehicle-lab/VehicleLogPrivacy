package droid.hack;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;

public class DGraph {
    HashSet<IDGNode> nodes = new HashSet<IDGNode>();
    private static final Logger logger = LoggerFactory.getLogger(DGraph.class);

    public void addNode(IDGNode node) {
        nodes.add(node);
    }

    public HashSet<IDGNode> getNodes() {
        return nodes;
    }

    public void solve(List<ValuePoint> vps) {
        IDGNode tnode;
        while (true) {
            initAllIfNeed();
            tnode = getNextSolvableNode();

            if (hasSolvedAllTarget(vps)) {
                logger.info("[DONE]: Solved All Targets!");
                return;
            }

            if (tnode == null) {
                logger.info("[DONE]: No Solvable Node Left!");
                if (try2PartiallySolve()) {
                    continue;
                } else {
                    logger.info("[DONE]: No PartiallySolvable Node Left!");
                    return;
                }
            }
            tnode.solve();
        }
    }

    private void initAllIfNeed() {
        IDGNode whoNeedInit;
        while (true) {
            whoNeedInit = null;
            for (IDGNode tmp : nodes)
                if (!tmp.inited()) {
                    whoNeedInit = tmp;
                    break;
                }
            if (whoNeedInit == null) {
                return;
            } else {
                whoNeedInit.initIfHavenot();
            }
        }
    }

    private IDGNode getNextSolvableNode() {
        for (IDGNode tmp : nodes) {
            if (tmp.getUnsovledDependentsCount() == 0 && !tmp.hasSolved()) {
                return tmp;
            }
        }
        return null;
    }

    private boolean try2PartiallySolve() {
        for (IDGNode tmp : nodes) {
            if (tmp.canBePartiallySolve()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSolvedAllTarget(List<ValuePoint> vps) {
        for (ValuePoint vp : vps) {
            if (!vp.hasSolved())
                return false;
        }
        return true;
    }

    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        JsonObject jnodes = new JsonObject();
        JsonObject jedges = new JsonObject();
        for (IDGNode node : nodes) {
            jnodes.addProperty(node.hashCode() + "", node.getClass().getSimpleName());
            for (IDGNode subn : node.getDependents()) {
                jedges.addProperty(node.hashCode() + "", subn.hashCode() + "");
            }
        }
        result.add("nodes", jnodes);
        result.add("edges", jedges);
        return result;
    }
}
