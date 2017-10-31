package soot.jimple.infoflow.sparseop.dataflowgraph;

import soot.SootField;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.sparseop.basicblock.BasicBlock;

import java.util.List;

/**
 * @author wanglei
 */
public class VariableInfo {

    public BasicBlock bb;
    public int idx ;
    public Stmt stmt;
    public boolean isLeft;
    public SootField field;
    public Value value;

    public VariableInfo(Value v, SootField f, BasicBlock b, int i, Stmt s, boolean isLeft) {
        this.value = v;
        this.field = f;
        this.bb = b;
        this.idx = i;
        this.stmt = s;
        this.isLeft = isLeft;
    }
    public List<VariableInfo> Succs = null;

    @Override
    public String toString() {
        return "Stmt : " + stmt + ", Value : " + value;
    }

}