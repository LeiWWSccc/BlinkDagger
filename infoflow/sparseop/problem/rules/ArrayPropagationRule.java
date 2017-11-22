package soot.jimple.infoflow.sparseop.problem.rules;

import heros.solver.Pair;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.AccessPath.ArrayTaintType;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.dataflowgraph.InnerBBFastBuildDFGSolver;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.util.ByReferenceBoolean;

import java.util.*;

/**
 * Rule for propagating array accesses
 * 
 * @author Steven Arzt
 *
 */
public class ArrayPropagationRule extends AbstractTaintPropagationRule {

	public ArrayPropagationRule(InfoflowManager manager, Aliasing aliasing,
			Abstraction zeroValue, TaintPropagationResults results) {
		super(manager, aliasing, zeroValue, results);
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateNormalFlow(Abstraction d1,
																		   Abstraction source, Stmt stmt, Stmt destStmt,
																		   ByReferenceBoolean killSource,
																		   ByReferenceBoolean killAll) {
		// Get the assignment
		if (!(stmt instanceof AssignStmt))
			return null;
		AssignStmt assignStmt = (AssignStmt) stmt;
		
		Abstraction newAbs = null;
		Set<Pair<DataFlowNode,Abstraction>> newRes = null;
		final Value leftVal = assignStmt.getLeftOp();
		final Value rightVal = assignStmt.getRightOp();
		
		if (rightVal instanceof LengthExpr) {
			LengthExpr lengthExpr = (LengthExpr) rightVal;
			if (getAliasing().mayAlias(source.getAccessPath().getPlainValue(), lengthExpr.getOp())) {
				// Is the length tainted? If only the contents are tainted, we the
				// incoming abstraction does not match
				if (source.getAccessPath().getArrayTaintType() == ArrayTaintType.Contents)
					return null;
				
				// Taint the array length
				AccessPath ap = AccessPathFactory.v().createAccessPath(assignStmt,
						leftVal, null, IntType.v(),
						(Type[]) null, true, false, true, ArrayTaintType.ContentsAndLength);
				newAbs = source.deriveNewAbstraction(ap, assignStmt);
			}
		}
		//y = x[i] && x tainted -> x, y tainted
		else if (rightVal instanceof ArrayRef) {
			Value rightBase = ((ArrayRef) rightVal).getBase();
			Value rightIndex = ((ArrayRef) rightVal).getIndex();
			if (source.getAccessPath().getArrayTaintType() != ArrayTaintType.Length
					&& getAliasing().mayAlias(rightBase, source.getAccessPath().getPlainValue())) {
				// We must remove one layer of array typing, e.g., A[][] -> A[]
				Type targetType = source.getAccessPath().getBaseType();
				assert targetType instanceof ArrayType;
				targetType = ((ArrayType) targetType).getElementType();
				
				// Create the new taint abstraction
				ArrayTaintType arrayTaintType = source.getAccessPath().getArrayTaintType();
				newAbs = source.deriveNewAbstraction(leftVal,
						false, assignStmt, targetType, arrayTaintType);
			}
			
			// y = x[i] with i tainted
			else if (source.getAccessPath().getArrayTaintType() != ArrayTaintType.Length
					&& rightIndex == source.getAccessPath().getPlainValue()
					&& getManager().getConfig().getEnableImplicitFlows()) {
				// Create the new taint abstraction
				ArrayTaintType arrayTaintType = ArrayTaintType.ContentsAndLength;
				newAbs = source.deriveNewAbstraction(leftVal,
						false, assignStmt, null, arrayTaintType);

			}
		}
		// y = new A[i] with i tainted
		else if (rightVal instanceof NewArrayExpr
				&& getManager().getConfig().getEnableArraySizeTainting()) {
			NewArrayExpr newArrayExpr = (NewArrayExpr) rightVal;
			if (getAliasing().mayAlias(source.getAccessPath().getPlainValue(), newArrayExpr.getSize())) {
				// Create the new taint abstraction
				newAbs = source.deriveNewAbstraction(leftVal,
						false, assignStmt, null, ArrayTaintType.Length);
			}
		}


		if (newAbs == null)
			return null;
		
		Set<Abstraction> res = new HashSet<>();
		res.add(newAbs);

		newRes = func(leftVal, stmt, Collections.singleton(newAbs));

		// Compute the aliases
		if (Aliasing.canHaveAliases(assignStmt, leftVal, newAbs))
			getAliasing().computeAliases(d1, assignStmt, leftVal, res,
					getManager().getICFG().getMethodOf(assignStmt), newAbs);
		
		return newRes;
	}


	private Set<Pair<DataFlowNode, Abstraction>> func(Value left,Unit stmt, Set<Abstraction> input) {

		Set<Pair<DataFlowNode, Abstraction>> res = new HashSet<>();
		Pair<Value, SootField> pair = InnerBBFastBuildDFGSolver.getBaseAndField(left);
		SootField field = pair.getO2();

		DataFlowNode dfg = useBaseAndFieldTofindDataFlowGraph(pair.getO1(), field, stmt);
		for(Abstraction abs : input) {
			AccessPath ap = abs.getAccessPath();
			SootField firstField = ap.getFirstField();
			if(dfg != null && dfg.getSuccs() != null) {
				Set<DataFlowNode> next = dfg.getSuccs().get(DataFlowNode.baseField);
				if(next != null)
					for(DataFlowNode d : next) {
						res.add(new Pair<DataFlowNode, Abstraction>(d, abs));
					}

				if(firstField != null) {
					Set<DataFlowNode> next1 = dfg.getSuccs().get(field);
					if(next1 != null)
						for(DataFlowNode d : next1) {
							res.add(new Pair<DataFlowNode, Abstraction>(d, abs));
						}
				}
			}
		}
		return res;
	}

	private DataFlowNode useBaseAndFieldTofindDataFlowGraph(Value base, SootField field, Unit stmt ) {
		SootMethod caller = getManager().getICFG().getMethodOf(stmt);
		if(field == null)
			field = DataFlowNode.baseField;

		DFGEntryKey key = new DFGEntryKey(stmt, base, field);

		if(!getManager().getDfg().containsKey(caller)) return null;
		Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> l1 = getManager().getDfg().get(caller);
		if(!l1.containsKey(base)) return null;
		Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2 = l1.get(base);
		if(!l2.containsKey(key)) return null;
		Pair<BaseInfoStmt, DataFlowNode> pair = l2.get(key);
		if(pair == null) return null;

		return pair.getO2();
	}


	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateCallFlow(Abstraction d1,
			Abstraction source, Stmt stmt, SootMethod dest,
			ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateCallToReturnFlow(Abstraction d1,
			Abstraction source, Stmt stmt, ByReferenceBoolean killSource,
			ByReferenceBoolean killAll) {
		return null;
	}

	@Override
	public Collection<Pair<DataFlowNode, Abstraction>> propagateReturnFlow(
			Collection<Abstraction> callerD1s, Abstraction source, Stmt stmt,
			Stmt retSite, Stmt callSite, ByReferenceBoolean killAll) {
		return null;
	}

}
