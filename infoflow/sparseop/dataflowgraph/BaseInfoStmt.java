package soot.jimple.infoflow.sparseop.dataflowgraph;

import soot.SootField;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.sparseop.basicblock.BasicBlock;

import java.util.Set;

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

    public Set<BaseInfoStmt> Succs = null;
    public Set<BaseInfoStmt> Preds = null;

    @Override
    public String toString() {
        if(base == null)
            return "RETURN STMT : " + stmt;

        return "STMT{ " + stmt + " }, [Base : " + base + "]";
    }

}