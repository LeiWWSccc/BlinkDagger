package soot.jimple.infoflow;

import heros.solver.Pair;
import soot.FastHierarchy;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.infoflow.collect.MyConcurrentHashMap;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.sparseop.basicblock.UnitOrderComputing;
import soot.jimple.infoflow.sparseop.dataflowgraph.BaseInfoStmt;
import soot.jimple.infoflow.sparseop.dataflowgraph.DataFlowNode;
import soot.jimple.infoflow.sparseop.dataflowgraph.data.DFGEntryKey;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.util.TypeUtils;

import java.util.Map;
import java.util.Set;

/**
 * Manager class for passing internal data flow objects to interface
 * implementors
 * 
 * @author Steven Arzt
 *
 */
public class InfoflowManager {
	
	private final InfoflowConfiguration config;
	private IInfoflowSolver forwardSolver;
	private final IInfoflowCFG icfg;
	private final ISourceSinkManager sourceSinkManager;
	private final ITaintPropagationWrapper taintWrapper;
	private final TypeUtils typeUtils;
	private final FastHierarchy hierarchy;

	//op
	private  Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> dfg ;

	private  Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> backwardDfg ;
	Map<SootMethod,  Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>>> returnInfo ;

	private Map<SootMethod, UnitOrderComputing> unitOrderComputingMap ;

	private  MyConcurrentHashMap<Pair<SootMethod, Unit>, Set<Unit>> activationUnitsToUnits;

	InfoflowManager(InfoflowConfiguration config,
			IInfoflowSolver forwardSolver,
			IInfoflowCFG icfg,
			ISourceSinkManager sourceSinkManager,
			ITaintPropagationWrapper taintWrapper,
			FastHierarchy hierarchy) {
		this.config = config;
		this.forwardSolver = forwardSolver;
		this.icfg = icfg;
		this.sourceSinkManager = sourceSinkManager;
		this.taintWrapper = taintWrapper;
		this.typeUtils = new TypeUtils(this);
		this.hierarchy = hierarchy;
	}
	
	/**
	 * Gets the configuration for this data flow analysis
	 * @return The configuration for this data flow analysis
	 */
	public InfoflowConfiguration getConfig() {
		return this.config;
	}
	
	/**
	 * Sets the IFDS solver that propagates edges forward
	 * @param solver The IFDS solver that propagates edges forward
	 */
	public void setForwardSolver(IInfoflowSolver solver) {
		this.forwardSolver = solver;
	}
	
	/**
	 * Gets the IFDS solver that propagates edges forward
	 * @return The IFDS solver that propagates edges forward
	 */
	public IInfoflowSolver getForwardSolver() {
		return this.forwardSolver;
	}
	
	/**
	 * Gets the interprocedural control flow graph
	 * @return The interprocedural control flow graph
	 */
	public IInfoflowCFG getICFG() {
		return this.icfg;
	}
	
	/**
	 * Gets the SourceSinkManager implementation
	 * @return The SourceSinkManager implementation
	 */
	public ISourceSinkManager getSourceSinkManager() {
		return this.sourceSinkManager;
	}
	
	/**
	 * Gets the taint wrapper to be used for handling library calls
	 * @return The taint wrapper to be used for handling library calls
	 */
	public ITaintPropagationWrapper getTaintWrapper() { 
		return this.taintWrapper;
	}
	
	/**
	 * Gets the utility class for type checks
	 * @return The utility class for type checks
	 */
	public TypeUtils getTypeUtils() {
		return this.typeUtils;
	}
	
	/**
	 * Gets the Soot type hierarchy that was constructed together with the
	 * callgraph. In contrast to Scene.v().getFastHierarchy, this object is
	 * guaranteed to be available.
	 * @return The fast hierarchy
	 */
	public FastHierarchy getHierarchy() {
		return hierarchy;
	}

	public void setDfg(Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> g) {
		this.dfg = g;
	}

	public Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> getDfg() {
		return dfg;
	}


	public void setBackwardDfgDfg(Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> g) {
		this.backwardDfg = g;
	}

	public Map<SootMethod, Map<Value, Map<DFGEntryKey, Pair<BaseInfoStmt, DataFlowNode>>>> getBackwardDfg() {
		return backwardDfg;
	}

	public void setUnitOrderComputingMap(Map<SootMethod, UnitOrderComputing> map) {
		this.unitOrderComputingMap = map;
	}

	public Map<SootMethod, UnitOrderComputing> getUnitOrderComputingMap() {
		return unitOrderComputingMap;
	}

	public  Map<SootMethod,  Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>>> getReturnInfo() {
		return returnInfo;
	}

	public void setReturnInfo(Map<SootMethod,  Map<Value, Map<DFGEntryKey, Set<DataFlowNode>>>>  returnInfo ) {
		this.returnInfo = returnInfo;
	}

	public void setActivationUnitsToUnits (MyConcurrentHashMap<Pair<SootMethod, Unit>, Set<Unit>>  map) {
		this.activationUnitsToUnits = map;
	}

	public MyConcurrentHashMap<Pair<SootMethod, Unit>, Set<Unit>> getActivationUnitsToUnits () {
		return this.activationUnitsToUnits;
	}

}
