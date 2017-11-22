package soot.jimple.infoflow.sparseop.solver;

import heros.solver.CountingThreadPoolExecutor;
import heros.solver.Pair;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.problem.AbstractInfoflowProblemOp;
import soot.jimple.infoflow.sparseop.solver.heros.FlowFunctionOp;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class BackwardsInfoflowSpSolver extends InfoflowSparseOPSolver {
	public BackwardsInfoflowSpSolver(AbstractInfoflowProblemOp problem, CountingThreadPoolExecutor executor) {
		super(problem, executor);
	}

	@Override
	public void injectContext(IInfoflowSolver otherSolver, SootMethod callee,
			Abstraction d3, Unit callSite, Abstraction d2, Abstraction d1) {
		if(!this.addIncoming(callee, d3, callSite, d1, d2)) {
			return;
		}
		
		Set<Pair<Unit, Abstraction>> endSumm = endSummary(callee, d3);		
		if (endSumm != null) {
			Collection<Unit> returnSiteNs = icfg.getReturnSitesOfCallAt(callSite);
			for(Pair<Unit, Abstraction> entry: endSumm) {
				Unit eP = entry.getO1();
				Abstraction d4 = entry.getO2();
				//for each return site
				for(Unit retSiteN: returnSiteNs) {
					//compute return-flow function
					FlowFunctionOp<DataFlowNode, Abstraction> retFunction = flowFunctions.getReturnFlowFunction(callSite, callee, eP, retSiteN);
					//for each target value of the function
					for(Pair<DataFlowNode, Abstraction> pair: computeReturnFlowFunction(retFunction, d3, d4, callSite, Collections.singleton(d1))) {
						DataFlowNode dfg = pair.getO1();
						Abstraction d5 = pair.getO2();

						if (memoryManager != null)
							d5 = memoryManager.handleGeneratedMemoryObject(d4, d5);

						// If we have not changed anything in the callee, we do not need the facts
						// from there. Even if we change something: If we don't need the concrete
						// path, we can skip the callee in the predecessor chain
						Abstraction d5p = d5;
						if (d5.equals(d2))
							d5p = d2;
						else if (setJumpPredecessors && d5p != d2) {
							d5p = d5p.clone();
							d5p.setPredecessor(d2);
						}
						propagate(d1,dfg , retSiteN, d5p, callSite, false, true);
					}
				}
			}
		}
	}
}
