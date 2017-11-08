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

import java.util.Set;

import static java.util.Collections.emptySet;


/**
 * The empty function, i.e. a function which returns an empty set for all points
 * in the definition space.
 *  
 * @param <D> The type of data-flow facts to be computed by the tabulation problem.
 */
public class KillAllOp<F, D> implements FlowFunctionOp<F, D> {

	@SuppressWarnings("rawtypes")
	private final static KillAllOp instance = new KillAllOp();

	private KillAllOp(){} //use v() instead

	public Set<Pair<F, D>> computeTargets(D source) {
		return emptySet();
	}
	
	@SuppressWarnings("unchecked")
	public static <F, D> KillAllOp<F, D> v() {
		return instance;
	}

}
