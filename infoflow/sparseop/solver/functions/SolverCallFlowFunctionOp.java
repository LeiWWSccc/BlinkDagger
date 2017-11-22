package soot.jimple.infoflow.sparseop.solver.functions;

import heros.solver.Pair;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.solver.heros.FlowFunctionOp;

import java.util.Set;

/**
 * A special implementation of the call flow function that allows
 * access to the fact associated with the method's start point (i.e. the
 * current context).
 *  
 * @author Steven Arzt
 */
public abstract class SolverCallFlowFunctionOp implements FlowFunctionOp<DataFlowNode, Abstraction> {

	@Override
	public Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction source) {
		return computeTargets(null, source);
	}

	/**
	 * Computes the call flow function for the given call-site abstraction
	 * @param d1 The abstraction at the current method's start node.
	 * @param d2 The abstraction at the call site
	 * @return The set of caller-side abstractions at the callee's start node
	 */
	public abstract Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction d1, Abstraction d2);
	
}
