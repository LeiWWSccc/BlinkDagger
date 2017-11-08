package soot.jimple.infoflow.sparseop.dataflowgraph;

import heros.solver.Pair;
import soot.SootField;
import soot.jimple.infoflow.sparseop.basicblock.BasicBlock;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;

import java.util.*;

/**
 * @author wanglei
 */
public class BaseInfoStmtSet {
    Set<BaseInfoStmt> varInfoSets = new HashSet<>();


    public void add(BaseInfoStmt varInfo) {
        varInfoSets.add(varInfo);
    }
    public void addAll(List<BaseInfoStmt> varInfo) {
        varInfoSets.addAll(varInfo);
    }

    public  Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>  solve() {
        HashMap<BasicBlock, Set<BaseInfoStmt>> bbToBaseInfoMap = new HashMap<>();

        //System.out.println("CC1");
        for(BaseInfoStmt varInfo : varInfoSets) {
            BasicBlock bb = varInfo.bb;
            Set<BaseInfoStmt> set = null;
            if(bbToBaseInfoMap.containsKey(bb)){
                set =  bbToBaseInfoMap.get(bb);
            }else {
                set = new HashSet<BaseInfoStmt>();
                bbToBaseInfoMap.put(bb, set);
            }
            set.add(varInfo);
        }
        //System.out.println("CC2");
        BaseInfoStmtCFG baseCFG = new BaseInfoStmtCFG(bbToBaseInfoMap);
        baseCFG.solve();
        int count = 0;

        Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>> seed = new HashMap<>();
        //System.out.println("CC3");
       // Set<Pair<VariableInfo, DataFlowNode>> seed = new HashSet<>();

        for(BaseInfoStmt baseInfo : varInfoSets) {
            if(baseInfo.leftField != null) {
               // Unit u = baseInfo.stmt;
                DataFlowNode dataFlowNode = DataFlowNodeFactory.v().createDataFlowNode
                        (baseInfo.stmt, baseInfo.base, baseInfo.leftField, true);
                seed.put(new DFGEntryKey(baseInfo.stmt, baseInfo.base, baseInfo.leftField),
                        new Pair<BaseInfoStmt, DataFlowNode>(baseInfo, dataFlowNode));
               // seed.add(new Pair<VariableInfo, DataFlowNode>(baseInfo, dataFlowNode));
            }

            if(baseInfo.argsFields !=null) {
                for(int i = 0; i < baseInfo.argsFields.length; i++) {
                    DataFlowNode dataFlowNode = DataFlowNodeFactory.v().createDataFlowNode
                            (baseInfo.stmt, baseInfo.base, baseInfo.argsFields[i], true);
                    seed.put(new DFGEntryKey(baseInfo.stmt, baseInfo.base, baseInfo.argsFields[i]),
                            new Pair<BaseInfoStmt, DataFlowNode>(baseInfo, dataFlowNode));
                }
            }


        }
        Set<Pair<BaseInfoStmt, DataFlowNode>> tmp = new HashSet<>(seed.values());
        //System.out.println("CC4");
        computeDataflow(tmp);
        //System.out.println("CC5");
        return seed;

    }

    private void computeDataflow(Set<Pair<BaseInfoStmt, DataFlowNode>> seed) {

        Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > visited = new HashMap<>();
        Queue<Pair<BaseInfoStmt, DataFlowNode>> worklist = new LinkedList<>();
        worklist.addAll(seed);
        while(!worklist.isEmpty()) {
            Pair<BaseInfoStmt, DataFlowNode> curPath = worklist.poll();
            if(visited.containsKey(curPath))
                continue;
            visited.put(curPath, curPath.getO2());

            BaseInfoStmt curBaseInfo = curPath.getO1();
            DataFlowNode curNode = curPath.getO2();

            if(curBaseInfo.Succs == null)
                continue;
            for(BaseInfoStmt next : curBaseInfo.Succs) {
                Set<DataFlowNode> ret = flowFunction(next, curNode, visited);

                for(DataFlowNode dataFlowNode : ret) {
                    worklist.offer(new Pair<BaseInfoStmt, DataFlowNode>(next, dataFlowNode));
                }
            }

        }
    }

//    private Set<DataFlowNode> callFlowFunction(Unit returnStmt, DataFlowNode source, ) {
//
//    }

//    private Set<DataFlowNode> returnFlowFunction(Unit returnStmt, DataFlowNode source, ) {
//
//
//    }


    private  Set<DataFlowNode> flowFunction(BaseInfoStmt target, DataFlowNode source, Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited) {
        Set<DataFlowNode> res = new HashSet<>();

        if(target.base == null) {
            //return
            DataFlowNode returnNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, null, false);
            //newNode = getNewDataFlowNode(target, newNode, visited);
            source.setSuccs(DataFlowNode.baseField, returnNode);
            //res.add(newNode);
            return res;
        }

        SootField baseField = DataFlowNode.baseField;

        SootField sourceField = source.getField();

        SootField targetLeftField = target.leftField;
        SootField[] targetRightFields = target.rightFields;
        SootField[] targetArgFields = target.argsFields;

        boolean isKillSource = false;
        DataFlowNode newNode = null;

        if(sourceField != baseField) {
            //(1) source like  :  a.f1

            if(targetLeftField != null) {
                //(1.1) like  a =  xxx; or  a.f1 = xxx; or a.f2 = xxx;
                if(targetLeftField == baseField || sourceField.equals(targetLeftField)) {
                    //(1.1.1) a = xxx;  source : a.f1  , kill source
                    //(1.1.2) a.f1 = xxx;   source : a.f1 , kill source
                    isKillSource = true;
                }else {
                    //(1.1.3) a.f2 = xx; source : a.f1  , do nothing.
                }

            }

            if(targetRightFields != null) {
                //(1.2) like : xxx = a; or xxx = a.f1; or xxx = a.f2;

                for(int i = 0; i < targetRightFields.length; i++) {
                    SootField right = targetRightFields[i];
                    if(right == baseField ) {
                        //(1.2.1) xxx = a;  source : a.f1 , gen f1 -> <a>
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                        newNode = getNewDataFlowNode(target, newNode, visited);
                        source.setSuccs(sourceField, newNode);
                        res.add(newNode);

                    }else if (right.equals(sourceField)) {
                        //(1.2.2) xxx = a.f1; source : a.f1  , gen f1 -> <a.f1>
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                        newNode = getNewDataFlowNode(target, newNode, visited);
                        source.setSuccs(sourceField, newNode);
                    }else {
                        //(1.2.3) xxx= a.f2  source : a.f1, do nothing.

                    }
                }
            }

            if(targetArgFields != null) {
                for(int i = 0; i < targetArgFields.length; i++) {
                    SootField arg = targetArgFields[i];
                    if(arg == baseField ) {
                        //(1.3.1) foo(a);    source : a.f1 , gen new a.f1
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode, visited);
                        source.setSuccs(sourceField, newNode);

                    }else if (arg.equals(sourceField)) {
                        //(1.3.2) foo(a.f1); source : a.f1  , gen new a.f1
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode, visited);
                        source.setSuccs(sourceField, newNode);
                    }else {
                        //(1.3.3) foo(a.f2); source : a.f1, do nothing.

                    }
                }
            }

        }else if(sourceField != null) {
            //(2) source like  :  a

            if(targetLeftField != null) {
                //(2.1) like  a =  xxx; or  a.f1 = xxx; or a.f2 = xxx;
                if(targetLeftField == baseField ) {
                    // a = xxxx;   source : a , kill source
                    isKillSource = true;
                }else {
                    // a.f1 = xx; source : a ,  just kill field f1.
                    source.setKillField(targetLeftField);
                }

            }

            if(targetRightFields != null) {
                //like xxx = a;  or xxx = a.f1 ;
                for(int i = 0; i < targetRightFields.length; i++) {
                    SootField right = targetRightFields[i];
                    if(right == baseField ) {
                        // xxx = a;    source : a , gen new a
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                        newNode = getNewDataFlowNode(target, newNode, visited);
                        source.setSuccs(sourceField, newNode);

                    }else {
                        //(1) xxx = a.f1 ; source : a  , gen new a.f1
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                        newNode = getNewDataFlowNode(target, newNode, visited);
                        source.setSuccs(sourceField, newNode);
                    }
                }
            }

            if(targetArgFields != null) {
                for(int i = 0; i < targetArgFields.length; i++) {
                    SootField arg = targetArgFields[i];
                    if(arg == baseField ) {
                        // foo(a);    source : a , gen "base" -> <a>
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode, visited);
                        source.setSuccs(arg, newNode);

                    }else if (arg.equals(sourceField)) {
                        // foo(a.f1); source : a , gen f1 -> <a.f1>
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode, visited);
                        source.setSuccs(arg, newNode);
                    }
                }
            }

        }else {
            throw new  RuntimeException("source's base field can not be null ");
        }

        if(!isKillSource)
            res.add(source);
        return res;


//
//
//        if(sourceField != null && targetField != null) {
//
//            if(sourceField.equals(targetField)) {
//                if(target.isLeft == false) {
//                    //a.f1   read
//                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
//                    if(visited.containsKey(newNode))
//                        newNode = visited.get(newNode);
//                    source.setSuccs(sourceField, newNode);
//
//                }else {
//                    isKillSource = true;
//                }
//
//            }else {
//                // a.f1  b.f1
//
//            }
//        }else if(sourceField != null) {
//            if(target.isLeft == false) {
//                // b = a ; < a.f > ;
//                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
//                if(visited.containsKey(newNode))
//                    newNode = visited.get(newNode);
//                source.setSuccs(sourceField, newNode);
//            }else {
//                // a = b ; < a.f > ;  kill : a.f
//                isKillSource = true;
//            }
//
//        }else if(targetField != null) {
//
//            if(target.isLeft == false) {
//                // b = a.f ; < a > ;
//                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
//                if(visited.containsKey(newNode))
//                    newNode = visited.get(newNode);
//                source.setSuccs(targetField, newNode);
//
//            }else {
//                // a.f = b ; < a > ;  kill : a.f
////                isKillSource = true;
//                source.setKillField(targetField);
//            }
//
//        }else {
//            if(target.isLeft == false) {
//                // a = "xxx";  source : < a >
//                // b = a ; < a > ;    use : a
//                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
//                if(visited.containsKey(newNode))
//                    newNode = visited.get(newNode);
//                source.setSuccs(sourceField, newNode);
//            }else {
//
//                // a = "xxx";  source : < a >
//                //target:  a = b ;  < a > ;  kill : a
//                isKillSource = true;
//            }
//
//        }

    }


    private DataFlowNode getNewDataFlowNode(BaseInfoStmt baseInfoStmt, DataFlowNode oldNode,
                                            Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited) {
        Pair<BaseInfoStmt, DataFlowNode> key = new Pair<>(baseInfoStmt, oldNode);
        if(visited.containsKey(key))
            return visited.get(key);
        else
            return oldNode;

    }

}
