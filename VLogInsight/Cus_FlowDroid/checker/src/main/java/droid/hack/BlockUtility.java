package droid.hack;

import soot.Unit;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.CompleteBlockGraph;

import java.util.Iterator;

public class BlockUtility {

    public static Block findLocatedBlock(CompleteBlockGraph cbg, Unit unit) {
        // TODO Auto-generated method stub

        for (Block block : cbg.getBlocks()) {
            Iterator<Unit> us = block.iterator();
            while (us.hasNext()) {
                if (us.next() == unit) {
                    return block;
                }
            }
        }
        return null;

    }

}
