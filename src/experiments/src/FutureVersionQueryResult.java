package experiments;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;

import pronghorn.ft_ops.FTableUpdate;

public class FutureVersionQueryResult
    implements Future<List<FTableUpdate>>
{
    private List<FTableUpdate> result = null;
    private boolean result_set = false;
    private ReentrantLock mutex = new ReentrantLock();
    private Condition condition = mutex.newCondition();
    
    
    public void put_result(List<FTableUpdate> _result)
    {
        mutex.lock();
        result = _result;
        result_set = true;
        condition.signalAll();
        mutex.unlock();
    }
    
    /**
       Attempts to cancel execution of this task.
     */
    @Override        
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        // disallow canceling.
        return false;
    }

    /**
       Waits if necessary for the computation to complete, and then
       retrieves its result.
     */
    @Override
    public List<FTableUpdate> get()
    {
        mutex.lock();
        try
        {
            while(! result_set)
                condition.await();
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
            assert(false);
            System.exit(-1);
        }
        finally
        {
            mutex.unlock();
            return result;
        }
    }

        
    /**
       Waits if necessary for at most the given time for the
       computation to complete, and then retrieves its result, if
       available.
    */
    @Override
    public List<FTableUpdate> get(long timeout, TimeUnit unit)
    {
        // FIXME: should never call.
        System.err.println(
            "Timed get does not work for version result querier");
        assert(false);
        System.exit(-1);
        return null;
    }

    /**
       Returns true if this task was cancelled before it completed
       normally.
     */
    @Override
    public boolean isCancelled()
    {
        return false;
    }

    @Override
    public boolean isDone()
    {
        return result_set;
    }
}