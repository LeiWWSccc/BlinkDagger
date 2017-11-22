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
import soot.jimple.infoflow.sparseop.basicblock.UnitOrderComputing;
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

    private Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg = new HashMap<>();

    private Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> backwardDfg = new HashMap<>();

    private Map<SootMethod,  Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>>> returnInfo = new HashMap<>();

    private Map<SootMethod, UnitOrderComputing> unitOrderComputingMap = new HashMap<>();

    public Map<SootMethod, UnitOrderComputing> getUnitOrderComputingMap() {
        return unitOrderComputingMap;
    }

    public InnerBBFastBuildDFGSolver(IInfoflowCFG iCfg ) {
        this.iCfg = iCfg;
    }

    public Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> getDfg() {
        return dfg;
    }

    public Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> getBackwardDfg() {
        return backwardDfg;
    }

    public  Map<SootMethod,  Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>>> getReturnInfo() {
        return returnInfo;
    }

    final public static String[] debugFunc = {"main","funcA","void <init>(com.flurry.android.FlurryAgent)"};
    //final public static String[] debugFunc = {"<com.geinimi.c.g: void a(java.lang.String,java.util.Map,org.apache.http.util.ByteArrayBuffer)>"};
    //public static String[] debugFunc = {"<com.qoEhMJg.iQGMdWhluiUGs: void onReceive(android.content.Context,android.content.Intent)>"};
//    public static String[] debugFunc = {"<com.qoEhMJg.u: void run()>"};

    public void solve() {
        for (SootMethod sm : getMethodsForSeeds(iCfg))
            buildDFGForeachSootMethod(sm);
    }


    private enum ValueType {
        Left,
        Right,
        Arg
    }


    public  Map<BasicBlock, Set<BasicBlock>> computeBBOrder(BasicBlockGraph bbg) {

        Map<BasicBlock, Set<BasicBlock>> bbToReachableBbMap = new HashMap<>();
        List<BasicBlock> bbList =  bbg.getBlocks();
        for(BasicBlock bb : bbList) {
            Set<BasicBlock> reached = new HashSet<>();
            Queue<BasicBlock> worklist = new LinkedList<>();
            worklist.add(bb);
            while(!worklist.isEmpty()) {
                BasicBlock cur = worklist.poll();
                if(reached.contains(cur))
                    continue;
                reached.add(cur);
                for(BasicBlock next : cur.getSuccs()) {
                    worklist.offer(next);
                }
            }

            bbToReachableBbMap.put(bb, reached);
        }
        return bbToReachableBbMap;

    }

    private void buildDFGForeachSootMethod(SootMethod m) {

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
            for(String func :debugFunc) {
                if(m.toString().contains(func))
                    System.out.println(m.getActiveBody());
            }
            //System.out.println(m.getActiveBody());

            // first we build the basic block of the method, which is used for opt
            // compute each index of Unit in their basic block
            // use unitToBBMap,unitToInnerBBIndexMap to store the Map (unit -> BB , unit -> index )
            BasicBlockGraph bbg = new BasicBlockGraph(iCfg, m) ;
            Map<Unit, BasicBlock> unitToBBMap =  bbg.getUnitToBBMap();
            Map<Unit, Integer> unitToInnerBBIndexMap = bbg.getUnitToIndexMap();
            Map<BasicBlock, Set<BasicBlock>> bbOrderMap =  computeBBOrder(bbg);

            unitOrderComputingMap.put(m, new UnitOrderComputing(unitToBBMap, unitToInnerBBIndexMap, bbOrderMap));

            List<BasicBlock> bbgTails = bbg.getTails();
            List<BasicBlock> bbgHeads = bbg.getHeads();

            List<BaseInfoStmt> headStmtList = new ArrayList<>();
            for(BasicBlock bb  : bbgTails) {
                if(bb.getPreds().size() == 0) {
                    Unit head = bb.getHead();
                    Integer innerBBIdx = unitToInnerBBIndexMap.get(head);
                    BaseInfoStmt headStmt = BaseInfoStmtFactory.v().
                            createVariableInfo(null, null, null, null, bb, innerBBIdx, head);
                    headStmtList.add(headStmt);
                }
            }

            List<BaseInfoStmt> returnStmtList = new ArrayList<>();
            for(BasicBlock bb  : bbgTails) {
                if(bb.getSuccs().size() == 0) {
                    Unit tail = bb.getTail();
                    Integer innerBBIdx = unitToInnerBBIndexMap.get(tail);
                    BaseInfoStmt returnStmt = BaseInfoStmtFactory.v().
                            createVariableInfo(null, null, null, null, bb, innerBBIdx, tail);
                    returnStmtList.add(returnStmt);
                }
            }


            Set<Pair<Value, SootField>> paramAndThis = new HashSet<>();

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
                    Pair<Value, SootField> ret = getBaseAndField(left);
                    Value leftBase = ret.getO1();
                    SootField leftField = ret.getO2();

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

                        Pair<Value, SootField> pair = getBaseAndField(rightVal);
                        rightBase = pair.getO1();
                        rightField = pair.getO2();

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
                    if (is.getRightOp() instanceof ParameterRef || is.getRightOp() instanceof ThisRef){
                        Pair<Value, SootField> pair = getBaseAndField(is.getLeftOp());
                        Value leftBase = pair.getO1();
                        SootField leftField = pair.getO2();

                        paramAndThis.add(pair);

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
                        BaseInfoStmtSet = new BaseInfoStmtSet(m, base, returnStmtList, paramAndThis);
                        BaseInfoStmtSet.addAll(returnStmtList);
                        BaseInfoStmtSet.addAll(headStmtList);
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

            Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> backwardBaseToVarInfoMap = new HashMap<>();

            Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>> returnInfoMap = new HashMap<>();

            for(Map.Entry<Value, BaseInfoStmtSet> entry : varInfoSetGbyBaseMap.entrySet()) {
                Value base = entry.getKey();
                BaseInfoStmtSet baseInfoStmtSet = entry.getValue();
                Pair<Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>  resPair = baseInfoStmtSet.solve();
                baseToVarInfoMap.put(base, resPair.getO1());
                backwardBaseToVarInfoMap.put(base, resPair.getO2());
                returnInfoMap.put(base, baseInfoStmtSet.getReturnInfo());

            }
            //printer(m, baseToVarInfoMap, backwardBaseToVarInfoMap);

            dfg.put(m, baseToVarInfoMap);
            backwardDfg.put(m, backwardBaseToVarInfoMap);
            returnInfo.put(m, returnInfoMap);

        }
        return ;
    }

    public static Pair<Value, SootField> getBaseAndField(Value value) {
//        Value rightBase = null;
//        SootField rightField = null;
//
//        if(value instanceof Local) {
//            rightBase = value;
//        }else if(value instanceof FieldRef) {
//            if(value instanceof  InstanceFieldRef) {
//                //Value base = BaseSelector.selectBase(left, true);
//                rightBase = ((InstanceFieldRef) value).getBase();
//                rightField = ((InstanceFieldRef)value).getField();
//            }
//        }
        Value base = null;
        SootField field = null;
        if(value instanceof  BinopExpr) {
            throw new RuntimeException("getBaseAndValue method should't be BinopExpr!");
        }

        if(value instanceof Local) {

            // a   : base : a
            base = value;
        }else if(value instanceof FieldRef) {
            if(value instanceof InstanceFieldRef) {
                //a.f  : base : a  field : f

                //Value base = BaseSelector.selectBase(left, true);
                base = ((InstanceFieldRef) value).getBase();
                field = ((InstanceFieldRef)value).getField();
            }

        }else if (value instanceof ArrayRef) {
            ArrayRef ref = (ArrayRef) value;
            base = (Local) ref.getBase();
            Value rightIndex = ref.getIndex();
        } if(value instanceof LengthExpr) {
            LengthExpr lengthExpr = (LengthExpr) value;
            base = lengthExpr.getOp();
        } else if (value instanceof NewArrayExpr) {
            NewArrayExpr newArrayExpr = (NewArrayExpr) value;
            base = newArrayExpr.getSize();
        }

        return new Pair<>(base, field);
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


    public void printer(SootMethod m, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> baseToVarInfoMap ,  Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> backBaseToVarInfoMap) {
        boolean found = false;
        for(String func : debugFunc) {
            if(m.toString().contains(func))
                found = true;
        }
        if(!found)
            return;

        System.out.println("================Forward  ==========================");
        System.out.print(printInnerMethodBaseInfo(baseToVarInfoMap));
        System.out.println("================Backward ==========================");
        System.out.print(printInnerMethodBaseInfo(backBaseToVarInfoMap));
        System.out.println("===================================================");

    }

    public String printDfg() {
        if(dfg.size() == 0)
            return "Error!";
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> entry : dfg.entrySet()) {
            SootMethod method = entry.getKey();
            sb.append("=============================================================\n");
            sb.append(method.toString() + "\n");
            sb.append("-------------------------------------------------------------\n");
            Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> l1Dfg = entry.getValue();
            for(Map.Entry<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> entry1 : l1Dfg.entrySet()) {
                Value base = entry1.getKey();
                sb.append("BASE[ " + base.toString() + " ]: \n");
                Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2Dfg = entry1.getValue();
                sb.append(subprint(l2Dfg));
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public String printInnerMethodBaseInfo(Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> baseinfo) {

        StringBuilder sb = new StringBuilder();
        sb.append("-------------------------------------------------------------\n");
        for(Map.Entry<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>> entry1 : baseinfo.entrySet()) {
            Value base = entry1.getKey();
            sb.append("BASE[ " + base.toString() + " ]: \n");
            Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2Dfg = entry1.getValue();
            sb.append(subprint(l2Dfg));
            sb.append("\n");
        }
        sb.append("-------------------------------------------------------------\n");
        return sb.toString();
    }

    private String subprint(Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> l2Dfg) {
        StringBuilder sb = new StringBuilder();
        Set<DataFlowNode> visited = new HashSet<>();
        Queue<DataFlowNode> list = new LinkedList<>();
        for(Pair<BaseInfoStmt, DataFlowNode> pair : l2Dfg.values() ) {
            list.offer(pair.getO2());
            visited.add(pair.getO2());
        }
        int count = 1 ;
        while (!list.isEmpty()) {
            DataFlowNode cur = list.poll();
            sb.append("  ("+count +") ");
            count++;
            sb.append(cur.toString() + "\n");
            if(cur.getSuccs() != null) {
                for(Map.Entry<SootField, Set<DataFlowNode>> entry : cur.getSuccs().entrySet()) {
                    SootField f = entry.getKey();
                    String fs ;
                    if(f == DataFlowNode.baseField)
                        fs = "NULL";
                    else
                        fs = f.toString();

                    sb.append("      " + fs + "  ->  \n");
                    Set<DataFlowNode> nextSet = entry.getValue();
                    for(DataFlowNode next : nextSet) {
                        sb.append("         " + next + "\n");
                        if(!visited.contains(next)) {
                            list.offer(next);
                            visited.add(next);
                        }
                    }
                    sb.append("\n");

                }
            }
            if(cur.getKillFields() != null) {
                sb.append("      Kill Sets:\n");
                sb.append("        " + cur.getKillFields().toString() + "\n");
            }

            sb.append("\n");

        }
        return sb.toString();
    }
}
