package soot.jimple.infoflow.sparseop.basicblock;

import soot.Unit;

import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public class UnitOrderComputing {

    private final Map<Unit, BasicBlock> unitToBBMap ;
    final private Map<Unit, Integer> unitToInnerBBIndexMap ;
    final private Map<BasicBlock, Set<BasicBlock>> bbOrderMap ;

    public UnitOrderComputing (Map<Unit, BasicBlock> unitToBBMap,  Map<Unit, Integer> unitToInnerBBIndexMap,
                       Map<BasicBlock, Set<BasicBlock>> bbOrderMap) {
        this.unitToBBMap = unitToBBMap;
        this.unitToInnerBBIndexMap = unitToInnerBBIndexMap;
        this.bbOrderMap = bbOrderMap;
    }


    public  boolean computeOrder(Unit u1, Unit u2) {

        //source or start of the method
        if(u1 == null)
            return true;

        BasicBlock bb1 = unitToBBMap.get(u1);
        BasicBlock bb2 = unitToBBMap.get(u2);
        if(bb1 == bb2) {
            int idx1 = unitToInnerBBIndexMap.get(u1);
            int idx2 = unitToInnerBBIndexMap.get(u2);
            return idx1 <= idx2;
        }else {
            if(bbOrderMap.get(bb1).contains(bb2))
                return true;
        }
        return false;

    }

}
