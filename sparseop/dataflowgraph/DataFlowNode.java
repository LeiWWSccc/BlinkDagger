package soot.jimple.infoflow.sparseop.dataflowgraph;

import soot.SootField;
import soot.Unit;
import soot.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author wanglei
 */
public class DataFlowNode {

    private final Value value;

    private final Unit stmt;

    public final static SootField baseField = new SootField("null", null);

    private SootField field;

    public int hashCode = 0;

    private HashMap<SootField, Set<DataFlowNode>> succs ;
    private Set<SootField> killFields ;

    DataFlowNode(Unit u, Value val, SootField f) {
        this.stmt = u;
        this.value = val;
        this.field = f;
    }

    public SootField getField() {
        return field;
    }

    public HashMap<SootField, Set<DataFlowNode>> getSuccs() {
        return this.succs;
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
        this.hashCode = result;

        return this.hashCode;
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
