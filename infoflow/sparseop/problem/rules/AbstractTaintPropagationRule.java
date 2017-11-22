package soot.jimple.infoflow.sparseop.problem.rules;

import heros.solver.Pair;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;

import java.util.Map;

/**
 * Abstract base class for all taint propagation rules
 * 
 * @author Steven Arzt
 *
 */
public abstract class AbstractTaintPropagationRule implements
        ITaintPropagationRule {
	
	private final InfoflowManager manager;
	private final Aliasing aliasing;
	private final Abstraction zeroValue;
	private final TaintPropagationResults results;
	
	public AbstractTaintPropagationRule(InfoflowManager manager,
			Aliasing aliasing, Abstraction zeroValue,
			TaintPropagationResults results) {
		this.manager = manager;
		this.aliasing = aliasing;
		this.zeroValue = zeroValue;
		this.results = results;
	}
	
	protected InfoflowManager getManager() {
		return this.manager;
	}
	
	protected Aliasing getAliasing() {
		return this.aliasing;
	}
	
	protected Abstraction getZeroValue() {
		return this.zeroValue;
	}
	
	protected TaintPropagationResults getResults() {
		return this.results;
	}

	protected DataFlowNode useApTofindDataFlowGraph(AccessPath ap, Unit stmt) {
		SootMethod caller = getManager().getICFG().getMethodOf(stmt);
		Value base = ap.getPlainValue();
		SootField field1 = ap.getFirstField();
		if(field1 == null)
			field1 = DataFlowNode.baseField;

		DFGEntryKey key = new DFGEntryKey(stmt, base, field1);

		if(!getManager().getDfg().containsKey(caller)) return null;
		Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> l1 = getManager().getDfg().get(caller);
		if(!l1.containsKey(base)) return null;
		Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2 = l1.get(base);
		if(!l2.containsKey(key)) return null;
		Pair<BaseInfoStmt, DataFlowNode> pair = l2.get(key);
		if(pair == null) return null;

		return pair.getO2();
	}


	protected DataFlowNode useBaseTofindDataFlowGraph(Value base, Unit stmt) {
		SootMethod caller = getManager().getICFG().getMethodOf(stmt);
		SootField field1 = DataFlowNode.baseField;

		DFGEntryKey key = new DFGEntryKey(stmt, base, field1);

		if(!getManager().getDfg().containsKey(caller)) return null;
		Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> l1 = getManager().getDfg().get(caller);
		if(!l1.containsKey(base)) return null;
		Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2 = l1.get(base);
		if(!l2.containsKey(key)) return null;
		Pair<BaseInfoStmt, DataFlowNode> pair = l2.get(key);
		if(pair == null) return null;

		return pair.getO2();
	}


}
