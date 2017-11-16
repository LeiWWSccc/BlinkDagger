package soot.jimple.infoflow.sparseop.Aliasing;

import heros.solver.Pair;
import heros.solver.PathEdge;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.AbstractBulkAliasStrategy;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.dataflowgraph.InnerBBFastBuildDFGSolver;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A fully flow-sensitive aliasing strategy
 * 
 * @author Steven Arzt
 */
public class FlowSensitiveAliasStrategyOp extends AbstractBulkAliasStrategy {

	private final IInfoflowSolver bSolver;

	protected final InfoflowManager manager;

	public FlowSensitiveAliasStrategyOp(InfoflowManager manager, IInfoflowCFG cfg, IInfoflowSolver backwardsSolver) {
		super(cfg);
		this.manager = manager;
		this.bSolver = backwardsSolver;
	}

	@Override
	public void computeAliasTaints
			(final Abstraction d1, final Stmt src,
			final Value targetValue, Set<Abstraction> taintSet,
			SootMethod method, Abstraction newAbs) {
		// Start the backwards solver



		Abstraction bwAbs = newAbs.deriveInactiveAbstraction(src);

		for(Unit predUnit : getNextStmtFromDfg(targetValue, src, bwAbs))
			bSolver.processEdge(new PathEdge<Unit, Abstraction>(d1,
					predUnit, bwAbs));

//		for (Unit predUnit : interproceduralCFG().getPredsOf(src))
//			bSolver.processEdge(new PathEdge<Unit, Abstraction>(d1,
//					predUnit, bwAbs));


	}


	private Set<Pair<DataFlowNode, Abstraction>> getNextResFromBackwardDfg(Value value , Unit stmt, Abstraction abs) {
		Set<Pair<DataFlowNode, Abstraction>> res = new HashSet<>();
		DataFlowNode dataFlowNode = useValueTofindDataFlowGraph(value, stmt, false);
		AccessPath ap = abs.getAccessPath();
		SootField firstField = ap.getFirstField();
		if(dataFlowNode.getSuccs() != null) {
			Set<DataFlowNode> next = dataFlowNode.getSuccs().get(DataFlowNode.baseField);
			if(next != null)
				for(DataFlowNode d : next) {
					res.add(new Pair<DataFlowNode, Abstraction>(d, abs));
				}

			if(firstField != null) {
				Set<DataFlowNode> next1 = dataFlowNode.getSuccs().get(firstField);
				if(next1 != null)
					for(DataFlowNode d : next1) {
						res.add(new Pair<DataFlowNode, Abstraction>(d, abs));
					}
			}
		}
		return res;
	}

	private Set<Unit> getNextStmtFromDfg(Value value , Unit stmt, Abstraction abs) {
		Set<Unit> res = new HashSet<>();
		DataFlowNode dataFlowNode = useValueTofindDataFlowGraph(value, stmt, false);
		AccessPath ap = abs.getAccessPath();
		SootField firstField = ap.getFirstField();
		if(dataFlowNode != null && dataFlowNode.getSuccs() != null) {
			Set<DataFlowNode> next = dataFlowNode.getSuccs().get(DataFlowNode.baseField);
			if(next != null)
				for(DataFlowNode d : next) {
					res.add(d.getStmt());
				}

			if(firstField != null) {
				Set<DataFlowNode> next1 = dataFlowNode.getSuccs().get(firstField);
				if(next1 != null)
					for(DataFlowNode d : next1) {
						res.add(d.getStmt());
					}
			}
		}
		return res;
	}

	private DataFlowNode useValueTofindDataFlowGraph(Value value, Unit stmt, boolean isforward) {
		SootMethod caller = manager.getICFG().getMethodOf(stmt);
		Pair<Value, SootField> pair = InnerBBFastBuildDFGSolver.getBaseAndField(value);
		return useBaseAndFieldTofindDataFlowGraph(pair.getO1(), pair.getO2(), stmt, isforward);
	}

	private DataFlowNode useBaseAndFieldTofindDataFlowGraph(Value base, SootField field, Unit stmt, boolean isforward ) {
		Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg;
		if(isforward)
			dfg = manager.getDfg();
		else
			dfg = manager.getBackwardDfg();

		SootMethod caller = manager.getICFG().getMethodOf(stmt);
		if(field == null)
			field = DataFlowNode.baseField;

		DFGEntryKey key = new DFGEntryKey(stmt, base, field);

		if(!dfg.containsKey(caller)) return null;
		Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> l1 = dfg.get(caller);
		if(!l1.containsKey(base)) return null;
		Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2 = l1.get(base);
		if(!l2.containsKey(key)) return null;
		Pair<BaseInfoStmt, DataFlowNode> pair = l2.get(key);
		if(pair == null) return null;

		return pair.getO2();
	}



	@Override
	public void injectCallingContext(Abstraction d3, IInfoflowSolver fSolver,
			SootMethod callee, Unit callSite, Abstraction source, Abstraction d1) {
		bSolver.injectContext(fSolver, callee, d3, callSite, source, d1);
	}

	@Override
	public boolean isFlowSensitive() {
		return true;
	}

	@Override
	public boolean requiresAnalysisOnReturn() {
		return false;
	}
	
}
