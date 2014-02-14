package single_host;

import net.floodlightcontroller.pronghornmodule.IPronghornBarrierCallback;

import java.util.Set;
import java.util.HashSet;

public class FloodlightShimBarrierCallback implements IPronghornBarrierCallback
{
    private Set<Integer> xids = new HashSet<Integer>();
    private Set<Integer> failed_xids = new HashSet<Integer>();

    private Boolean has_finished = false;
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
                while (! has_finished)
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
            has_finished.notifyAll();
        }
    }

    @Override
    public void barrier_failure()
    {
        succeeded = false;
        synchronized(has_finished)
        {
            has_finished.notifyAll();
        }
    }
}