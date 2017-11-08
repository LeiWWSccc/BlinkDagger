package soot.jimple.infoflow.sparseop.dataflowgraph;

import heros.solver.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sparseop.basicblock.BasicBlock;
import soot.jimple.infoflow.sparseop.basicblock.BasicBlockGraph;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.util.BaseSelector;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.callgraph.ReachableMethods;

import java.util.*;

/**
 * @author wanglei
 */
public class InnerBBFastBuildDFGSolver {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private IInfoflowCFG iCfg;
    protected InfoflowConfiguration config = new InfoflowConfiguration();

    public InnerBBFastBuildDFGSolver(IInfoflowCFG iCfg ) {
        this.iCfg = iCfg;
    }

    public Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> solve() {


        Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg = new HashMap<>();

        for (SootMethod sm : getMethodsForSeeds(iCfg))
            scanMethodDoPreAnalysis(sm, dfg);
        return dfg;
    }

    private enum ValueType {
        Left,
        Right,
        Arg
    }



    public static Pair<Value, SootField> getBaseAndField(Value value) {
        Value rightBase = null;
        SootField rightField = null;

        if(value instanceof Local) {
            rightBase = value;
        }else if(value instanceof FieldRef) {
            if(value instanceof  InstanceFieldRef) {
                //Value base = BaseSelector.selectBase(left, true);
                rightBase = ((InstanceFieldRef) value).getBase();
                rightField = ((InstanceFieldRef)value).getField();
            }

        }
        return new Pair<>(rightBase, rightField);
    }

    private void scanMethodDoPreAnalysis(SootMethod m, Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg) {

        if (m.hasActiveBody()) {
            // Check whether this is a system class we need to ignore
            final String className = m.getDeclaringClass().getName();
            if (config.getIgnoreFlowsInSystemPackages()
                    && SystemClassHandler.isClassInSystemPackage(className))
                return ;

            PatchingChain<Unit> units = m.getActiveBody().getUnits();
//			logger.info("getName :" + m.getName());
//			logger.info("getBytecodeParms :" + m.getBytecodeParms());
//			logger.info("getSubSignature :" + m.getSubSignature());
//			logger.info("getParameterCount :" + m.getParameterCount());
//			logger.info("getName :" + m.getActiveBody());
//
            //BriefUnitGraph ug  = new BriefUnitGraph(m.getActiveBody());

            //debug
            System.out.println(m.getActiveBody());

            // first we build the basic block of the method, which is used for opt
            // compute each index of Unit in their basic block
            // use unitToBBMap,unitToInnerBBIndexMap to store the Map (unit -> BB , unit -> index )
            BasicBlockGraph bbg = new BasicBlockGraph(iCfg, m) ;
            Map<Unit, BasicBlock> unitToBBMap =  bbg.getUnitToBBMap();
            Map<Unit, Integer> unitToInnerBBIndexMap = bbg.getUnitToIndexMap();
            List<BasicBlock> bbgTails = bbg.getTails();

            List<BaseInfoStmt> returnStmtList = new ArrayList<>();
            for(BasicBlock bb  : bbgTails) {
                Unit tail = bb.getTail();
                Integer innerBBIdx = unitToInnerBBIndexMap.get(tail);
                BaseInfoStmt returnStmt = BaseInfoStmtFactory.v().
                        createVariableInfo(null, null, null, null, bb, innerBBIdx, tail);
                returnStmtList.add(returnStmt);
            }

            //bbg.computeLeaders(iCfg, m);

            // foreach base
            // 	do
            //		for each basic block
            //			preinfo

            Map<Value , BaseInfoStmtSet> varInfoSetGbyBaseMap = new HashMap<>();

            // Collect all units' variables , store it in VariableInfo class,
            // then group by the VariableInfo instances by their base value
            // store them into varInfoSetGbyBaseMap HashMap
            // [Read]   S1: b = a      ::   Base : a -> { <S1:a> }
            // [Write]  S2: a = b      ::   Base : a -> { <S1:a> <S2:a> }
            // [Load]   S3: c = a.f1   ::   Base : a -> { <S1:a> <S2:a> <S3:a.f1> }
            // [Store]  S4: a.f2 = d   ::   Base : a -> { <S1:a> <S2:a> <S3:a.f1> <S4:a.f2> }
            // [invoke] s5: foo(a.f2)  ::   Base : a -> { <S1:a> <S2:a> <S3:a.f1> <S4:a.f2> <S5:a.f2>}
            for (Unit u : units) {
                if(!(u instanceof Stmt))
                    continue;
                Stmt stmt = (Stmt) u;
                BasicBlock bb = unitToBBMap.get(u);
                Integer innerBBIdx = unitToInnerBBIndexMap.get(u);

                InvokeExpr ie = (stmt != null && stmt.containsInvokeExpr())
                        ? stmt.getInvokeExpr() : null;

                List<Pair<Value, SootField>> rights = new ArrayList<>();

                Map<Value, Set<Pair<ValueType, SootField>>> baseTpEachVarInfoMap = new HashMap<>();

                if(stmt instanceof AssignStmt) {
                    final AssignStmt assignStmt = (AssignStmt) stmt;
                    Value left = assignStmt.getLeftOp();
                    Value right = assignStmt.getRightOp();
                    final Value[] rightVals = BaseSelector.selectBaseList(right, true);

                    //get left value :
                    Value leftBase = null;
                    SootField leftField = null;
                    if(left instanceof Local) {
                        leftBase = left;
                    }else if(left instanceof FieldRef) {
                        if(left instanceof InstanceFieldRef) {
                            //Value base = BaseSelector.selectBase(left, true);
                            leftBase = ((InstanceFieldRef) left).getBase();
                            leftField = ((InstanceFieldRef)left).getField();
                        }

                    }
                    if(leftBase != null) {
                        Set<Pair<ValueType, SootField>> tmpSet = null;
                        if(baseTpEachVarInfoMap.containsKey(leftBase)) {
                            tmpSet = baseTpEachVarInfoMap.get(leftBase);
                        }else {
                            tmpSet = new HashSet<>();
                            baseTpEachVarInfoMap.put(leftBase, tmpSet);
                        }
                        tmpSet.add(new Pair<ValueType, SootField>(ValueType.Left, leftField));

                    }

//					if(leftBase != null) {
//						VariableInfoSet leftVariableInfoSet = null;
//						if(varInfoSetGbyBaseMap.containsKey(leftBase)){
//							leftVariableInfoSet =  varInfoSetGbyBaseMap.get(leftBase);
//						}else {
//							leftVariableInfoSet = new VariableInfoSet();
//							varInfoSetGbyBaseMap.put(leftBase, leftVariableInfoSet);
//						}
//						leftVariableInfoSet.add(new VariableInfo(left, leftField, bb, innerBBIdx, stmt, true));
//					}

                    //get right value :
                    Value rightBase = null;
                    SootField rightField = null;
                    for(Value rightVal : rightVals) {

                        if(rightVal instanceof Local) {
                            rightBase = rightVal;
                        }else if(rightVal instanceof FieldRef) {
                            if(rightVal instanceof  InstanceFieldRef) {
                                //Value base = BaseSelector.selectBase(left, true);
                                rightBase = ((InstanceFieldRef) rightVal).getBase();
                                rightField = ((InstanceFieldRef)rightVal).getField();
                            }

                        }

                        if(rightBase != null) {
                            Set<Pair<ValueType, SootField>> tmpSet = null;
                            if(baseTpEachVarInfoMap.containsKey(rightBase)) {
                                tmpSet = baseTpEachVarInfoMap.get(rightBase);
                            }else {
                                tmpSet = new HashSet<>();
                                baseTpEachVarInfoMap.put(rightBase, tmpSet);
                            }
                            tmpSet.add(new Pair<ValueType, SootField>(ValueType.Right, rightField));
                        }
//						if(rightBase != null) {
//							VariableInfoSet rightVariableInfoSet = null;
//							if(varInfoSetGbyBaseMap.containsKey(rightBase)){
//								rightVariableInfoSet =  varInfoSetGbyBaseMap.get(rightBase);
//							}else {
//								rightVariableInfoSet = new VariableInfoSet();
//								varInfoSetGbyBaseMap.put(rightBase, rightVariableInfoSet);
//							}
//							rightVariableInfoSet.add(new VariableInfo(rightVal, rightField, bb, innerBBIdx, stmt, false));
//						}

                    }

//					logger.info("Right :" + right.toString());
//					logger.info("Left :" + left.toString());
//					List<ValueBox> list = left.getUseBoxes();
//					logger.info("valueBox: " + list);
//					logger.info(s.toString());
                }else if(stmt instanceof IdentityStmt) {
                    IdentityStmt is = ((IdentityStmt)u);
                    if (is.getRightOp() instanceof ParameterRef){
                        ParameterRef pr = (ParameterRef) is.getRightOp();
                        Pair<Value, SootField> pair = getBaseAndField(is.getLeftOp());
                        Value leftBase = pair.getO1();
                        SootField leftField = pair.getO2();
                        if(leftBase != null) {
                            Set<Pair<ValueType, SootField>> tmpSet = null;
                            if(baseTpEachVarInfoMap.containsKey(leftBase)) {
                                tmpSet = baseTpEachVarInfoMap.get(leftBase);
                            }else {
                                tmpSet = new HashSet<>();
                                baseTpEachVarInfoMap.put(leftBase, tmpSet);
                            }
                            tmpSet.add(new Pair<ValueType, SootField>(ValueType.Left, leftField));

                        }
                    }

                }else if(stmt instanceof InvokeStmt) {
//
//					final Stmt iCallStmt = (Stmt) stmt;
//					final InvokeExpr invExpr = iCallStmt.getInvokeExpr();
//
//					//rights = new Pair<Value, SootField> [invExpr.getArgCount()];
//					for (int i = 0; i < invExpr.getArgCount(); i++) {
//						Pair<Value, SootField> args = getBaseAndField(invExpr.getArg(i));
//						rights.add(args);
//					}

                    //final boolean hasValidCallees = hasValidCallees(stmt);
                }

                if(ie != null) {

                    if((stmt instanceof IdentityStmt) &&
                            ((IdentityStmt)u).getRightOp() instanceof ThisRef) {

                    }
                    if(ie instanceof InstanceInvokeExpr) {
                        InstanceInvokeExpr vie = (InstanceInvokeExpr) ie;
                        Pair<Value, SootField> args = getBaseAndField(vie.getBase());
                        Value base = args.getO1();
                        SootField field = args.getO2();

                        if(base != null) {
                            Set<Pair<ValueType, SootField>> tmpSet = null;
                            if(baseTpEachVarInfoMap.containsKey(base)) {
                                tmpSet = baseTpEachVarInfoMap.get(base);
                            }else {
                                tmpSet = new HashSet<>();
                                baseTpEachVarInfoMap.put(base, tmpSet);
                            }
                            tmpSet.add(new Pair<ValueType, SootField>(ValueType.Arg, field));
                        }
                    }

                    for (int i = 0; i < ie.getArgCount(); i++) {
                        Pair<Value, SootField> args = getBaseAndField(ie.getArg(i));
                        Value base = args.getO1();
                        SootField field = args.getO2();

                        if(base != null) {
                            Set<Pair<ValueType, SootField>> tmpSet = null;
                            if(baseTpEachVarInfoMap.containsKey(base)) {
                                tmpSet = baseTpEachVarInfoMap.get(base);
                            }else {
                                tmpSet = new HashSet<>();
                                baseTpEachVarInfoMap.put(base, tmpSet);
                            }
                            tmpSet.add(new Pair<ValueType, SootField>(ValueType.Arg, field));
                        }
                    }
                }

                for(Map.Entry<Value, Set<Pair<ValueType, SootField>>> entry : baseTpEachVarInfoMap.entrySet()) {
                    Value base = entry.getKey();
                    SootField leftField = null;
                    List<SootField> rightFields = new ArrayList<>();
                    List<SootField> argsFields = new ArrayList<>();

                    Set<Pair<ValueType, SootField>> tmpSet = entry.getValue();

                    for(Pair<ValueType, SootField> pair : tmpSet) {
                        ValueType type = pair.getO1();
                        SootField field = pair.getO2();
                        if(field == null)
                            field = DataFlowNode.baseField;

                        switch (type) {
                            case Left :
                                leftField = field;

                                break;
                            case Right:
                                rightFields.add(field);
                                break;
                            case Arg:
                                argsFields.add(field);
                                break;
                        }
                    }

                    BaseInfoStmtSet BaseInfoStmtSet = null;
                    if(varInfoSetGbyBaseMap.containsKey(base)){
                        BaseInfoStmtSet =  varInfoSetGbyBaseMap.get(base);
                    }else {
                        BaseInfoStmtSet = new BaseInfoStmtSet();
                        BaseInfoStmtSet.addAll(returnStmtList);
                        varInfoSetGbyBaseMap.put(base, BaseInfoStmtSet);
                    }
                    BaseInfoStmtSet.add(
                            BaseInfoStmtFactory.v().createVariableInfo(base,
                                    leftField, rightFields, argsFields, bb, innerBBIdx, stmt));

                }


            }


            int count = 0;
//			for(VariableInfoSet variableInfoSet : varInfoSetGbyBaseMap.values()) {
//				Set<Pair<VariableInfo, DataFlowNode>> res = variableInfoSet.solve(bbg.getHeads());
//				//System.out.println(count++);
//			}

            Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> baseToVarInfoMap = new HashMap<>();

            for(Map.Entry<Value, BaseInfoStmtSet> entry : varInfoSetGbyBaseMap.entrySet()) {
                Value base = entry.getKey();
                BaseInfoStmtSet baseInfoStmtSet = entry.getValue();
                Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>  res = baseInfoStmtSet.solve();
                baseToVarInfoMap.put(base, res);

            }
            dfg.put(m, baseToVarInfoMap);

        }
        return ;
    }



    private Collection<SootMethod> getMethodsForSeeds(IInfoflowCFG icfg) {
        List<SootMethod> seeds = new LinkedList<SootMethod>();
        // If we have a callgraph, we retrieve the reachable methods. Otherwise,
        // we have no choice but take all application methods as an approximation
        if (Scene.v().hasCallGraph()) {
            List<MethodOrMethodContext> eps = new ArrayList<MethodOrMethodContext>(Scene.v().getEntryPoints());
            ReachableMethods reachableMethods = new ReachableMethods(Scene.v().getCallGraph(), eps.iterator(), null);
            reachableMethods.update();
            for (Iterator<MethodOrMethodContext> iter = reachableMethods.listener(); iter.hasNext();)
                seeds.add(iter.next().method());
        }
        else {
            long beforeSeedMethods = System.nanoTime();
            Set<SootMethod> doneSet = new HashSet<SootMethod>();
            for (SootMethod sm : Scene.v().getEntryPoints())
                getMethodsForSeedsIncremental(sm, doneSet, seeds, icfg);
            logger.info("Collecting seed methods took {} seconds", (System.nanoTime() - beforeSeedMethods) / 1E9);
        }
        return seeds;
    }


    private void getMethodsForSeedsIncremental(SootMethod sm,
                                               Set<SootMethod> doneSet, List<SootMethod> seeds, IInfoflowCFG icfg) {
        assert Scene.v().hasFastHierarchy();
        if (!sm.isConcrete() || !sm.getDeclaringClass().isApplicationClass() || !doneSet.add(sm))
            return;
        seeds.add(sm);
        for (Unit u : sm.retrieveActiveBody().getUnits()) {
            Stmt stmt = (Stmt) u;
            if (stmt.containsInvokeExpr())
                for (SootMethod callee : icfg.getCalleesOfCallAt(stmt))
                    getMethodsForSeedsIncremental(callee, doneSet, seeds, icfg);
        }
    }
}
