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
package soot.jimple.infoflow.sparseop.problem;

import heros.solver.Pair;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.aliasing.IAliasingStrategy;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.AccessPath.ArrayTaintType;
import soot.jimple.infoflow.data.AccessPathFactory;
import soot.jimple.infoflow.handlers.TaintPropagationHandler.FlowFunctionType;
import soot.jimple.infoflow.problems.TaintPropagationResults;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.dataflowgraph.InnerBBFastBuildDFGSolver;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.sparseop.problem.rules.PropagationRuleManagerOp;
import soot.jimple.infoflow.sparseop.solver.functions.SolverCallFlowFunctionOp;
import soot.jimple.infoflow.sparseop.solver.functions.SolverCallToReturnFlowFunctionOp;
import soot.jimple.infoflow.sparseop.solver.functions.SolverNormalFlowFunctionOp;
import soot.jimple.infoflow.sparseop.solver.functions.SolverReturnFlowFunctionOp;
import soot.jimple.infoflow.sparseop.solver.heros.FlowFunctionOp;
import soot.jimple.infoflow.sparseop.solver.heros.FlowFunctionsOp;
import soot.jimple.infoflow.sparseop.solver.heros.KillAllOp;
import soot.jimple.infoflow.util.BaseSelector;
import soot.jimple.infoflow.util.ByReferenceBoolean;
import soot.jimple.infoflow.util.TypeUtils;

import java.util.*;

public class InfoflowSPProblem extends AbstractInfoflowProblemOp {

	private final Aliasing aliasing;
	private final IAliasingStrategy aliasingStrategy;
	private final PropagationRuleManagerOp propagationRules;

	protected final TaintPropagationResults results;

	public InfoflowSPProblem(InfoflowManager manager,
                             IAliasingStrategy aliasingStrategy,
                             Abstraction zeroValue) {
		super(manager);
		
		if (zeroValue != null)
			setZeroValue(zeroValue);
		
		this.aliasingStrategy = aliasingStrategy;
		this.aliasing = new Aliasing(aliasingStrategy, manager.getICFG());
		this.results = new TaintPropagationResults(manager);
		
		this.propagationRules = new PropagationRuleManagerOp(manager, aliasing,
				createZeroValue(), results);
	}

	public class SourceAbsInfo {
		public DataFlowNode node;
		public Value value;
		public Abstraction source;

	}
	
	@Override
	public FlowFunctionsOp<Unit, DataFlowNode, Abstraction, SootMethod> createFlowFunctionsFactory() {
		return new FlowFunctionsOp<Unit, DataFlowNode, Abstraction, SootMethod>() {
			
			/**
			 * Abstract base class for all normal flow functions. This is to
			 * share code that e.g. notifies the taint handlers between the
			 * various functions.
			 * 
			 * @author Steven Arzt
			 */
			abstract class NotifyingNormalFlowFunction extends SolverNormalFlowFunctionOp {
				
				protected final Stmt stmt;
				
				public NotifyingNormalFlowFunction(Stmt stmt) {
					this.stmt = stmt;
				}
				
				@Override
				public Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction d1, Abstraction source) {
					if (1 <= manager.getConfig().getStopAfterFirstKFlows() && manager.getConfig().getStopAfterFirstKFlows() <= results.getResults().size())
						return Collections.emptySet();

					// Notify the handler if we have one
					if (taintPropagationHandler != null)
						taintPropagationHandler.notifyFlowIn(stmt, source, interproceduralCFG(),
								FlowFunctionType.NormalFlowFunction);
					
					// Compute the new abstractions
					Set<Pair<DataFlowNode, Abstraction>> res = computeTargetsInternal(d1, source);
					return notifyOutFlowHandlers(stmt, d1, source, res,
							FlowFunctionType.NormalFlowFunction);
				}
				
				public abstract Set<Pair<DataFlowNode, Abstraction>> computeTargetsInternal(Abstraction d1, Abstraction source);

			}
			
			/**
			 * Taints the left side of the given assignment
			 * @param assignStmt The source statement from which the taint originated
			 * @param //targetValue The target value that shall now be tainted
			 * @param source The incoming taint abstraction from the source
			 * @param taintSet The taint set to which to add all newly produced
			 * taints
			 */
			private void addTaintViaStmt
					(final Abstraction d1,
					final AssignStmt assignStmt,
					Abstraction source,
					Set<Abstraction> taintSet,
					boolean cutFirstField,
					SootMethod method,
					Type targetType) {
				final Value leftValue = assignStmt.getLeftOp();
				final Value rightValue = assignStmt.getRightOp();
				
				// Do not taint static fields unless the option is enabled
				if (!manager.getConfig().getEnableStaticFieldTracking()
						&& leftValue instanceof StaticFieldRef)
					return;
				
				Abstraction newAbs = null;
				if (!source.getAccessPath().isEmpty()) {
					// Special handling for array construction
					if (leftValue instanceof ArrayRef && targetType != null)
						targetType = TypeUtils.buildArrayOrAddDimension(targetType);
					
					// If this is an unrealizable typecast, drop the abstraction
					if (rightValue instanceof CastExpr) {
						// For casts, we must update our typing information
						CastExpr cast = (CastExpr) assignStmt.getRightOp();
						targetType = cast.getType();
					}
					// Special type handling for certain operations
					else if (rightValue instanceof InstanceOfExpr)
						newAbs = source.deriveNewAbstraction(AccessPathFactory.v().createAccessPath(assignStmt,
								leftValue, BooleanType.v(), true,
								ArrayTaintType.ContentsAndLength), assignStmt);
				}
				else
					// For implicit taints, we have no type information
					assert targetType == null;
				
				// Do we taint the contents of an array? If we do not differentiate,
				// we do not set any special type.
				ArrayTaintType arrayTaintType = source.getAccessPath().getArrayTaintType();
				if (leftValue instanceof ArrayRef
						&& manager.getConfig().getEnableArraySizeTainting())
					arrayTaintType = ArrayTaintType.Contents;
				
				// also taint the target of the assignment
				if (newAbs == null)
					if (source.getAccessPath().isEmpty())
						newAbs = source.deriveNewAbstraction(AccessPathFactory.v().createAccessPath(assignStmt,
								leftValue, true), assignStmt, true);
					else
						newAbs = source.deriveNewAbstraction(leftValue, cutFirstField, assignStmt, targetType,
								arrayTaintType);
				
				if (newAbs != null) {
					taintSet.add(newAbs);

					if (Aliasing.canHaveAliases(assignStmt, leftValue, newAbs))
						aliasing.computeAliases(d1, assignStmt, leftValue, taintSet,
								method, newAbs);
				}
			}
			
			/**
			 * Checks whether the given call has at least one valid target,
			 * i.e. a callee with a body.
			 * @param call The call site to check
			 * @return True if there is at least one callee implementation
			 * for the given call, otherwise false
			 */
			private boolean hasValidCallees(Unit call) {
				Collection<SootMethod> callees = interproceduralCFG().getCalleesOfCallAt(call);
				for (SootMethod callee : callees)
					if (callee.isConcrete())
						return true;
				return false;
			}
			
			private Set<Abstraction> createNewTaintOnAssignment(
					final AssignStmt assignStmt,
					final Value[] rightVals,
					Abstraction d1,
					final Abstraction newSource) {
				final Value leftValue = assignStmt.getLeftOp();
				final Value rightValue = assignStmt.getRightOp();
				boolean addLeftValue = false;
				
				// If we have an implicit flow, but the assigned
				// local is never read outside the condition, we do
				// not need to taint it.
				boolean implicitTaint = newSource.getTopPostdominator() != null
						&& newSource.getTopPostdominator().getUnit() != null;
				implicitTaint |= newSource.getAccessPath().isEmpty();
											
				// If we have a non-empty postdominator stack, we taint
				// every assignment target
				if (implicitTaint) {
					assert manager.getConfig().getEnableImplicitFlows();
					
					// We can skip over all local assignments inside conditionally-
					// called functions since they are not visible in the caller
					// anyway
					if ((d1 == null || d1.getAccessPath().isEmpty())
							&& !(leftValue instanceof FieldRef))
						return Collections.singleton(newSource);
													
					if (newSource.getAccessPath().isEmpty())
						addLeftValue = true;
				}
				
				// If we have a = x with the taint "x" being inactive,
				// we must not taint the left side. We can only taint
				// the left side if the tainted value is some "x.y".
				boolean aliasOverwritten = !addLeftValue
						&& !newSource.isAbstractionActive()
						&& Aliasing.baseMatchesStrict(rightValue, newSource)
						&& rightValue.getType() instanceof RefType
						&& !newSource.dependsOnCutAP();
				
				boolean cutFirstField = false;
				AccessPath mappedAP = newSource.getAccessPath();
				Type targetType = null;
				if (!addLeftValue && !aliasOverwritten) {
					for (Value rightVal : rightVals) {
						if (rightVal instanceof FieldRef) {
							// Get the field reference
							FieldRef rightRef = (FieldRef) rightVal;

							// If the right side references a NULL field, we kill the taint
							if (rightRef instanceof InstanceFieldRef
									&& ((InstanceFieldRef) rightRef).getBase().getType() instanceof NullType)
								return null;
							
							// Check for aliasing
							mappedAP = aliasing.mayAlias(newSource.getAccessPath(), rightRef);
							
							// check if static variable is tainted (same name, same class)
							//y = X.f && X.f tainted --> y, X.f tainted
							if (rightVal instanceof StaticFieldRef) {
								if (manager.getConfig().getEnableStaticFieldTracking() && mappedAP != null) {
									addLeftValue = true;
									cutFirstField = true;
								}
							}
							// check for field references
							//y = x.f && x tainted --> y, x tainted
							//y = x.f && x.f tainted --> y, x tainted
							else if (rightVal instanceof InstanceFieldRef) {
								Local rightBase = (Local) ((InstanceFieldRef) rightRef).getBase();
								Local sourceBase = newSource.getAccessPath().getPlainValue();
								final SootField rightField = rightRef.getField();
								
								// We need to compare the access path on the right side
								// with the start of the given one
								if (mappedAP != null) {
									addLeftValue = true;
									cutFirstField = (mappedAP.getFieldCount() > 0
											&& mappedAP.getFirstField() == rightField);
								}
								else if (aliasing.mayAlias(rightBase, sourceBase)
										&& newSource.getAccessPath().getFieldCount() == 0
										&& newSource.getAccessPath().getTaintSubFields()) {
									addLeftValue = true;
									targetType = rightField.getType();
								}
							}
						}
						// indirect taint propagation:
						// if rightvalue is local and source is instancefield of this local:
						// y = x && x.f tainted --> y.f, x.f tainted
						// y.g = x && x.f tainted --> y.g.f, x.f tainted
						else if (rightVal instanceof Local && newSource.getAccessPath().isInstanceFieldRef()) {
							Local base = newSource.getAccessPath().getPlainValue();
							if (aliasing.mayAlias(rightVal, base)) {
								addLeftValue = true;
								targetType = newSource.getAccessPath().getBaseType();
							}
						}
						// generic case, is true for Locals, ArrayRefs that are equal etc..
						//y = x && x tainted --> y, x tainted
						else if (aliasing.mayAlias(rightVal, newSource.getAccessPath().getPlainValue())) {
							if (!(assignStmt.getRightOp() instanceof NewArrayExpr)) {
								if (manager.getConfig().getEnableArraySizeTainting()
										|| !(rightValue instanceof NewArrayExpr)) {
									addLeftValue = true;
									targetType = newSource.getAccessPath().getBaseType();
								}
							}
						}
						
						// One reason to taint the left side is enough
						if (addLeftValue)
							break;
					}
				}
				
				// If we have nothing to add, we quit
				if (!addLeftValue)
					return null;
				
				// Do not propagate non-active primitive taints
				if (!newSource.isAbstractionActive()
						&& (assignStmt.getLeftOp().getType() instanceof PrimType
								|| (TypeUtils.isStringType(assignStmt.getLeftOp().getType())
										&& !newSource.getAccessPath().getCanHaveImmutableAliases())))
					return Collections.singleton(newSource);
				
				Set<Abstraction> res = new HashSet<Abstraction>();
				Abstraction targetAB = mappedAP.equals(newSource.getAccessPath())
						? newSource : newSource.deriveNewAbstraction(mappedAP, null);
				addTaintViaStmt(d1, assignStmt, targetAB, res, cutFirstField,
						interproceduralCFG().getMethodOf(assignStmt), targetType);
				res.add(newSource);
				return res;
			}



			@Override
			public FlowFunctionOp<DataFlowNode, Abstraction> getNormalFlowFunction(final Unit src, final Unit dest) {
				// Get the call site
				if (!(src instanceof Stmt))
					return KillAllOp.v();
				
				return new NotifyingNormalFlowFunction((Stmt) src) {
					
					@Override
					public Set<Pair<DataFlowNode, Abstraction>> computeTargetsInternal(Abstraction d1, Abstraction source) {
						// Check whether we must activate a taint

						final Abstraction newSource;
						if (!source.isAbstractionActive() && isActivatingTaint(manager.getICFG().getMethodOf(src),
								source.getActivationUnit(), src, source))
							newSource = source.getActiveCopy();
						else
							newSource = source;
//						if (!source.isAbstractionActive() && src == source.getActivationUnit())
//							newSource = source.getActiveCopy();
//						else
//							newSource = source;

						// Apply the propagation rules
						ByReferenceBoolean killSource = new ByReferenceBoolean();
						killSource.value = true;
						ByReferenceBoolean killAll = new ByReferenceBoolean();
						Set<Pair<DataFlowNode, Abstraction>> res = propagationRules.applyNormalFlowFunction(d1,
								newSource, stmt, (Stmt) dest, killSource, killAll);
						if (killAll.value)
							return Collections.<Pair<DataFlowNode, Abstraction>>emptySet();
						if(res != null)
						for(Pair<DataFlowNode, Abstraction> pair : res) {
							if(pair.getO2() == null || pair.getO2().equals(newSource)) {
								throw new RuntimeException("don't need source");
							}
						}
						
						// Propagate over an assignment
						if (src instanceof AssignStmt) {
							final AssignStmt assignStmt = (AssignStmt) src;
							final Value right = assignStmt.getRightOp();
							final Value[] rightVals = BaseSelector.selectBaseList(right, true);
							
							// Create the new taints that may be created by this assignment
							Set<Abstraction> resAssign = createNewTaintOnAssignment(assignStmt,
									rightVals, d1, newSource);
							//Set<Abstraction> newResAssin = null;
							if (resAssign != null && !resAssign.isEmpty()) {
								if(resAssign.size() == 1 && resAssign.contains(newSource))
									resAssign = null;
								else
									resAssign.remove(newSource);
							}
							//resAssign = newResAssin;

							if (resAssign != null && !resAssign.isEmpty()) {
								final Value left = assignStmt.getLeftOp();
								Set<Pair<DataFlowNode, Abstraction>> set = func(left, src, resAssign);

								if (res != null) {
									res.addAll(set);
									return res;
								}
								else
									res = set;
							}
						}
						
						// Return what we have so far
						return res == null || res.isEmpty() ? Collections.<Pair<DataFlowNode, Abstraction>>emptySet() : res;
					}
					
				};
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



			@Override
			public FlowFunctionOp<DataFlowNode, Abstraction> getCallFlowFunction(final Unit src, final SootMethod dest) {
                if (!dest.isConcrete()){
                    logger.debug("Call skipped because target has no body: {} -> {}", src, dest);
                    return KillAllOp.v();
                }

				final Stmt stmt = (Stmt) src;
				final InvokeExpr ie = (stmt != null && stmt.containsInvokeExpr())
						? stmt.getInvokeExpr() : null;

				final Map<Value, Unit> paramLocalToStmtMap = dest.getActiveBody().getParameterLocalsToStmt();

				final Local[] paramLocals = dest.getActiveBody().getParameterLocals().toArray(new Local[0]);
				//final Local[] paramLocals = (Local [])paramLocalToStmtMap.keySet().toArray() ;

				// This is not cached by Soot, so accesses are more expensive
				// than one might think
				final Local thisLocal = dest.isStatic() ? null : dest.getActiveBody().getThisLocal();

				return new SolverCallFlowFunctionOp() {

					@Override
					public Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction d1, Abstraction source) {
						Set<Pair<DataFlowNode, Abstraction>> res = computeTargetsInternal(d1, source);
						Set<Abstraction> newRes = new HashSet<>();
						for(Pair<DataFlowNode, Abstraction> pair : res) {
							newRes.add(pair.getO2());
						}
						if (!res.isEmpty())
							for (Abstraction abs : newRes)
								aliasingStrategy.injectCallingContext(abs, solver, dest, src, source, d1);
						return notifyOutFlowHandlers(stmt, d1, source, res,
								FlowFunctionType.CallFlowFunction);
					}

					private Set<Pair<DataFlowNode, Abstraction>> computeTargetsInternal(Abstraction d1, Abstraction source) {
						if (manager.getConfig().getStopAfterFirstFlow() && !results.isEmpty())
							return Collections.emptySet();
						if (source == getZeroValue())
							return Collections.emptySet();

						// Do not analyze static initializers if static field
						// tracking is disabled
						if (!manager.getConfig().getEnableStaticFieldTracking() && dest.isStaticInitializer())
							return Collections.emptySet();

						// Notify the handler if we have one
						if (taintPropagationHandler != null)
							taintPropagationHandler.notifyFlowIn(stmt, source, interproceduralCFG(),
									FlowFunctionType.CallFlowFunction);

						ByReferenceBoolean killAll = new ByReferenceBoolean();
						Set<Pair<DataFlowNode, Abstraction>> res = propagationRules.applyCallFlowFunction(d1,
								source, stmt, dest, killAll);
						if (killAll.value)
							return Collections.emptySet();

						// Only propagate the taint if the target field is actually read
						if (source.getAccessPath().isStaticFieldRef()
								&& !manager.getConfig().getEnableStaticFieldTracking())
							return Collections.emptySet();

						// Map the source access path into the callee
						Set<Pair<Value, AccessPath>> resMapping = mapAccessPathToCalleeOp(dest, ie, paramLocals,
								thisLocal, source.getAccessPath());
						if (resMapping == null)
							return res == null || res.isEmpty() ? Collections.<Pair<DataFlowNode, Abstraction>>emptySet() : res;

						// Translate the access paths into abstractions
						Set<Pair<DataFlowNode, Abstraction>> resAbs = new HashSet<Pair<DataFlowNode, Abstraction>>(resMapping.size());
						if (res != null && !res.isEmpty())
							resAbs.addAll(res);
						for (Pair<Value, AccessPath> pair : resMapping) {
							Value target = pair.getO1();
							AccessPath ap = pair.getO2();
							if (ap != null) {
								if (ap.isStaticFieldRef()) {
									// Do not propagate static fields that are not read inside the callee
									if (interproceduralCFG().isStaticFieldRead(dest, ap.getFirstField())) {
										Abstraction newAbs = source.deriveNewAbstraction(ap, stmt);
										if (newAbs != null) {
											Unit idStmt = paramLocalToStmtMap.get(target);
											if(idStmt == null)
												throw new RuntimeException("no id stmt of thr param in OP");
											resAbs.addAll(callHelper(target, idStmt, newAbs));

										}
											//resAbs.add(newAbs);

									}
								}
								// If the variable is never read in the callee, there is no
								// need to propagate it through
								else if (source.isImplicit() || interproceduralCFG().methodReadsValue(dest, ap.getPlainValue())) {
									Abstraction newAbs = source.deriveNewAbstraction(ap, stmt);
									if (newAbs != null) {
										Unit idStmt = paramLocalToStmtMap.get(target);
										if(idStmt == null)
											throw new RuntimeException("no id stmt of thr param in OP");
										resAbs.addAll(callHelper(target, idStmt, newAbs));

									}
										//resAbs.add(newAbs);
								}
							}
						}

						return resAbs;
					}
				};
			}

			@Override
			public FlowFunctionOp<DataFlowNode, Abstraction> getReturnFlowFunction(final Unit callSite,
					final SootMethod callee, final Unit exitStmt, final Unit retSite) {
				// Get the call site
				if (callSite != null && !(callSite instanceof Stmt))
					return KillAllOp.v();
				final Stmt iCallStmt = (Stmt) callSite;

				final ReturnStmt returnStmt = (exitStmt instanceof ReturnStmt) ? (ReturnStmt) exitStmt : null;

				final Local[] paramLocals = callee.getActiveBody().getParameterLocals().toArray(
						new Local[0]);

				// This is not cached by Soot, so accesses are more expensive
				// than one might think
				final Local thisLocal = callee.isStatic() ? null : callee.getActiveBody().getThisLocal();

				return new SolverReturnFlowFunctionOp() {

					@Override
					public Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction source, Abstraction d1,
							Collection<Abstraction> callerD1s) {
						Set<Pair<DataFlowNode, Abstraction>> res = computeTargetsInternal(source, callerD1s);
						return notifyOutFlowHandlers(exitStmt, d1, source, res,
								FlowFunctionType.ReturnFlowFunction);
					}

					private Set<Pair<DataFlowNode, Abstraction>> computeTargetsInternal(Abstraction source,
							Collection<Abstraction> callerD1s) {
						if (manager.getConfig().getStopAfterFirstFlow() && !results.isEmpty())
							return Collections.emptySet();
						if (source == getZeroValue())
							return Collections.emptySet();

						// Notify the handler if we have one
						if (taintPropagationHandler != null)
								taintPropagationHandler.notifyFlowIn(exitStmt, source, interproceduralCFG(),
										FlowFunctionType.ReturnFlowFunction);

						boolean callerD1sConditional = false;
						for (Abstraction d1 : callerD1s)
							if (d1.getAccessPath().isEmpty()) {
								callerD1sConditional = true;
								break;
							}

						// Activate taint if necessary
						Abstraction newSource = source;
//						if(!source.isAbstractionActive())
//							if(callSite != null)
//								if (isActivatingTaint(manager.getICFG().getMethodOf(callSite),
//										source.getActivationUnit(), callSite, source))
//									newSource = source.getActiveCopy();
						if(!source.isAbstractionActive())
							if(callSite != null)
								if (callSite == source.getActivationUnit()
										|| isCallSiteActivatingTaint(callSite, source.getActivationUnit()))
									newSource = source.getActiveCopy();

						//if abstraction is not active and activeStmt was in this method, it will not get activated = it can be removed:
						if(!newSource.isAbstractionActive() && newSource.getActivationUnit() != null)
							if (interproceduralCFG().getMethodOf(newSource.getActivationUnit()) == callee)
								return Collections.emptySet();

						// Static field tracking can be disabled
						if (!manager.getConfig().getEnableStaticFieldTracking()
								&& newSource.getAccessPath().isStaticFieldRef())
							return Collections.emptySet();

						ByReferenceBoolean killAll = new ByReferenceBoolean();
						Set<Pair<DataFlowNode, Abstraction>> res = propagationRules.applyReturnFlowFunction(callerD1s,
								newSource, (Stmt) exitStmt, (Stmt) retSite, (Stmt) callSite,
								killAll);
						if (killAll.value)
							return Collections.emptySet();
						if (res == null)
							res = new HashSet<Pair<DataFlowNode, Abstraction>>();

						// If we have no caller, we have nowhere to propagate. This
						// can happen when leaving the main method.
						if (callSite == null)
							return Collections.emptySet();

						// if we have a returnStmt we have to look at the returned value:
						if (returnStmt != null && callSite instanceof DefinitionStmt) {
							Value retLocal = returnStmt.getOp();
							DefinitionStmt defnStmt = (DefinitionStmt) callSite;
							Value leftOp = defnStmt.getLeftOp();

							if (aliasing.mayAlias(retLocal, newSource.getAccessPath().getPlainValue())
									&& !isExceptionHandler(retSite)) {
								Abstraction abs = newSource.deriveNewAbstraction
										(newSource.getAccessPath().copyWithNewValue(callSite, leftOp), (Stmt) exitStmt);
								if (abs != null) {

									res.addAll(callHelper(leftOp, callSite, abs));

									// Aliases of implicitly tainted variables must be mapped back
									// into the caller's context on return when we leave the last
									// implicitly-called method
//									if (aliasingStrategy.requiresAnalysisOnReturn())
//										for (Abstraction d1 : callerD1s)
//											aliasing.computeAliases(d1, iCallStmt, leftOp, res,
//													interproceduralCFG().getMethodOf(callSite), abs);
								}
							}
						}

						// easy: static
//						if (newSource.getAccessPath().isStaticFieldRef()) {
//							// Simply pass on the taint
//							Abstraction abs = newSource;
//							res.add(abs);
//
//							// Aliases of implicitly tainted variables must be mapped back
//							// into the caller's context on return when we leave the last
//							// implicitly-called method
//							if ((abs.isImplicit() && !callerD1sConditional)
//									 || aliasingStrategy.requiresAnalysisOnReturn())
//								for (Abstraction d1 : callerD1s)
//									aliasing.computeAliases(d1, iCallStmt, null, res,
//											interproceduralCFG().getMethodOf(callSite), abs);
//						}

						// checks: this/params/fields

						// check one of the call params are tainted (not if simple type)
						Value sourceBase = newSource.getAccessPath().getPlainValue();
						boolean parameterAliases = false;
						{
						Value originalCallArg = null;
						for (int i = 0; i < callee.getParameterCount(); i++) {
							// If this parameter is overwritten, we cannot propagate
							// the "old" taint over. Return value propagation must
							// always happen explicitly.
							if (callSite instanceof DefinitionStmt && !isExceptionHandler(retSite)) {
								DefinitionStmt defnStmt = (DefinitionStmt) callSite;
								Value leftOp = defnStmt.getLeftOp();
								originalCallArg = defnStmt.getInvokeExpr().getArg(i);
								if (originalCallArg == leftOp)
									continue;
							}

							// Propagate over the parameter taint
							if (aliasing.mayAlias(paramLocals[i], sourceBase)) {
								parameterAliases = true;
								originalCallArg = iCallStmt.getInvokeExpr().getArg(i);

								// If this is a constant parameter, we can safely ignore it
								if (!AccessPath.canContainValue(originalCallArg))
									continue;
								if (!manager.getTypeUtils().checkCast(source.getAccessPath(), originalCallArg.getType()))
									continue;

								// Primitive types and strings cannot have aliases and thus
								// never need to be propagated back
								if (source.getAccessPath().getBaseType() instanceof PrimType)
									continue;
								if (TypeUtils.isStringType(source.getAccessPath().getBaseType())
										&& !source.getAccessPath().getCanHaveImmutableAliases())
									continue;

								// If only the object itself, but no field is tainted, we can safely ignore it
								if (!source.getAccessPath().getTaintSubFields())
									continue;

								// If the variable was overwritten somewehere in the callee, we assume it
								// to overwritten on all paths (yeah, I know ...) Otherwise, we need SSA
								// or lots of bookkeeping to avoid FPs (BytecodeTests.flowSensitivityTest1).
								if (interproceduralCFG().methodWritesValue(callee, paramLocals[i]))
									continue;

								Abstraction abs = newSource.deriveNewAbstraction
										(newSource.getAccessPath().copyWithNewValue(callSite, originalCallArg), (Stmt) exitStmt);
								if (abs != null) {
									res.addAll(callHelper(originalCallArg, callSite, abs));

									// Aliases of implicitly tainted variables must be mapped back
									// into the caller's context on return when we leave the last
									// implicitly-called method
//									if ((abs.isImplicit()
//											&& !callerD1sConditional) || aliasingStrategy.requiresAnalysisOnReturn()) {
//										assert originalCallArg.getType() instanceof ArrayType
//												|| originalCallArg.getType() instanceof RefType;
//										for (Abstraction d1 : callerD1s)
//											aliasing.computeAliases(d1, iCallStmt, originalCallArg, res,
//												interproceduralCFG().getMethodOf(callSite), abs);
//									}
								}
							}
						}
						}

						{
						if (!callee.isStatic()) {
							// If this parameter is overwritten, we cannot propagate
							// the "old" taint over. Return value propagation must
							// always happen explicitly.
							boolean thisAliases = false;
							if (callSite instanceof DefinitionStmt && !isExceptionHandler(retSite)) {
								DefinitionStmt defnStmt = (DefinitionStmt) callSite;
								Value leftOp = defnStmt.getLeftOp();
								if (thisLocal == leftOp)
									thisAliases = true;
							}

							// check if it is not one of the params (then we have already fixed it)
							if (!parameterAliases && !thisAliases
									&& source.getAccessPath().getTaintSubFields()
									&& iCallStmt.getInvokeExpr() instanceof InstanceInvokeExpr
									&& aliasing.mayAlias(thisLocal, sourceBase)) {
								// Type check
								if (manager.getTypeUtils().checkCast(source.getAccessPath(), thisLocal.getType())) {
									InstanceInvokeExpr iIExpr = (InstanceInvokeExpr) iCallStmt.getInvokeExpr();
									Abstraction abs = newSource.deriveNewAbstraction
											(newSource.getAccessPath().copyWithNewValue(callSite, iIExpr.getBase()), (Stmt) exitStmt);
									if (abs != null) {

										//res.add(abs);
										res.addAll(callHelper(iIExpr.getBase(), callSite, abs));
										// Aliases of implicitly tainted variables must be mapped back
										// into the caller's context on return when we leave the last
										// implicitly-called method
//										if ((abs.isImplicit()
//												&& Aliasing.canHaveAliases(iCallStmt, iIExpr.getBase(), abs)
//												&& !callerD1sConditional) || aliasingStrategy.requiresAnalysisOnReturn())
//											for (Abstraction d1 : callerD1s)
//												aliasing.computeAliases(d1, iCallStmt, iIExpr.getBase(), res,
//														interproceduralCFG().getMethodOf(callSite), abs);
									}
								}
							}
							}
						}

//						for (Abstraction abs : res) {
//							if (abs != newSource) {
//								abs.setCorrespondingCallSite(iCallStmt);
//							}
//						}
						return res;
					}

				};
			}

//			Pair<DataFlowNode, Abstraction> returnHelper(Value op , SootMethod callee , Abstraction input) {
//				DataFlowNode dfg = useValueTofindDataFlowGraph(op, callee);
//				return new Pair<>(dfg, input);
//			}
			Set<Pair<DataFlowNode, Abstraction>> callHelper(Value op , Unit stmt , Abstraction input) {
				Set<Pair<DataFlowNode, Abstraction>> res = new HashSet<>();

				DataFlowNode dfg = useValueTofindDataFlowGraph(op, stmt);
				if(dfg != null) {
					if(dfg.getSuccs() != null) {
						Set<DataFlowNode> nextSet = dfg.getSuccs().get(dfg.getField());
						if(nextSet != null) {
							for(DataFlowNode next : nextSet) {
								res.add(new Pair<>(next, input));
							}
						}
					}
				}
				return res;
			}

			Pair<DataFlowNode, Abstraction> returnHelper(Value op , Unit stmt , Abstraction input) {
				DataFlowNode dfg = useValueTofindDataFlowGraph(op, stmt);
				return new Pair<>(dfg, input);
			}
			
			@Override
			public FlowFunctionOp<DataFlowNode, Abstraction> getCallToReturnFlowFunction(final Unit call,
					final Unit returnSite) {
				// special treatment for native methods:
				if (!(call instanceof Stmt))
					return KillAllOp.v();
				
				final Stmt iCallStmt = (Stmt) call;
				final InvokeExpr invExpr = iCallStmt.getInvokeExpr();
				
				final Value[] callArgs = new Value[invExpr.getArgCount()];
				for (int i = 0; i < invExpr.getArgCount(); i++)
					callArgs[i] = invExpr.getArg(i);
				
				final boolean isSink = (manager.getSourceSinkManager() != null)
						? manager.getSourceSinkManager().isSink(iCallStmt, interproceduralCFG(), null) : false;
				final boolean isSource = (manager.getSourceSinkManager() != null)
						? manager.getSourceSinkManager().getSourceInfo(iCallStmt, interproceduralCFG()) != null : false;
				
				final SootMethod callee = invExpr.getMethod();
				final boolean hasValidCallees = hasValidCallees(call);
				
				return new SolverCallToReturnFlowFunctionOp() {

					@Override
					public Set<Pair<DataFlowNode, Abstraction>> computeTargets(Abstraction d1, Abstraction source) {
						Set<Pair<DataFlowNode, Abstraction>> res = computeTargetsInternal(d1, source);
						return notifyOutFlowHandlers(call, d1, source, res,
								FlowFunctionType.CallToReturnFlowFunction);
					}
					
					private Set<Pair<DataFlowNode, Abstraction>> computeTargetsInternal(Abstraction d1, Abstraction source) {
						if (manager.getConfig().getStopAfterFirstFlow() && !results.isEmpty())
							return Collections.emptySet();
						
						// Notify the handler if we have one
						if (taintPropagationHandler != null)
							taintPropagationHandler.notifyFlowIn(call, source, interproceduralCFG(),
									FlowFunctionType.CallToReturnFlowFunction);
						
						// Static field tracking can be disabled
						if (!manager.getConfig().getEnableStaticFieldTracking()
								&& source.getAccessPath().isStaticFieldRef())
							return Collections.emptySet();
						
						//check inactive elements:
						final Abstraction newSource;
						if (!source.isAbstractionActive() && isActivatingTaint(manager.getICFG().getMethodOf(call),
								source.getActivationUnit(), call, source))
							newSource = source.getActiveCopy();
						else
							newSource = source;

//						if (!source.isAbstractionActive() && (call == source.getActivationUnit()
//								|| isCallSiteActivatingTaint(call, source.getActivationUnit())))
//							newSource = source.getActiveCopy();
//						else
//							newSource = source;
						
						ByReferenceBoolean killSource = new ByReferenceBoolean();
						ByReferenceBoolean killAll = new ByReferenceBoolean();
						Set<Pair<DataFlowNode, Abstraction>> newRes = propagationRules.applyCallToReturnFlowFunction(
								d1, newSource, iCallStmt, killSource, killAll, true);
						if (killAll.value)
							return Collections.emptySet();
						boolean passOn = !killSource.value;


						// Do not propagate zero abstractions
//						if (source == getZeroValue())
//							return res == null || res.isEmpty() ? Collections.<Pair<DataFlowNode, Abstraction>>emptySet() : res;
						if (source == getZeroValue())
							return newRes == null || newRes.isEmpty() ? Collections.<Pair<DataFlowNode, Abstraction>>emptySet() : newRes;

						Set<Abstraction> res = null;
						// Initialize the result set
						if(newRes == null)
							newRes = new HashSet<>();
						if (res == null)
							res = new HashSet<>();
						
//						if (newSource.getTopPostdominator() != null
//								&& newSource.getTopPostdominator().getUnit() == null)
//							return Collections.singleton(newSource);
						
						// If this call overwrites the left side, the taint is never passed on.
						if (passOn) {
							if (newSource.getAccessPath().isStaticFieldRef())
								passOn = false;
							else if (call instanceof DefinitionStmt
									&& aliasing.mayAlias(((DefinitionStmt) call).getLeftOp(),
											newSource.getAccessPath().getPlainValue()))
								passOn = false;
						}
						
						//we only can remove the taint if we step into the call/return edges
						//otherwise we will loose taint - see ArrayTests/arrayCopyTest
						if (passOn
								&& invExpr instanceof InstanceInvokeExpr
								&& newSource.getAccessPath().isInstanceFieldRef()
								&& (manager.getConfig().getInspectSinks() || !isSink)
								&& (manager.getConfig().getInspectSources() || !isSource)
								&& (hasValidCallees
									|| (taintWrapper != null && taintWrapper.isExclusive(
											iCallStmt, newSource)))) {
							// If one of the callers does not read the value, we must pass it on
							// in any case
							boolean allCalleesRead = true;
							outer : for (SootMethod callee : interproceduralCFG().getCalleesOfCallAt(call)) {
								if (callee.isConcrete() && callee.hasActiveBody()) {
									Set<AccessPath> calleeAPs = mapAccessPathToCallee(callee,
											invExpr, null, null, source.getAccessPath());
									if (calleeAPs != null)
										for (AccessPath ap : calleeAPs)
											if (ap != null)
												if (!interproceduralCFG().methodReadsValue(callee, ap.getPlainValue())) {
													allCalleesRead = false;
													break outer;
												}
										}
							}
							
							if (allCalleesRead) {
								if (aliasing.mayAlias(((InstanceInvokeExpr) invExpr).getBase(),
										newSource.getAccessPath().getPlainValue())) {
									passOn = false;
								}
								if (passOn)
									for (int i = 0; i < callArgs.length; i++)
										if (aliasing.mayAlias(callArgs[i], newSource.getAccessPath().getPlainValue())) {
											passOn = false;
											break;
										}
								//static variables are always propagated if they are not overwritten. So if we have at least one call/return edge pair,
								//we can be sure that the value does not get "lost" if we do not pass it on:
								if(newSource.getAccessPath().isStaticFieldRef())
									passOn = false;
							}
						}
						
						// If the callee does not read the given value, we also need to pass it on
						// since we do not propagate it into the callee.
						if (source.getAccessPath().isStaticFieldRef()) {
							if (!interproceduralCFG().isStaticFieldUsed(callee,
									source.getAccessPath().getFirstField()))
								passOn = true;
						}
												
						// Implicit taints are always passed over conditionally called methods
						passOn |= source.getTopPostdominator() != null || source.getAccessPath().isEmpty();
						if (passOn) {
							if (newSource != getZeroValue())
								res.add(newSource);
						}

						if (callee.isNative())
							for (Value callVal : callArgs)
								if (callVal == newSource.getAccessPath().getPlainValue()) {
									// java uses call by value, but fields of complex objects can be changed (and tainted), so use this conservative approach:
									Set<Abstraction> nativeAbs = ncHandler.getTaintedValues(iCallStmt, newSource, callArgs);
									if (nativeAbs != null) {
										res.addAll(nativeAbs);

										// Compute the aliases
										for (Abstraction abs : nativeAbs)
											if (abs.getAccessPath().isStaticFieldRef()
													|| Aliasing.canHaveAliases(iCallStmt,
															abs.getAccessPath().getPlainValue(), abs))
												aliasing.computeAliases(d1, iCallStmt,
														abs.getAccessPath().getPlainValue(), res,
														interproceduralCFG().getMethodOf(call), abs);
									}

									// We only call the native code handler once per statement
									break;
								}

						for (Abstraction abs : res)
							if (abs != newSource)
								abs.setCorrespondingCallSite(iCallStmt);


						for(Abstraction abs : res) {
							DataFlowNode dfg = useApTofindDataFlowGraph(abs.getAccessPath(), iCallStmt);
							if(dfg == null)
								dfg = useBaseTofindDataFlowGraph(abs.getAccessPath().getPlainValue(), iCallStmt);

							//debugdebug
							//SootMethod m = manager.getICFG().getMethodOf(iCallStmt);
							if(dfg == null)
								throw new RuntimeException("Wapper AccessPath cant find the relative Dfg, it should be built before using! ");
							if(dfg.getSuccs() != null){
								Set<DataFlowNode> nextSet = dfg.getSuccs().get(dfg.getField());
								for(DataFlowNode next : nextSet) {
									newRes.add(new Pair<>(next, abs));
								}
							}
						}


						
						return newRes;
					}
				};
			}
			
			/**
			 * Maps the given access path into the scope of the callee
			 * @param callee The method that is being called
			 * @param ie The invocation expression for the call
			 * @param paramLocals The list of parameter locals in the callee
			 * @param thisLocal The "this" local in the callee
			 * @param ap The caller-side access path to map
			 * @return The set of callee-side access paths corresponding to the
			 * given caller-side access path
			 */
			private Set<Pair<Value, AccessPath>> mapAccessPathToCalleeOp(final SootMethod callee, final InvokeExpr ie,
					Value[] paramLocals, Local thisLocal, AccessPath ap) {
				// We do not transfer empty access paths
				if (ap.isEmpty())
					return Collections.emptySet();
				
				// Android executor methods are handled specially. getSubSignature()
				// is slow, so we try to avoid it whenever we can
				final boolean isExecutorExecute = interproceduralCFG().isExecutorExecute(ie, callee);
				
				Set<Pair<Value, AccessPath>> res = null;
				
				// check if whole object is tainted (happens with strings, for example:)
				if (!isExecutorExecute
						&& !ap.isStaticFieldRef()
						&& !callee.isStatic()) {
					assert ie instanceof InstanceInvokeExpr;
					InstanceInvokeExpr vie = (InstanceInvokeExpr) ie;
					if (aliasing.mayAlias(vie.getBase(), ap.getPlainValue()))
						if (manager.getTypeUtils().hasCompatibleTypesForCall(ap, callee.getDeclaringClass())) {
							if (res == null) res = new HashSet<Pair<Value, AccessPath>>();
							
							// Get the "this" local if we don't have it yet
							if (thisLocal == null)
								thisLocal = callee.getActiveBody().getThisLocal();
							
							//res.add(ap.copyWithNewValue(thisLocal));
							res.add(new Pair<Value, AccessPath>(thisLocal, ap.copyWithNewValue(null, thisLocal)));
						}
				}
				// staticfieldRefs must be analyzed even if they are not part of the params:
				else if (ap.isStaticFieldRef()) {
//					if (res == null) res = new HashSet<AccessPath>();
//					res.add(ap);
				}
				
				//special treatment for clinit methods - no param mapping possible
				if (isExecutorExecute) {
					if (aliasing.mayAlias(ie.getArg(0), ap.getPlainValue())) {
						if (res == null) res = new HashSet<Pair<Value, AccessPath>>();
						//res.add(ap.copyWithNewValue(callee.getActiveBody().getThisLocal()));
						Value local = callee.getActiveBody().getThisLocal();
						res.add(new Pair<Value, AccessPath>(local, ap.copyWithNewValue(null, local)));
					}
				}
				else if (callee.getParameterCount() > 0) {
					assert callee.getParameterCount() == ie.getArgCount();
					// check if param is tainted:
					for (int i = 0; i < ie.getArgCount(); i++) {
						if (aliasing.mayAlias(ie.getArg(i), ap.getPlainValue())) {
							if (res == null) res = new HashSet<Pair<Value, AccessPath>>();
							
							// Get the parameter locals if we don't have them yet
							if (paramLocals == null)
								paramLocals = callee.getActiveBody().getParameterLocals().toArray(
										new Local[callee.getParameterCount()]);
							
							AccessPath newAP = ap.copyWithNewValue(null, paramLocals[i]);
							if (newAP != null)
								res.add(new Pair<Value, AccessPath>(paramLocals[i], newAP));
						}
					}
				}
				return res;
			}


			private Set<AccessPath> mapAccessPathToCallee(final SootMethod callee, final InvokeExpr ie,
														  Value[] paramLocals, Local thisLocal, AccessPath ap) {
				// We do not transfer empty access paths
				if (ap.isEmpty())
					return Collections.emptySet();

				// Android executor methods are handled specially. getSubSignature()
				// is slow, so we try to avoid it whenever we can
				final boolean isExecutorExecute = interproceduralCFG().isExecutorExecute(ie, callee);

				Set<AccessPath> res = null;

				// check if whole object is tainted (happens with strings, for example:)
				if (!isExecutorExecute
						&& !ap.isStaticFieldRef()
						&& !callee.isStatic()) {
					assert ie instanceof InstanceInvokeExpr;
					InstanceInvokeExpr vie = (InstanceInvokeExpr) ie;
					if (aliasing.mayAlias(vie.getBase(), ap.getPlainValue()))
						if (manager.getTypeUtils().hasCompatibleTypesForCall(ap, callee.getDeclaringClass())) {
							if (res == null) res = new HashSet<AccessPath>();

							// Get the "this" local if we don't have it yet
							if (thisLocal == null)
								thisLocal = callee.getActiveBody().getThisLocal();

							res.add(ap.copyWithNewValue(null, thisLocal));
						}
				}
				// staticfieldRefs must be analyzed even if they are not part of the params:
				else if (ap.isStaticFieldRef()) {
					if (res == null) res = new HashSet<AccessPath>();
					res.add(ap);
				}

				//special treatment for clinit methods - no param mapping possible
				if (isExecutorExecute) {
					if (aliasing.mayAlias(ie.getArg(0), ap.getPlainValue())) {
						if (res == null) res = new HashSet<AccessPath>();
						res.add(ap.copyWithNewValue(null, callee.getActiveBody().getThisLocal()));
					}
				}
				else if (callee.getParameterCount() > 0) {
					assert callee.getParameterCount() == ie.getArgCount();
					// check if param is tainted:
					for (int i = 0; i < ie.getArgCount(); i++) {
						if (aliasing.mayAlias(ie.getArg(i), ap.getPlainValue())) {
							if (res == null) res = new HashSet<AccessPath>();

							// Get the parameter locals if we don't have them yet
							if (paramLocals == null)
								paramLocals = callee.getActiveBody().getParameterLocals().toArray(
										new Local[callee.getParameterCount()]);

							AccessPath newAP = ap.copyWithNewValue(null, paramLocals[i]);
							if (newAP != null)
								res.add(newAP);
						}
					}
				}
				return res;
			}
		};
	}

	@Override
	public boolean autoAddZero() {
		return false;
	}
	
	/**
	 * Gets the results of the data flow analysis
	 */
    public TaintPropagationResults getResults(){
   		return this.results;
	}

	private DataFlowNode useApTofindDataFlowGraph(AccessPath ap, Unit stmt) {
		SootMethod caller = manager.getICFG().getMethodOf(stmt);
		Value base = ap.getPlainValue();
		SootField field1 = ap.getFirstField();
		return useBaseAndFieldTofindDataFlowGraph(base, field1, stmt);
	}

	private DataFlowNode useBaseTofindDataFlowGraph(Value base, Unit stmt) {
		return useBaseAndFieldTofindDataFlowGraph(base, null, stmt);
	}
//	private DataFlowNode useValueTofindDataFlowGraph(Value value, SootMethod caller) {
//		Pair<Value, SootField> pair = Infoflow.getBaseAndField(value);
//		return useBaseAndFieldTofindDataFlowGraph(pair.getO1(), pair.getO2(), caller);
//	}

	private DataFlowNode useValueTofindDataFlowGraph(Value value, Unit stmt) {
		SootMethod caller = manager.getICFG().getMethodOf(stmt);
		Pair<Value, SootField> pair = InnerBBFastBuildDFGSolver.getBaseAndField(value);
		return useBaseAndFieldTofindDataFlowGraph(pair.getO1(), pair.getO2(), stmt);
	}

//	private DataFlowNode useBaseAndFieldTofindDataFlowGraph(Value base, SootField field ,Unit stmt ) {
//		SootMethod caller = manager.getICFG().getMethodOf(stmt);
//		 return useBaseAndFieldTofindDataFlowGraph(base, field, caller);
//
//	}

	private DataFlowNode useBaseAndFieldTofindDataFlowGraph(Value base, SootField field, Unit stmt ) {
		SootMethod caller = manager.getICFG().getMethodOf(stmt);
		if(field == null)
			field = DataFlowNode.baseField;

		DFGEntryKey key = new DFGEntryKey(stmt, base, field);

		if(!manager.getDfg().containsKey(caller)) return null;
		Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> l1 = manager.getDfg().get(caller);
		if(!l1.containsKey(base)) return null;
		Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2 = l1.get(base);
		if(!l2.containsKey(key)) return null;
		Pair<BaseInfoStmt, DataFlowNode> pair = l2.get(key);
		if(pair == null) return null;

		return pair.getO2();
	}
    
}
