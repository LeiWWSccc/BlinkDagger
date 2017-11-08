package soot.jimple.infoflow.sparseop.dataflowgraph;

import soot.SootField;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.sparseop.basicblock.BasicBlock;

import java.util.List;

/**
 * @author wanglei
 */
public class BaseInfoStmt {

    public BasicBlock bb;
    public int idx ;
    public Stmt stmt;
    public boolean isLeft;
    public SootField leftField;
    public Value base;
    //public Unit returnStmt;

    public SootField[] rightFields;
    public SootField[] argsFields;

    public BaseInfoStmt(Value base, SootField left, SootField[] rightFields, SootField[] argsFields, BasicBlock bb, int i, Stmt s) {
        this.base = base;
        this.leftField = left;
        this.rightFields = rightFields;
        this.argsFields = argsFields;
        this.bb = bb;
        this.idx = i;
        this.stmt = s;
    }

    public List<BaseInfoStmt> Succs = null;

    @Override
    public String toString() {
        return "[Stmt : " + stmt + "], [Base : " + base + "]";
    }

}