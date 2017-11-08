/*******************************************************************************
 * Copyright (c) 2012 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Eric Bodden - initial API and implementation
 ******************************************************************************/
package soot.jimple.infoflow.sparseop.solver.heros;

import heros.solver.Pair;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ZeroedFlowFunctionsOp<N,F, D, M> implements FlowFunctionsOp<N, F, D, M> {

	protected final FlowFunctionsOp<N, F, D, M> delegate;
	protected final D zeroValue;

	public ZeroedFlowFunctionsOp(FlowFunctionsOp<N, F, D, M> delegate, D zeroValue) {
		this.delegate = delegate;
		this.zeroValue = zeroValue;
	}

	public FlowFunctionOp<F, D> getNormalFlowFunction(N curr, N succ) {
		return new ZeroedFlowFunction(delegate.getNormalFlowFunction(curr, succ));
	}

	public FlowFunctionOp<F, D> getCallFlowFunction(N callStmt, M destinationMethod) {
		return new ZeroedFlowFunction(delegate.getCallFlowFunction(callStmt, destinationMethod));
	}

	public FlowFunctionOp<F, D> getReturnFlowFunction(N callSite, M calleeMethod, N exitStmt, N returnSite) {
		return new ZeroedFlowFunction(delegate.getReturnFlowFunction(callSite, calleeMethod, exitStmt, returnSite));
	}

	public FlowFunctionOp<F, D> getCallToReturnFlowFunction(N callSite, N returnSite) {
		return new ZeroedFlowFunction(delegate.getCallToReturnFlowFunction(callSite, returnSite));
	}
	
	protected class ZeroedFlowFunction implements FlowFunctionOp<F, D> {

		protected FlowFunctionOp<F, D> del;

		private ZeroedFlowFunction(FlowFunctionOp<F, D> del) {
			this.del = del;
		}		
		
		@Override
		public Set<Pair<F, D>> computeTargets(D source) {
			if(source==zeroValue) {
				HashSet<Pair<F, D>> res = new LinkedHashSet<Pair<F, D>>(del.computeTargets(source));
				res.add(new Pair<F, D>(null, zeroValue));
				return res;
			} else {
				return del.computeTargets(source);
			}
		}
		
	}
	

}
