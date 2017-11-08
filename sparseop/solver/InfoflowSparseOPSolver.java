/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.sparseop.solver;

import heros.solver.CountingThreadPoolExecutor;
import heros.solver.Pair;
import heros.solver.PathEdge;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.solver.IFollowReturnsPastSeedsHandler;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.functions.SolverCallToReturnFlowFunction;
import soot.jimple.infoflow.solver.functions.SolverNormalFlowFunction;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.sparseop.problem.AbstractInfoflowProblemOp;
import soot.jimple.infoflow.sparseop.solver.functions.SolverCallToReturnFlowFunctionOp;
import soot.jimple.infoflow.sparseop.solver.functions.SolverNormalFlowFunctionOp;
import soot.jimple.infoflow.sparseop.solver.heros.FlowFunctionOp;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

import java.util.Map;
import java.util.Set;

/**
 * We are subclassing the JimpleIFDSSolver because we need the same executor for both the forward and the backward analysis
 * Also we need to be able to insert edges containing new taint information
 * 
 */
public class InfoflowSparseOPSolver extends IFDSSpSolver<Unit,DataFlowNode , Abstraction, SootMethod, BiDiInterproceduralCFG<Unit, SootMethod>>
		implements IInfoflowSolver {

	//op
	private  Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg ;

	private IFollowReturnsPastSeedsHandler followReturnsPastSeedsHandler = null;

	public InfoflowSparseOPSolver(AbstractInfoflowProblemOp problem, CountingThreadPoolExecutor executor) {
		super(problem);
		this.executor = executor;
		problem.setSolver(this);		
	}
	
	@Override
	protected CountingThreadPoolExecutor getExecutor() {
		return executor;
	}

	@Override
	public boolean processEdge(PathEdge<Unit, Abstraction> edge){
		propagate(edge.factAtSource(), edge.getTarget(), edge.factAtTarget(), null, false, true);
		return true;
	}
	
	@Override
	public void injectContext(IInfoflowSolver otherSolver, SootMethod callee,
			Abstraction d3, Unit callSite, Abstraction d2, Abstraction d1) {
		addIncoming(callee, d3, callSite, d1, d2);
	}
	
//	@Override
//	protected Set<Abstraction> computeReturnFlowFunction(
//			FlowFunction<Abstraction> retFunction,
//			Abstraction d1,
//			Abstraction d2,
//			Unit callSite,
//			Collection<Abstraction> callerSideDs) {
//		if (retFunction instanceof SolverReturnFlowFunction) {
//			// Get the d1s at the start points of the caller
//			return ((SolverReturnFlowFunction) retFunction).computeTargets(d2, d1, callerSideDs);
//		}
//		else
//			return retFunction.computeTargets(d2);
//	}

	@Override
	protected Set<Pair<DataFlowNode, Abstraction>> computeNormalFlowFunction
			(FlowFunctionOp<DataFlowNode, Abstraction> flowFunction, Abstraction d1, Abstraction d2) {
		if (flowFunction instanceof SolverNormalFlowFunction)
			return ((SolverNormalFlowFunctionOp) flowFunction).computeTargets(d1, d2);
		else
			return flowFunction.computeTargets(d2);
	}

	@Override
	protected Set<Pair<DataFlowNode, Abstraction>> computeCallToReturnFlowFunction
			(FlowFunctionOp<DataFlowNode, Abstraction> flowFunction, Abstraction d1, Abstraction d2) {
		if (flowFunction instanceof SolverCallToReturnFlowFunction)
			return ((SolverCallToReturnFlowFunctionOp) flowFunction).computeTargets(d1, d2);
		else
			return flowFunction.computeTargets(d2);		
	}

//	@Override
//	protected Set<Abstraction> computeCallFlowFunction
//			(FlowFunction<Abstraction> flowFunction, Abstraction d1, Abstraction d2) {
//		if (flowFunction instanceof SolverCallFlowFunction)
//			return ((SolverCallFlowFunction) flowFunction).computeTargets(d1, d2);
//		else
//			return flowFunction.computeTargets(d2);
//	}
	
	@Override
	public void cleanup() {
		this.jumpFn.clear();
		this.incoming.clear();
		this.endSummary.clear();
	}
	
	@Override
	public Set<Pair<Unit, Abstraction>> endSummary(SootMethod m, Abstraction d3) {
		return super.endSummary(m, d3);
	}
	
//	@Override
//	protected void processExit(PathEdge<Unit, Abstraction> edge) {
//		super.processExit(edge);
//
//		if (followReturnsPastSeeds && followReturnsPastSeedsHandler != null) {
//			final Abstraction d1 = edge.factAtSource();
//			final Unit u = edge.getTarget();
//			final Abstraction d2 = edge.factAtTarget();
//
//			final SootMethod methodThatNeedsSummary = icfg.getMethodOf(u);
//			final Map<Unit, Map<Abstraction, Abstraction>> inc = incoming(d1, methodThatNeedsSummary);
//
//			if (inc == null || inc.isEmpty())
//				followReturnsPastSeedsHandler.handleFollowReturnsPastSeeds(d1, u, d2);
//		}
//	}
	
	@Override
	public void setFollowReturnsPastSeedsHandler(IFollowReturnsPastSeedsHandler handler) {
		this.followReturnsPastSeedsHandler = handler;
	}



	protected void propagate(Abstraction sourceVal,DataFlowNode dataFlowNode, Unit target, Abstraction targetVal,
			/* deliberately exposed to clients */ Unit relatedCallSite,
			/* deliberately exposed to clients */ boolean isUnbalancedReturn,
							 boolean forceRegister) {

		if(targetVal == zeroValue) {
			super.propagate(sourceVal, target, targetVal, relatedCallSite, isUnbalancedReturn);
			return;
		}


		if(dataFlowNode == null)
			return ;
		super.propagate(sourceVal, dataFlowNode.getStmt(), targetVal, relatedCallSite , isUnbalancedReturn, forceRegister);
//		HashMap<SootField, Set<DataFlowNode>> nextInfo = dataFlowNode.getSuccs();
//		if(nextInfo == null)
//			return;
//		for(DataFlowNode next : nextInfo.get(DataFlowNode.baseField)) {
//			super.propagate(sourceVal, next.getStmt(), targetVal, relatedCallSite , isUnbalancedReturn);
//		}


	}

//	protected void propagate(Abstraction sourceVal, Unit target, Abstraction targetVal,
//			/* deliberately exposed to clients */ Unit relatedCallSite,
//			/* deliberately exposed to clients */ boolean isUnbalancedReturn,
//							 boolean forceRegister) {
//
//		if(targetVal == zeroValue) {
//			super.propagate(sourceVal, target, targetVal, relatedCallSite, isUnbalancedReturn, forceRegister);
//			return;
//		}
//
//		AccessPath ap = targetVal.getAccessPath();
//		DataFlowNode dataFlowNode = useApTofindDataFlowGraph(ap, relatedCallSite);
//		if(dataFlowNode == null)
//			return ;
//		HashMap<SootField, Set<DataFlowNode>> nextInfo = dataFlowNode.getSuccs();
//		if(nextInfo == null)
//			return;
//		for(DataFlowNode next : nextInfo.get(DataFlowNode.baseField)) {
//			super.propagate(sourceVal, next.getStmt(), targetVal, relatedCallSite , isUnbalancedReturn, forceRegister);
//		}
//	}

//
//	public DataFlowNode useApTofindDataFlowGraph(AccessPath ap, Unit stmt) {
//		SootMethod caller = icfg.getMethodOf(stmt);
//		Value base = ap.getPlainValue();
//		SootField field1 = ap.getFirstField();
//		if(!dfg.containsKey(caller)) return null;
//		Map<Value, Map<DFGEntryKey, Pair<VariableInfo, DataFlowNode>>> l1 = dfg.get(caller);
//		if(!l1.containsKey(base)) return null;
//		Map<DFGEntryKey, Pair<VariableInfo, DataFlowNode>> l2 = l1.get(base);
//		if(!l2.containsKey(new DFGEntryKey(stmt, base, field1))) return null;
//		Pair<VariableInfo, DataFlowNode> pair = l2.get(new Pair<Unit, Value>(stmt, base));
//		if(pair == null) return null;
//
//		return pair.getO2();
//	}

	public void setDfg(Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> g) {
		this.dfg = g;
	}


}
