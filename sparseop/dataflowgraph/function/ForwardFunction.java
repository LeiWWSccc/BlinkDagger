package soot.jimple.infoflow.sparseop.dataflowgraph.function;

import heros.solver.Pair;
import soot.SootField;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNodeFactory;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author wanglei
 */
public class ForwardFunction extends AbstractFunction {

    //private final  Map<Pair<BaseInfoStmt, DataFlowNode>,DataFlowNode > backwardSeed;

    Map<DFGEntryKey, Set<DataFlowNode>> returnInfo = new HashMap<>();

    ForwardFunction(Map<Pair<BaseInfoStmt, DataFlowNode>, DataFlowNode > visited) {
        super(visited);
       // this.backwardSeed = backwardSeed;
    }

//    protected DataFlowNode getNewReturnNode(BaseInfoStmt baseInfoStmt, DataFlowNode oldNode) {
//        Pair<BaseInfoStmt, DataFlowNode> key = new Pair<>(baseInfoStmt, oldNode);
//        if(backwardSeed.containsKey(key))
//            return backwardSeed.get(key);
//        else
//            return oldNode;
//    }


    public Set<Pair<BaseInfoStmt, DataFlowNode>> flowFunction(BaseInfoStmt target, DataFlowNode source) {
        Set<Pair<BaseInfoStmt, DataFlowNode>> res = new HashSet<>();

        if(target.base == null) {
            //return
            DataFlowNode returnNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, null, false);
            returnNode = getNewDataFlowNode(target, returnNode);
            source.setSuccs(DataFlowNode.baseField, returnNode);
            //res.add(newNode);
            DFGEntryKey key =  new DFGEntryKey(returnNode.getStmt(), source.getValue(), source.getField());

//            DataFlowNode newBackNode = getNewReturnNode(target, source.clone());
//
//            Set<DataFlowNode> tmp;
//            if(returnInfo.containsKey(key)) {
//                tmp = returnInfo.get(key);
//            }else {
//                tmp = new HashSet<>();
//            }
//            tmp.add(newBackNode);
//            returnInfo.put(key, tmp);
//            return res;
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
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(sourceField, newNode);

                    }else if (right.equals(sourceField)) {
                        //(1.2.2) xxx = a.f1; source : a.f1  , gen f1 -> <a.f1>
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                        newNode = getNewDataFlowNode(target, newNode);
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
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(sourceField, newNode);

                    }else if (arg.equals(sourceField)) {
                        //(1.3.2) foo(a.f1); source : a.f1  , gen new a.f1
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode);
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
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(sourceField, newNode);

                    }else {
                        //(1) xxx = a.f1 ; source : a  , gen new a.f1
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, right, false);
                        newNode = getNewDataFlowNode(target, newNode);
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
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(arg, newNode);

                    }else if (arg.equals(sourceField)) {
                        // foo(a.f1); source : a , gen f1 -> <a.f1>
                        newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.base, arg, false);
                        newNode = getNewDataFlowNode(target, newNode);
                        source.setSuccs(arg, newNode);
                    }
                }
            }

        }else {
            throw new  RuntimeException("source's base field can not be null ");
        }

        if(!isKillSource)
            addResult(res, target, source);
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

}
