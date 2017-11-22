package soot.jimple.infoflow.sparseop.solver.functions;

import heros.solver.Pair;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.solver.heros.FlowFunctionOp;

import java.util.Set;

/**
 * A special implementation of the normal flow function that allows access to
 * the fact associated with the method's start point (i.e. the current context).
 *  
 * @author Steven Arzt
 */
public abstract class SolverNormalFlowFunctionOp implements FlowFunctionOp<DataFlowNode, Abstraction> {

	@Override
	public Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction source) {
		return computeTargets(null, source);
	}

	/**
	 * Computes the abstractions at the next node in the CFG.
	 * @param d1 The abstraction at the beginning of the current method, i.e.
	 * the context
	 * @param d2 The abstraction at the current node
	 * @return The set of abstractions at the next node
	 */
	public abstract Set<Pair<DataFlowNode,Abstraction>> computeTargets(Abstraction d1, Abstraction d2);
	
}
