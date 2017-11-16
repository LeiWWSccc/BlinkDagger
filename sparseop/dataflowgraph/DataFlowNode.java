package soot.jimple.infoflow.sparseop.dataflowgraph;

import soot.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author wanglei
 */
public class DataFlowNode {

    private final Value value;

    private final Unit stmt;

    public final static SootField baseField = new SootField("null",  NullType.v(), soot.Modifier.FINAL);

    private SootField field;

    private boolean isOverWrite;

    public int hashCode = 0;

    private HashMap<SootField, Set<DataFlowNode>> succs ;
    private Set<SootField> killFields ;

    DataFlowNode(Unit u, Value val, SootField f, boolean isLeft) {
        this.stmt = u;
        this.value = val;
        this.field = f;
        this.isOverWrite = isLeft;
    }

    public Value getValue() {
        return value;
    }

    public SootField getField() {
        return field;
    }

    public HashMap<SootField, Set<DataFlowNode>> getSuccs() {
        return this.succs;
    }

    public Unit getStmt() {
        return this.stmt;
    }

    public void setSuccs(SootField field, DataFlowNode target) {
        if(field == null)
            field = baseField;

        if(succs == null)
            succs = new HashMap<>();
        if(succs.containsKey(field)) {
            succs.get(field).add(target);
        } else {
            Set<DataFlowNode> tmp = new HashSet<>();
            tmp.add(target);
            succs.put(field, tmp);
        }
    }

    public void setKillField(SootField field) {
        if(killFields == null)
            killFields = new HashSet<>();
        killFields.add(field);
    }

    public Set<SootField> getKillFields() {
        return killFields;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DataFlowNode other = (DataFlowNode) obj;

        // If we have already computed hash codes, we can use them for
        // comparison
        if (this.hashCode != 0
                && other.hashCode != 0
                && this.hashCode != other.hashCode)
            return false;

        if (stmt == null) {
            if (other.stmt != null)
                return false;
        } else if (!stmt.equals(other.stmt))
            return false;

        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;

        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;

        if(isOverWrite != other.isOverWrite)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        if (this.hashCode != 0)
            return hashCode;

        final int prime = 31;
        int result = 1;

        // deliberately ignore prevAbs
        result = prime * result + ((stmt == null) ? 0 : stmt.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((isOverWrite) ? 1 : 0);
        this.hashCode = result;

        return this.hashCode;
    }

    public DataFlowNode clone() {
        DataFlowNode newNode = new DataFlowNode(stmt, value, field, isOverWrite);
        return newNode;
    }

    public String toString() {
        if(value == null) {
            return "RETURN Stmt :" + stmt;
        }
        String fs ;
        if(field == DataFlowNode.baseField)
            fs = "NULL";
        else
            fs = field.toString();


        return "STMT{ " + stmt + " }, BASE{ " + value + " }, FIELD{ " + fs +" }";
    }

//    public void setSuccs(DataFlowNode source, SootField field, DataFlowNode target) {
//        HashMap<SootField, Set<DataFlowNode>> succs = source.getSuccs();
//        if(succs == null)
//            succs = new HashMap<>();
//        if(succs.containsKey(field)) {
//            succs.get(field).add(target);
//        } else {
//            Set<DataFlowNode> tmp = new HashSet<>();
//            tmp.add(target);
//            succs.put(field, tmp);
//        }
//    }


}
