package soot.jimple.infoflow.sparseop.dataflowgraph;

import heros.solver.Pair;
import soot.SootField;
import soot.jimple.infoflow.sparseop.basicblock.BasicBlock;

import java.util.*;

/**
 * @author wanglei
 */
public class VariableInfoSet {
    Set<VariableInfo> varInfoSets = new HashSet<>();


    public void add(VariableInfo varInfo) {
        varInfoSets.add(varInfo);
    }

    public Set<Pair<VariableInfo, DataFlowNode>> solve(List<BasicBlock> heads) {
        HashMap<BasicBlock, Set<VariableInfo>> bbToBaseInfoMap = new HashMap<>();

        //System.out.println("CC1");
        for(VariableInfo varInfo : varInfoSets) {
            BasicBlock bb = varInfo.bb;
            Set<VariableInfo> set = null;
            if(bbToBaseInfoMap.containsKey(bb)){
                set =  bbToBaseInfoMap.get(bb);
            }else {
                set = new HashSet<VariableInfo>();
                bbToBaseInfoMap.put(bb, set);
            }
            set.add(varInfo);
        }
        //System.out.println("CC2");
        BaseDataFlowCFG  baseCFG = new BaseDataFlowCFG(bbToBaseInfoMap);
        baseCFG.solve();
        int count = 0;

//        Map<Pair<Unit, Value>, DataFlowNode>
        //System.out.println("CC3");
        Set<Pair<VariableInfo, DataFlowNode>> seed = new HashSet<>();

        for(VariableInfo baseInfo : varInfoSets) {
            if(baseInfo.isLeft) {
               // Unit u = baseInfo.stmt;
                DataFlowNode dataFlowNode = DataFlowNodeFactory.v().createDataFlowNodeFromBaseInfo(baseInfo);
                seed.add(new Pair<VariableInfo, DataFlowNode>(baseInfo, dataFlowNode));
            }
        }
        //System.out.println("CC4");
        computeDataflow(seed);
        //System.out.println("CC5");
        return seed;

    }

    private void computeDataflow(Set<Pair<VariableInfo, DataFlowNode>> seed) {

        Map<Pair<VariableInfo, DataFlowNode>,DataFlowNode > visited = new HashMap<>();
        Queue<Pair<VariableInfo, DataFlowNode>> worklist = new LinkedList<>();
        worklist.addAll(seed);
        while(!worklist.isEmpty()) {
            Pair<VariableInfo, DataFlowNode> curPath = worklist.poll();
            if(visited.containsKey(curPath))
                continue;
            visited.put(curPath, curPath.getO2());

            VariableInfo curBaseInfo = curPath.getO1();
            DataFlowNode curNode = curPath.getO2();
            if(curBaseInfo.Succs == null)
                continue;
            for(VariableInfo next : curBaseInfo.Succs) {
                Set<DataFlowNode> ret = function(next, curNode, visited);

                for(DataFlowNode dataFlowNode : ret) {
                    worklist.offer(new Pair<VariableInfo, DataFlowNode>(next, dataFlowNode));
                }
            }

        }
    }
    private  Set<DataFlowNode> function(VariableInfo target, DataFlowNode source, Map<Pair<VariableInfo, DataFlowNode>, DataFlowNode > visited) {
        SootField sourceField = source.getField();
        SootField targetField = target.field;
        Set<DataFlowNode> res = new HashSet<>();

        boolean isKillSource = false;
        DataFlowNode newNode = null;

        if(sourceField != null && targetField != null) {

            if(sourceField.equals(targetField)) {
                if(target.isLeft == false) {
                    //a.f1   read
                    newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
                    if(visited.containsKey(newNode))
                        newNode = visited.get(newNode);
                    source.setSuccs(sourceField, newNode);

                }else {
                    isKillSource = true;
                }

            }else {
                // a.f1  b.f1

            }
        }else if(sourceField != null) {
            if(target.isLeft == false) {
                // b = a ; < a.f > ;
                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
                if(visited.containsKey(newNode))
                    newNode = visited.get(newNode);
                source.setSuccs(sourceField, newNode);
            }else {
                // a = b ; < a.f > ;  kill : a.f
                isKillSource = true;
            }

        }else if(targetField != null) {

            if(target.isLeft == false) {
                // b = a.f ; < a > ;
                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
                if(visited.containsKey(newNode))
                    newNode = visited.get(newNode);
                source.setSuccs(targetField, newNode);

            }else {
                // a.f = b ; < a > ;  kill : a.f
//                isKillSource = true;
                source.setKillField(targetField);
            }

        }else {
            if(target.isLeft == false) {
                // a = "xxx";  source : < a >
                // b = a ; < a > ;    use : a
                newNode = DataFlowNodeFactory.v().createDataFlowNode(target.stmt, target.value, targetField);
                if(visited.containsKey(newNode))
                    newNode = visited.get(newNode);
                source.setSuccs(sourceField, newNode);
            }else {

                // a = "xxx";  source : < a >
                //target:  a = b ;  < a > ;  kill : a
                isKillSource = true;
            }

        }
        if(!isKillSource)
            res.add(source);
        if(newNode != null)
            res.add(newNode);
        return res;
    }


}
