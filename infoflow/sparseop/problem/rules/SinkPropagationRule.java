package soot.jimple.infoflow.sparseop.problem.rules;

import heros.solver.Pair;
import soot.SootMethod;
import soot.Value;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AbstractionAtSink;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.util.BaseSelector;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.Collection;

/**
 * Rule for recording abstractions that arrive at sinks
 * 
 * @author Steven Arzt
 */
public class SinkPropagationRule extends AbstractTaintPropagationRule {
	
	private boolean killState = false;

	public SinkPropagationRule(InfoflowManager manager, Aliasing aliasing,
			Abstraction zeroValue, TaintPropagationResults results) {
		super(manager, aliasing, zeroValue, results);
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateNormalFlow(Abstraction d1,
																		   Abstraction source, Stmt stmt, Stmt destStmt,
																		   ByReferenceBoolean killSource,
																		   ByReferenceBoolean killAll) {
		if (stmt instanceof ReturnStmt) {
			final ReturnStmt returnStmt = (ReturnStmt) stmt;
			checkForSink(d1, source, stmt, returnStmt.getOp());
		}
		else if (stmt instanceof IfStmt) {
			final IfStmt ifStmt = (IfStmt) stmt;
			checkForSink(d1, source, stmt, ifStmt.getCondition());
		}
		else if (stmt instanceof LookupSwitchStmt) {
			final LookupSwitchStmt switchStmt = (LookupSwitchStmt) stmt;
			checkForSink(d1, source, stmt, switchStmt.getKey());
		}
		else if (stmt instanceof TableSwitchStmt) {
			final TableSwitchStmt switchStmt = (TableSwitchStmt) stmt;
			checkForSink(d1, source, stmt, switchStmt.getKey());
		}
		else if (stmt instanceof AssignStmt) {
			final AssignStmt assignStmt = (AssignStmt) stmt;
			checkForSink(d1, source, stmt, assignStmt.getRightOp());
		}
		
		return null;
	}
	
	/**
	 * Checks whether the given taint abstraction at the given satement triggers
	 * a sink. If so, a new result is recorded
	 * @param d1 The context abstraction
	 * @param source The abstraction that has reached the given statement
	 * @param stmt The statement that was reached
	 * @param retVal The value to check
	 */
	private void checkForSink(Abstraction d1, Abstraction source, Stmt stmt,
			final Value retVal) {
		// The incoming value may be a complex expression. We have to look at
		// every simple value contained within it.
		for (Value val : BaseSelector.selectBaseList(retVal, false)) {
			if (getManager().getSourceSinkManager() != null
					&& source.isAbstractionActive()
					&& getAliasing().mayAlias(val, source
							.getAccessPath().getPlainValue())
					&& getManager().getSourceSinkManager().isSink(stmt,
							getManager().getICFG(), source.getAccessPath()))
				if (!getResults().addResult(new AbstractionAtSink(source, stmt)))
					killState = true;
		}
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateCallFlow(Abstraction d1,
			Abstraction source, Stmt stmt, SootMethod dest,
			ByReferenceBoolean killAll) {
		// If we are in the kill state, we stop the analysis
		if (killAll != null)
			killAll.value |= killState;		

		return null;
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateCallToReturnFlow(Abstraction d1,
			Abstraction source, Stmt stmt, ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		// The given access path must at least be referenced somewhere in the
		// sink
		if (source.isAbstractionActive()
				&& !source.getAccessPath().isStaticFieldRef()) {
			InvokeExpr iexpr = stmt.getInvokeExpr();
			boolean found = false;
			for (int i = 0; i < iexpr.getArgCount(); i++)
				if (getAliasing().mayAlias(iexpr.getArg(i),
						source.getAccessPath().getPlainValue())) {
					if (source.getAccessPath().getTaintSubFields()
							|| source.getAccessPath().isLocal()) {
						found = true;
						break;
					}
				}
			if (!found && iexpr instanceof InstanceInvokeExpr)
				if (((InstanceInvokeExpr) iexpr).getBase() == source
						.getAccessPath().getPlainValue())
					found = true;

			// Is this a call to a sink?
			if (found
					&& getManager().getSourceSinkManager() != null
					&& getManager().getSourceSinkManager().isSink(stmt,
							getManager().getICFG(), source.getAccessPath())) {
				if (!getResults().addResult(new AbstractionAtSink(source, stmt)))
					killState = true;
			}
		}

		// If we are in the kill state, we stop the analysis
		if (killAll != null)
			killAll.value |= killState;
		
		return null;
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateReturnFlow(
			Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt,
			Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
		// Check whether this return is treated as a sink
		if (stmt instanceof ReturnStmt) {
			final ReturnStmt returnStmt = (ReturnStmt) stmt;
			boolean matches = source.getAccessPath().isLocal()
					|| source.getAccessPath().getTaintSubFields();
			if (matches
					&& source.isAbstractionActive()
					&& getManager().getSourceSinkManager() != null
					&& getAliasing().mayAlias(source.getAccessPath().getPlainValue(),
							returnStmt.getOp())
					&& getManager().getSourceSinkManager().isSink(returnStmt,
							getManager().getICFG(), source.getAccessPath()))
				if (!getResults().addResult(new AbstractionAtSink(source, returnStmt)))
					killState = true;
		}
		
		// If we are in the kill state, we stop the analysis
		if (killAll != null)
			killAll.value |= killState;
		
		return null;
	}

}
