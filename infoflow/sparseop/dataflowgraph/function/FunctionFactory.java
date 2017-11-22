package soot.jimple.infoflow.sparseop.dataflowgraph.function;

import heros.solver.Pair;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;

import java.util.Map;

/**
 * @author wanglei
 */
public class FunctionFactory {

    public static AbstractFunction getFunction(boolean isForward, Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > visited) {
        if(isForward)
            return new ForwardFunction(visited);
        else
            return new BackwardFunction(visited);
    }
//
//    public static AbstractFunction getFunction(boolean isForward, Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > backwardSeed , Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > visited) {
//        if(isForward)
//            return new ForwardFunction(visited, backwardSeed);
//        else
//            return new BackwardFunction(visited);
//    }
}
