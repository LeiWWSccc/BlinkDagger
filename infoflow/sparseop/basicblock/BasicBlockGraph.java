package soot.jimple.infoflow.sparseop.basicblock;

import soot.Body;
import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.jimple.NopStmt;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.util.Chain;

import java.util.*;

/**
 * BasicBlock for ICFG
 *
 * @author wanglei
 */

public  class BasicBlockGraph implements DirectedGraph<BasicBlock> {
    protected Body mBody;
    protected Chain<Unit> mUnits;
    protected SootMethod method;
    protected List<BasicBlock> mBlocks;
    protected List<BasicBlock> mHeads = new ArrayList<BasicBlock>();
    protected List<BasicBlock> mTails = new ArrayList<BasicBlock>();

    protected Map<Unit, BasicBlock> unitToBBMap = new HashMap<>();
    protected Map<Unit, Integer> unitToIndexMap = new HashMap<>();


    public BasicBlockGraph(IInfoflowCFG unitGraph, SootMethod m) {
        mBody = m.getActiveBody();
        mUnits = mBody.getUnits();
        method = m;
        Set<Unit> leaders = computeLeaders(unitGraph, m);
        buildBlocks(leaders, unitGraph);
    }


    public Set<Unit> computeLeaders(IInfoflowCFG unitGraph, SootMethod m) {

        Body body = m.getActiveBody();
        if (body != mBody) {
            throw new RuntimeException(
                    "BlockGraph.computeLeaders() called with a UnitGraph that doesn't match its mBody.");
        }
        Set<Unit> leaders = new HashSet<Unit>();

        // Trap handlers start new basic blocks, no matter how many
        // predecessors they have.
        Chain<Trap> traps = body.getTraps();
        for (Iterator<Trap> trapIt = traps.iterator(); trapIt.hasNext();) {
            Trap trap = trapIt.next();
            leaders.add(trap.getHandlerUnit());
        }

        for (Iterator<Unit> unitIt = body.getUnits().iterator(); unitIt.hasNext();) {
            Unit u = unitIt.next();
            List<Unit> predecessors = unitGraph.getPredsOf(u);
            int predCount = predecessors.size();
            List<Unit> successors = unitGraph.getSuccsOf(u);
            int succCount = successors.size();

            if (predCount != 1) { // If predCount == 1 but the predecessor
                leaders.add(u); // is a branch, u will get added by that
            } // branch's successor test.
            if ((succCount > 1) || (u.branches())) {
                for (Iterator<Unit> it = successors.iterator(); it.hasNext();) {
                    leaders.add((Unit) it.next()); // The cast is an
                } // assertion check.
            }
        }
        return leaders;

    }


    protected Map<Unit, BasicBlock> buildBlocks(Set<Unit> leaders, IInfoflowCFG unitGraph) {
        List<BasicBlock> blockList = new ArrayList<BasicBlock>(leaders.size());
        Map<Unit, BasicBlock> unitToBlock = new HashMap<Unit, BasicBlock>(); // Maps head
        // and tail
        // units to
        // their blocks, for building
        // predecessor and successor lists.
        Unit blockHead = null;
        int blockLength = 0;
        Iterator<Unit> unitIt = mUnits.iterator();
        if (unitIt.hasNext()) {
            blockHead = unitIt.next();
            if (!leaders.contains(blockHead)) {
                throw new RuntimeException("BlockGraph: first unit not a leader!");
            }
            blockLength++;
        }
        Unit blockTail = blockHead;
        int indexInMethod = 0;

        while (unitIt.hasNext()) {
            Unit u = unitIt.next();
            if (leaders.contains(u)) {
                addBlock(blockHead, blockTail, indexInMethod, blockLength, blockList, unitToBlock, unitGraph);
                indexInMethod++;
                blockHead = u;
                blockLength = 0;
            }
            blockTail = u;
            blockLength++;
        }
        if (blockLength > 0) {
            // Add final block.
            addBlock(blockHead, blockTail, indexInMethod, blockLength, blockList, unitToBlock, unitGraph);
        }

        // The underlying UnitGraph defines heads and tails.
        //mHead get the head basic block
        for (Iterator<Unit> it = unitGraph.getStartPointsOf(method).iterator(); it.hasNext();) {
            Unit headUnit = (Unit) it.next();
            BasicBlock headBlock = unitToBlock.get(headUnit);
            if (headBlock.getHead() == headUnit) {
                mHeads.add(headBlock);
            } else {
                throw new RuntimeException("BlockGraph(): head Unit is not the first unit in the corresponding Block!");
            }
        }

        //mTails get the head basic block
        for (Iterator<Unit> it = unitGraph.getEndPointsOf(method).iterator(); it.hasNext();) {
            Unit tailUnit = (Unit) it.next();
            BasicBlock tailBlock = unitToBlock.get(tailUnit);
            if (tailBlock.getTail() == tailUnit) {
                mTails.add(tailBlock);
            } else {
                throw new RuntimeException("BlockGraph(): tail Unit is not the last unit in the corresponding Block!");
            }
        }

        for (Iterator<BasicBlock> blockIt = blockList.iterator(); blockIt.hasNext();) {
            BasicBlock block = blockIt.next();

            List<Unit> predUnits = unitGraph.getPredsOf(block.getHead());
            List<BasicBlock> predBlocks = new ArrayList<BasicBlock>(predUnits.size());
            for (Iterator<Unit> predIt = predUnits.iterator(); predIt.hasNext();) {
                Unit predUnit = predIt.next();
                BasicBlock predBlock = unitToBlock.get(predUnit);
                if (predBlock == null) {
                    throw new RuntimeException("BlockGraph(): block head mapped to null block!");
                }
                predBlocks.add(predBlock);
            }

            if (predBlocks.size() == 0) {
                block.setPreds(Collections.<BasicBlock> emptyList());

                // If the UnreachableCodeEliminator is not eliminating
                // unreachable handlers, then they will have no
                // predecessors, yet not be heads.
				/*
				 * if (! mHeads.contains(block)) { throw new
				 * RuntimeException("Block with no predecessors is not a head!"
				 * );
				 *
				 * // Note that a block can be a head even if it has //
				 * predecessors: a handler that might catch an exception //
				 * thrown by the first Unit in the method. }
				 */
            } else {
                block.setPreds(Collections.unmodifiableList(predBlocks));
                if (block.getHead() == mUnits.getFirst()) {
                    mHeads.add(block); // Make the first block a head
                    // even if the Body is one huge loop.
                }
            }

            List<Unit> succUnits = unitGraph.getSuccsOf(block.getTail());
            List<BasicBlock> succBlocks = new ArrayList<BasicBlock>(succUnits.size());
            for (Iterator<Unit> succIt = succUnits.iterator(); succIt.hasNext();) {
                Unit succUnit = succIt.next();
                BasicBlock succBlock = unitToBlock.get(succUnit);
                if (succBlock == null) {
                    throw new RuntimeException("BlockGraph(): block tail mapped to null block!");
                }
                succBlocks.add(succBlock);
            }

            if (succBlocks.size() == 0) {
                block.setSuccs(Collections.<BasicBlock> emptyList());
                if (!mTails.contains(block)) {
                    // if this block is totally empty and unreachable, we remove it
                    if (block.getPreds().isEmpty()
                            && block.getHead() == block.getTail()
                            && block.getHead() instanceof NopStmt)
                        blockIt.remove();
                    else
                        throw new RuntimeException("Block with no successors is not a tail!: " + block.toString());
                    // Note that a block can be a tail even if it has
                    // successors: a return that throws a caught exception.
                }
            } else {
                block.setSuccs(Collections.unmodifiableList(succBlocks));
            }
        }
        mBlocks = Collections.unmodifiableList(blockList);
        mHeads = Collections.unmodifiableList(mHeads);
        if (mTails.size() == 0) {
            mTails = Collections.emptyList();
        } else {
            mTails = Collections.unmodifiableList(mTails);
        }
        return unitToBlock;
    }

    private void addBlock(Unit head, Unit tail, int index, int length, List<BasicBlock> blockList,
                          Map<Unit, BasicBlock> unitToBlock, IInfoflowCFG unitGraph) {
        BasicBlock block = new BasicBlock(head, tail, mBody, index, length, this);
        blockList.add(block);
        unitToBlock.put(tail, block);
        unitToBlock.put(head, block);
        addOtherBlockInfo(head, tail, block, unitGraph);
    }

    private void addOtherBlockInfo(Unit head, Unit tail, BasicBlock block, IInfoflowCFG unitGraph) {
        int idx = 0;
        Unit cur = head;
        while(cur != tail) {
            unitToBBMap.put(cur, block);
            unitToIndexMap.put(cur, idx);
            idx++;
            List<Unit> succs = unitGraph.getSuccsOf(cur);
            if(succs.size() != 1)
                throw new RuntimeException("inner BB's Unit has more than 1 succ!");
            cur = succs.get(0);
        }
        unitToBBMap.put(cur, block);
        unitToIndexMap.put(cur, idx);
    }

    public Map<Unit, BasicBlock> getUnitToBBMap() {
        return unitToBBMap;
    }

    public Map<Unit, Integer> getUnitToIndexMap() {
        return unitToIndexMap;
    }



    public Body getBody() {
        return mBody;
    }

    public List<BasicBlock> getBlocks() {
        return mBlocks;
    }

    public int getInnerBlockId(Unit u) {

        return 0;

    }

    public BasicBlock getBasicBlock(Unit u) {
        return null;
    }


    @Override
    public List<BasicBlock> getHeads() {
        return mHeads;
    }

    @Override
    public List<BasicBlock> getTails() {
        return mTails;
    }

    @Override
    public List<BasicBlock> getPredsOf(BasicBlock s) {
        return s.getPreds();
    }

    @Override
    public List<BasicBlock> getSuccsOf(BasicBlock s) {
        return s.getSuccs();
    }

    @Override
    public int size() {
        return mBlocks.size();
    }

    @Override
    public Iterator<BasicBlock> iterator() {
        return mBlocks.iterator();
    }

    public String toString() {

        Iterator<BasicBlock> it = mBlocks.iterator();
        StringBuffer buf = new StringBuffer();
        while (it.hasNext()) {
            BasicBlock someBlock = it.next();

            buf.append(someBlock.toString() + '\n');
        }

        return buf.toString();
    }
}
