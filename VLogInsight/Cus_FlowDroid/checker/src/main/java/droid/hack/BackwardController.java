package droid.hack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BackwardController {
    private static final Logger logger = LoggerFactory.getLogger(BackwardController.class);
    static BackwardController sc = new BackwardController();

    public static BackwardController getInstance() {
        return sc;
    }

    private BackwardController() {

    }


    public List<BackwardContext> doBackWard(ValuePoint vp, DGraph dg) {
//		System.out.println("===================== doBackWard =====================");
//		System.out.println(vp.getMethod_location().toString());
        List<BackwardContext> bcs = new ArrayList<BackwardContext>();
        bcs.add(new BackwardContext(vp, dg));

        // chaoyang.lin modify begin
        HashSet<String> contextVisited = new HashSet<>();
        // chaoyang.lin modify end
        BackwardContext bc;
        while (true) {
            bc = null;
            for (BackwardContext tmp : bcs) {
                if (!tmp.backWardHasFinished()) {
                    bc = tmp;
                    break;
                }
            }
            if (bc == null) {
                break;
            }
//			System.out.println("======================= BEGIN =========================");
//			System.out.println("Current bc: " + bc.currentInstruction.toString() + "(" + String.valueOf(bc.hashCode()) + ")");
//			System.out.println("Current Method: " + bc.getCurrentMethod().toString());
            // chaoyang.lin modify begin
            contextVisited.add(bc.getIdentifier());
            // chaoyang.lin modify end
            List<BackwardContext> newBcs = bc.oneStepBackWard();
            for (BackwardContext newBc : newBcs) {
                // chaoyang.lin modify begin
                if (!newBc.equals(bc) && !contextVisited.contains(newBc.getIdentifier()))
                    bcs.add(newBc);
                // chaoyang.lin modify end
            }
//			System.out.println("Finished: " + bc.backWardHasFinished());
//			System.out.println("Size: " + bcs.size());
//			System.out.println("======================= END ===========================");
        }

        return bcs;

    }

}
