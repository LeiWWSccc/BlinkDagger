package soot.jimple.infoflow.sparseop.solver.functions;

import heros.solver.Pair;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.solver.heros.FlowFunctionOp;

import java.util.Set;

/**
 * A special implementation of the call-to-return flow function that allows
 * access to the fact associated with the method's start point (i.e. the
 * current context).
 *  
 * @author Steven Arzt
 */
public abstract class SolverCallToReturnFlowFunctionOp implements FlowFunctionOp<DataFlowNode, Abstraction> {

	@Override
	public Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction source) {
		return computeTargets(null, source);
	}

	/**
	 * Computes the abstractions at the return site
	 * @param d1 The abstraction at the beginning of the caller, i.e. the
	 * context in which the method call is made
	 * @param d2 The abstraction at the call site
	 * @return The set of abstractions at the first node inside the callee
	 */
	public abstract Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction d1, Abstraction d2);
	
}
