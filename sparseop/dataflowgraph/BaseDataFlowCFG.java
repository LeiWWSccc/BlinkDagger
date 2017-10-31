package soot.jimple.infoflow.sparseop.dataflowgraph;

import soot.jimple.infoflow.sparseop.basicblock.BasicBlock;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.Pair;

import java.util.*;

/**
 * @author wanglei
 */
public class BaseDataFlowCFG implements DirectedGraph {
    private HashMap<BasicBlock, Set<VariableInfo>> bbToBaseInfoMap = null;

    public BaseDataFlowCFG(HashMap<BasicBlock, Set<VariableInfo>> bbToBaseInfoMap) {
        this.bbToBaseInfoMap = bbToBaseInfoMap;
    }

    public void solve1(List<BasicBlock> heads) {
        Set<BasicBlock> visited = new HashSet<>();
        for(BasicBlock head : heads) {
            dfs(head, null, visited);
        }
    }
    private void dfs(BasicBlock bb, VariableInfo pre, Set<BasicBlock> visited) {
        if(visited.contains(bb))
            return ;
        visited.add(bb);
        Pair<VariableInfo, VariableInfo> ret = innerBasicBlock(bb);
        VariableInfo tail = null;
        if(ret == null) {
            tail = pre;
        }else {
            tail = ret.getO2();
        }

        if(pre != null && ret != null) {
            if(pre.Succs == null)
                pre.Succs = new ArrayList<>();
                pre.Succs.add(ret.getO1());

        }

        for(BasicBlock succ : bb.getSuccs()) {
            dfs(succ, tail, visited);
        }

    }

    public void solve() {
        Map<BasicBlock, Pair<VariableInfo, VariableInfo> > result = new HashMap<>();
        for(BasicBlock bb : bbToBaseInfoMap.keySet()) {
            solverBB(bb, result);
        }
    }
    private void solverBB(BasicBlock bb, Map<BasicBlock, Pair<VariableInfo, VariableInfo> > result) {
        Pair<VariableInfo, VariableInfo> ret = null;
        if(result.containsKey(bb)) {
            ret = result.get(bb);
        }else {
            ret = innerBasicBlock(bb);
            if(ret != null)
                result.put(bb, ret);
        }

        VariableInfo tail = ret.getO2();
        Set<BasicBlock> visited = new HashSet<>();
        for(BasicBlock succ : bb.getSuccs()) {
            subSolverBB(succ, tail, result, visited);
        }

    }
    private void subSolverBB(BasicBlock bb, VariableInfo preTail, Map<BasicBlock,
            Pair<VariableInfo, VariableInfo> > result, Set<BasicBlock> visited ) {
        if(visited.contains(bb))
            return ;
        visited.add(bb);

        Pair<VariableInfo, VariableInfo> ret = null;
        if(result.containsKey(bb)) {
            ret = result.get(bb);
        }else {
            ret = innerBasicBlock(bb);
            if(ret != null)
                result.put(bb, ret);
        }
        if(ret != null ) {
            if(preTail.Succs == null)
                preTail.Succs = new ArrayList<>();
            preTail.Succs.add(ret.getO1());
            return;
        }

        VariableInfo tail = null;
        if(ret == null)
            tail = preTail;
        else
            tail = ret.getO2();
        for(BasicBlock succ : bb.getSuccs()) {
            subSolverBB(succ, tail, result, visited);
        }

    }

    private  Pair<VariableInfo, VariableInfo> innerBasicBlock(BasicBlock bb) {
        if(!bbToBaseInfoMap.containsKey(bb))
            return null;

        List<VariableInfo> col = new ArrayList<>(bbToBaseInfoMap.get(bb));
        Collections.sort(col, new Comparator<VariableInfo>() {
            public int compare(VariableInfo arg0, VariableInfo arg1) {
                return arg0.idx - arg1.idx;
            }
        });
        VariableInfo head = null;
        VariableInfo tail = null;
        VariableInfo pre = null;
        for(int i = 0; i < col.size(); i++) {
            if(i == 0) {
                head = col.get(i);
            }
            if(i == col.size() - 1) {
                tail = col.get(i);
            }
            if(pre != null) {
                if(pre.Succs == null)
                    pre.Succs = new ArrayList<>();
                pre.Succs.add(col.get(i));

            }
            pre = col.get(i);

        }
        return new Pair<>(head, tail);
    }

    @Override
    public List getHeads() {
        return null;
    }

    @Override
    public List getTails() {
        return null;
    }

    @Override
    public List getPredsOf(Object s) {
        return null;
    }

    @Override
    public List getSuccsOf(Object s) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Iterator iterator() {
        return null;
    }
}
