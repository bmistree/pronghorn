package single_host;

import net.floodlightcontroller.pronghornmodule.IPronghornBarrierCallback;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;


public class FloodlightShimBarrierCallback implements IPronghornBarrierCallback
{
    private Set<Integer> xids = new HashSet<Integer>();
    private Set<Integer> failed_xids = new HashSet<Integer>();

    // Note: using atomicboolean here because it is mutable.  If we
    // just used a Boolean, we would not be able to set its internal
    // values.  Therefore, we would have to set it, eg., has_finished
    // = true.  This would interfere with the notify, wait,
    // synchronized code below: we would be waiting on another
    // object's condition.
    private AtomicBoolean has_finished = new AtomicBoolean(false);
    private boolean succeeded = false;
    
    public synchronized void add_xid(int xid)
    {
        xids.add(xid);
    }

    public boolean wait_on_complete()
    {
        synchronized(has_finished)
        {
            try
            {
                while (! has_finished.get())
                    has_finished.wait();
            }
            catch (InterruptedException ex)
            {
                // FIXME: handle interrupted exceptions more cleanly.
                ex.printStackTrace();
                assert(false);
            }
            return succeeded;
        }
    }

    @Override
    public synchronized void command_failure(int id)
    {
        failed_xids.add(id);
    }

    @Override
    public void barrier_success()
    {
        if (failed_xids.size() == 0)
            succeeded = true;
        
        synchronized(has_finished)
        {
            has_finished.set(true);
            has_finished.notifyAll();
        }
    }

    @Override
    public void barrier_failure()
    {
        succeeded = false;
        synchronized(has_finished)
        {
            has_finished.set(true);
            has_finished.notifyAll();
        }
    }
}