package soot.jimple.infoflow.sparseop.solver.heros;


import heros.solver.Pair;

import java.util.Set;

public interface FlowFunctionOp<F, D> {

    /**
     * Returns the target values reachable from the source.
     */
    Set<Pair<F, D>> computeTargets(D source);
}
