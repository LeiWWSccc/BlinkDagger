package soot.jimple.infoflow.sparseop.problem.rules;

import heros.solver.Pair;
import soot.SootMethod;
import soot.ValueBox;
import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.source.SourceInfo;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.infoflow.util.TypeUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Rule to introduce unconditional taints at sources
 * 
 * @author Steven Arzt
 *
 */
public class SourcePropagationRule extends AbstractTaintPropagationRule {

	public SourcePropagationRule(InfoflowManager manager, Aliasing aliasing,
			Abstraction zeroValue, TaintPropagationResults results) {
		super(manager, aliasing, zeroValue, results);
	}

	private Collection<Pair<DataFlowNode, Abstraction>> propagate(Abstraction d1,
			Abstraction source, Stmt stmt, ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		if (source == getZeroValue()) {
			// Check whether this can be a source at all
			final SourceInfo sourceInfo = getManager().getSourceSinkManager() != null
					? getManager().getSourceSinkManager().getSourceInfo(stmt, getManager().getICFG()) : null;
					
			// We never propagate zero facts onwards
			killSource.value = true;

			//debugdebug
//			if(stmt.toString().equals("$r12 = virtualinvoke $r11.<java.net.URL: java.net.URLConnection openConnection()>()")) {
//				SootMethod m = getManager().getICFG().getMethodOf(stmt);
//				int count = 1;
//			}
			
			// Is this a source?
			if (sourceInfo != null && !sourceInfo.getAccessPaths().isEmpty()) {
				Set<Pair<DataFlowNode, Abstraction>> res = new HashSet<>();
				Set<Abstraction> resAbs = new HashSet<>();
				for (AccessPath ap : sourceInfo.getAccessPaths()) {
					// Create the new taint abstraction

					DataFlowNode dfg = useApTofindDataFlowGraph(ap, stmt);

					Abstraction abs = new Abstraction(ap,
							stmt,
							sourceInfo.getUserData(),
							false,
							false);
					int count;
					resAbs.add(abs);
					if(dfg == null)
						throw new RuntimeException("Source AccessPath cant find the relative Dfg, it should be built before using! ");
//					if(dfg.getSuccs() == null){
//						System.out.println(getManager().getICFG().getMethodOf(stmt).getActiveBody());
//						System.out.println(getManager().getICFG().getMethodOf(stmt));
//					}
					if(dfg.getSuccs() != null) {
						Set<DataFlowNode> nextSet = dfg.getSuccs().get(dfg.getField());
						if(nextSet == null)
							count = 1;
						for(DataFlowNode next : nextSet) {
							res.add(new Pair<>(next, abs));
						}
					}

					
					// Compute the aliases
					for (ValueBox vb : stmt.getUseAndDefBoxes()) {
						if (ap.startsWith(vb.getValue())) {
							// We need a relaxed "can have aliases" check here. Even if we have
							// a local, the source/sink manager is free to taint the complete local
							// while keeping alises valid (no overwrite).
							// The startsWith() above already gets rid of constants, etc.
							if (!TypeUtils.isStringType(vb.getValue().getType())
									|| ap.getCanHaveImmutableAliases())
								getAliasing().computeAliases(d1, stmt, vb.getValue(),
										resAbs, getManager().getICFG().getMethodOf(stmt), abs);
						}
					}
					
					// Set the corresponding call site
					if (stmt.containsInvokeExpr())
						abs.setCorrespondingCallSite(stmt);
				}
				return res;
			}
			if (killAll != null)
				killAll.value = true;
		}
		return null;
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateNormalFlow(Abstraction d1,
			Abstraction source, Stmt stmt, Stmt destStmt,
			ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		return propagate(d1, source, stmt, killSource, killAll);
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateCallToReturnFlow(Abstraction d1,
			Abstraction source, Stmt stmt, ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		return propagate(d1, source, stmt, killSource, null);
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateReturnFlow(
			Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt,
			Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateCallFlow(Abstraction d1,
																		 Abstraction source, Stmt stmt, SootMethod dest,
																		 ByReferenceBoolean killAll) {
		// Normally, we don't inspect source methods
		if (!getManager().getConfig().getInspectSources()
				&& getManager().getSourceSinkManager() != null) {
			final SourceInfo sourceInfo = getManager().getSourceSinkManager().getSourceInfo(
					stmt, getManager().getICFG());
			if (sourceInfo != null)
				killAll.value = true;
		}
		
		// By default, we don't inspect sinks either
		if (!getManager().getConfig().getInspectSinks()
				&& getManager().getSourceSinkManager() != null) {
			final boolean isSink = getManager().getSourceSinkManager().isSink(
					stmt, getManager().getICFG(), source.getAccessPath());
			if (isSink)
				killAll.value = true;
		}
		
		return null;
	}



}
