/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.taintWrappers;

import soot.SootMethod;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.AccessPathFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Taints the return value of a method call if one of the parameter values
 * or the base object is tainted.
 * 
 * @author Steven Arzt
 *
 */
public class IdentityTaintWrapper extends AbstractTaintWrapper {
	
	@Override
	public Set<AccessPath> getTaintsForMethodInternal(Stmt stmt, AccessPath taintedPath) {
		assert stmt.containsInvokeExpr();
		
		// For the moment, we don't implement static taints on wrappers. Pass it on
		// not to break anything
		if(taintedPath.isStaticFieldRef())
			return Collections.singleton(taintedPath);

		if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
			InstanceInvokeExpr iiExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
			
			// If the base object is tainted, the return value is always tainted
			if (taintedPath.getPlainValue().equals(iiExpr.getBase()))
				if (stmt instanceof AssignStmt)
					return Collections.singleton(AccessPathFactory.v().createAccessPath(stmt,
							((AssignStmt) stmt).getLeftOp(), taintedPath.getTaintSubFields()));
		}
			
		// If one of the parameters is tainted, the return value is tainted, too
		for (Value param : stmt.getInvokeExpr().getArgs())
			if (taintedPath.getPlainValue().equals(param))
				if (stmt instanceof AssignStmt)
					return Collections.singleton(AccessPathFactory.v().createAccessPath(stmt,
							((AssignStmt) stmt).getLeftOp(), taintedPath.getTaintSubFields()));
		
		return Collections.emptySet();
	}

	@Override
	public boolean isExclusiveInternal(Stmt stmt, AccessPath taintedPath) {
		assert stmt.containsInvokeExpr();
		
		// We are exclusive if the base object is tainted
		if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
			InstanceInvokeExpr iiExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
			if (taintedPath.getPlainValue().equals(iiExpr.getBase()))
				return true;
		}
		
		// If one parameter is tainted, we are exclusive as well
		for (Value param : stmt.getInvokeExpr().getArgs())
			if (taintedPath.getPlainValue().equals(param))
				return true;
		
		return false;
	}

	@Override
	public boolean supportsCallee(SootMethod method) {
		return true;
	}

	@Override
	public boolean supportsCallee(Stmt callSite) {
		return true;
	}

	@Override
	public Set<Abstraction> getAliasesForMethod(Stmt stmt, Abstraction d1,
			Abstraction taintedPath) {
		// We do not provide any aliases
		return null;
	}
	
}
