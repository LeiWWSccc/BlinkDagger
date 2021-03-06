package soot.jimple.infoflow.sparseop.basicblock;

import soot.Body;
import soot.Unit;
import soot.baf.BafBody;
import soot.toolkits.graph.BlockGraph;
import soot.util.Chain;

import java.util.Iterator;
import java.util.List;

/**
 * @author wanglei
 */
public class BasicBlock {

    private Unit mHead, mTail;
    private final Body mBody;
    private List<BasicBlock> mPreds, mSuccessors;
    private int mBlockLength = 0, mIndexInMethod = 0;
    public BasicBlock(Unit aHead, Unit aTail, Body aBody, int aIndexInMethod, int aBlockLength, BasicBlockGraph aBlockGraph)
    {
        mHead = aHead;
        mTail = aTail;
        mBody = aBody;
        mIndexInMethod = aIndexInMethod;
        mBlockLength = aBlockLength;
    }


    /**
     *  Returns the Block's enclosing Body instance.
     *
     *  @return      The block's chain of instructions.
     *  @see         soot.jimple.JimpleBody
     *  @see         BafBody
     *  @see         Body
     */
    public Body getBody()
    {
        return mBody;
    }


    /**
     *  Returns an iterator for the linear chain of Units that make up the block.
     *
     *  @return      An iterator that iterates over the block's units.
     *  @see Chain
     *  @see Unit
     */
    public Iterator<Unit> iterator()
    {
        if(mBody != null)
        {
            Chain<Unit> units = mBody.getUnits();
            return units.iterator(mHead, mTail);
        } else {
            return null;
        }
    }

    /**
     *  Inserts a Unit before some other Unit in this block.
     *
     *
     *  @param toInsert  A Unit to be inserted.
     *  @param point     A Unit in the Block's body
     *                   before which we wish to insert the Unit.
     *  @see Unit
     *  @see Chain
     */
    public void insertBefore(Unit toInsert, Unit point)
    {
        if(point == mHead)
            mHead = toInsert;

        Chain<Unit> methodBody = mBody.getUnits();
        methodBody.insertBefore(toInsert, point);
    }


    /**
     *  Inserts a Unit after some other Unit in the Block.
     *
     *  @param toInsert  A Unit to be inserted.
     *  @param point     A Unit in the Block  after which we wish to
     *                   insert the Unit.
     *  @see Unit
     */
    public void insertAfter(Unit toInsert, Unit point)
    {
        if(point == mTail)
            mTail = toInsert;

        Chain<Unit> methodBody = mBody.getUnits();
        methodBody.insertAfter(toInsert, point);
    }



    /**
     *  Removes a Unit occurring before some other Unit in the Block.
     *
     *  @param item       A Unit to be remove from the Block's Unit Chain.
     *  @return           True if the item could be found and removed.
     *
     */
    public boolean remove(Unit item)
    {
        Chain<Unit> methodBody = mBody.getUnits();

        if(item == mHead)
            mHead = methodBody.getSuccOf(item);
        else if(item == mTail)
            mTail = methodBody.getPredOf(item);

        return methodBody.remove(item);
    }

    /**
     *  Returns the  Unit occurring immediately after some other Unit in the block.
     *
     *  @param aItem      The Unit from which we wish to get it's successor.
     *  @return           The successor or null if <code>aItem</code> is the tail
     *                    for this Block.
     *
     */
    public Unit getSuccOf(Unit aItem)
    {
        Chain<Unit> methodBody = mBody.getUnits();
        if(aItem != mTail)
            return methodBody.getSuccOf(aItem);
        else
            return null;
    }

    /**
     *  Returns the  Unit occurring immediately before some other Unit in the block.
     *
     *  @param aItem      The Unit from which we wish to get it's predecessor.
     *  @return           The predecessor or null if <code>aItem</code> is the head
     *                    for this Block.
     */
    public Unit getPredOf(Unit aItem)
    {
        Chain<Unit> methodBody = mBody.getUnits();
        if(aItem != mHead)
            return methodBody.getPredOf(aItem);
        else
            return null;
    }

    /**
     *  Set the index of this Block in the list of Blocks that partition
     *  its enclosing Body instance.
     *
     *   @param aIndexInMethod The index of this Block in the list of
     *                         Blocks that partition it's enclosing
     *                         Body instance.
     **/
    public void setIndexInMethod(int aIndexInMethod)
    {
        mIndexInMethod = aIndexInMethod;
    }

    /**
     *  Returns the index of this Block in the list of Blocks that partition it's
     *  enclosing Body instance.
     *   @return         The index of the block in it's enclosing Body instance.
     */
    public int getIndexInMethod()
    {
        return mIndexInMethod;
    }

    /**
     * Returns the first unit in this block.
     * @return The first unit in this block.
     */
    public Unit getHead()
    {
        return mHead;
    }

    /**
     * Returns the last unit in this block.
     * @return The last unit in this block.
     */
    public Unit getTail()
    {
        return mTail;
    }

    /**
     *   Sets the list of Blocks that are predecessors of this block in it's enclosing
     *   BlockGraph instance.
     *   @param preds       The a List of Blocks that precede this block.
     *
     *   @see BlockGraph
     */
    public void setPreds(List<BasicBlock> preds)
    {
        mPreds = preds;
        return;
    }

    /**
     *   Returns the List of Block that are predecessors to this block,
     *   @return            A list of predecessor blocks.
     *   @see BlockGraph
     */
    public List<BasicBlock> getPreds()
    {
        return mPreds;
    }



    /**
     *   Sets the list of Blocks that are successors of this block in it's enclosing
     *   BlockGraph instance.
     *   @param succs      The a List of Blocks that succede this block.
     *
     *   @see BlockGraph
     */
    public void setSuccs(List<BasicBlock> succs)
    {
        mSuccessors = succs;
    }



    /**
     *   Returns the List of Blocks that are successors to this block,
     *   @return            A list of successorblocks.
     *   @see BlockGraph
     */
    public List<BasicBlock> getSuccs()
    {
        return mSuccessors;
    }

    public String toShortString() {return "Block #" + mIndexInMethod; }

    public String toString()
    {
        StringBuffer strBuf = new StringBuffer();



        // print out predecessors.

        strBuf.append("Block " + mIndexInMethod + ":" + System.getProperty("line.separator"));
        strBuf.append("[preds: ");
        if(mPreds != null) {
            Iterator<BasicBlock> it = mPreds.iterator();
            while(it.hasNext()) {

                strBuf.append(it.next().getIndexInMethod()+ " ");
            }
        }
        strBuf.append("] [succs: ");
        if(mSuccessors != null) {
            Iterator<BasicBlock> it = mSuccessors.iterator();
            while(it.hasNext()) {

                strBuf.append(it.next().getIndexInMethod() + " ");
            }

        }

        strBuf.append("]" + System.getProperty("line.separator"));



        //strBuf.append("     block" + mIndexInMethod + ":" + System.getProperty("line.separator"));

        Chain<Unit> methodUnits = mBody.getUnits();
        Iterator<Unit> basicBlockIt = methodUnits.iterator(mHead, mTail);

        if(basicBlockIt.hasNext()) {
            Unit someUnit = (Unit) basicBlockIt.next();
            strBuf.append(someUnit.toString() + ";" + System.getProperty("line.separator"));
            while(basicBlockIt.hasNext()){
                someUnit = (Unit) basicBlockIt.next();
                if(someUnit == mTail)
                    break;
                strBuf.append(someUnit.toString() + ";" + System.getProperty("line.separator"));
            }
            someUnit = mTail;
            if(mTail == null)
                strBuf.append("error: null tail found; block length: " + mBlockLength +"" + System.getProperty("line.separator"));
            else if(mHead != mTail)
                strBuf.append(someUnit.toString() + ";" + System.getProperty("line.separator"));


        }
        // Or, it could be an empty block (e.g. Start or Stop Block) --NU
        // else
        // G.v().out.println("No basic blocks found; must be interface class.");

        return strBuf.toString();
    }


}
