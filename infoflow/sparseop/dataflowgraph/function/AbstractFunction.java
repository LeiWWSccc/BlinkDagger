package soot.jimple.infoflow.sparseop.dataflowgraph.function;

import heros.solver.Pair;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;

import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public abstract class AbstractFunction {


    Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > jumpFunc ;

    AbstractFunction(Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited) {
        this.jumpFunc = visited;
    }

    public abstract Set<Pair<BaseInfoStmt, DataFlowNode>>  flowFunction(
            BaseInfoStmt target, DataFlowNode source);

    protected DataFlowNode getNewDataFlowNode(BaseInfoStmt baseInfoStmt, DataFlowNode oldNode) {
        Pair<BaseInfoStmt, DataFlowNode> key = new Pair<>(baseInfoStmt, oldNode);
        if(jumpFunc.containsKey(key))
            return jumpFunc.get(key);
        else
            return oldNode;

    }

    protected void addResult( Set<Pair<BaseInfoStmt, DataFlowNode>>  res, BaseInfoStmt target , DataFlowNode newDfn) {
        Pair<BaseInfoStmt, DataFlowNode> path = new Pair<BaseInfoStmt, DataFlowNode>(target, newDfn);
        if(this.jumpFunc.containsKey(path))
            return;
        jumpFunc.put(path, newDfn);
        res.add(path);
    }

    public Map<DFGEntryKey, Set<DataFlowNode>> getReturnInfo() {
        return null;
    }
}
