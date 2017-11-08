package soot.jimple.infoflow.sparseop.dataflowgraph;

import soot.SootField;
import soot.Unit;
import soot.Value;

/**
 * @author wanglei
 */
public class DataFlowNodeFactory {

    public static DataFlowNodeFactory instance = new DataFlowNodeFactory();

    public static DataFlowNodeFactory v() {return instance;}

    public DataFlowNode createDataFlowNode(Unit u, Value val, SootField field, boolean isOverWrite) {


        return new DataFlowNode(u, val, field, isOverWrite);
    }


//    public DataFlowNode createDataFlowNode(Value val, SootField[] appendingFields) {
//
//        Local value = null;
//
//        if (val instanceof FieldRef) {
//
//        }else if (val instanceof ArrayRef) {
//
//        }else {
//            value = (Local) val;
//
//        }
//        return new DataFlowNode(value);
//    }

}
