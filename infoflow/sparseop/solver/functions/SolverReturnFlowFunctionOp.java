package soot.jimple.infoflow.sparseop.solver.functions;

import heros.solver.Pair;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.solver.heros.FlowFunctionOp;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * A special implementation of the return flow function that allows access to
 * the facts associated with the start points of the callees (i.e. the contexts
 * to which we return).
 *  
 * @author Steven Arzt
 */
public abstract class SolverReturnFlowFunctionOp implements FlowFunctionOp<DataFlowNode, Abstraction> {
		
	@Override
	public Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction source) {
		return computeTargets(source, null, Collections.<Abstraction>emptySet());
	}

	/**
	 * Computes the abstractions at the return site.
	 * @param source The abstraction at the exit node
	 * @param calleeD1 The abstraction at the start point of the callee
	 * @param callerD1s The abstractions at the start nodes of all methods to
	 * which we return (i.e. the contexts to which this flow function will be
	 * applied).
	 * @return The set of abstractions at the return site.
	 */
	public abstract Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction source,
			Abstraction calleeD1,
			Collection<Abstraction> callerD1s);
	
}
