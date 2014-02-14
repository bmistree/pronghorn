package single_host;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import pronghorn.ShimInterface;
import pronghorn.SwitchStatusHandler;
import pronghorn.RTableUpdate;

import net.floodlightcontroller.pronghornmodule.IPronghornService;
import net.floodlightcontroller.core.Main;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.pronghornmodule.IPronghornService;
import net.floodlightcontroller.pronghornmodule.ISwitchAddedRemovedListener;
/**
   Serves as intermediate layer between Ralph and Floodlight
 */
public class SingleHostFloodlightShim
    implements ShimInterface, ISwitchAddedRemovedListener
{
    private IPronghornService pronghorn_floodlight = null;

    /**
       Whenever we see that there was a new switch or we see that a
       switch went down, we notify these handlers. 
     */
    private ReentrantLock handler_lock = new ReentrantLock();
    private Set<SwitchStatusHandler> switch_status_handlers =
        new HashSet<SwitchStatusHandler>();


    
    public SingleHostFloodlightShim(String settings_filename)
    {
        try
        {
            final Main.ProviderPronghornTuple ppt =
                Main.get_controller(settings_filename);
            pronghorn_floodlight = ppt.pronghorn;


            Thread t = new Thread()
            {
                @Override
                public void run()
                {
                    ppt.floodlight_provider.run();
                }
            };
            t.start();

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException ex)
            {
                ex.printStackTrace();
                assert(false);
            }

            pronghorn_floodlight.register_switch_changes_listener(this);

        }
        catch (FloodlightModuleException ex)
        {
            ex.printStackTrace();
            assert(false);
        }
    }

    /** ISwtichAddedRemovedListener */
    @Override
    public void switch_added(String unique_switch_id)
    {
        handler_lock.lock();
        for (SwitchStatusHandler ssh : switch_status_handlers)
            ssh.new_switch(this,unique_switch_id);
        handler_lock.unlock();
    }
    @Override    
    public void switch_removed(String unique_switch_id)
    {
        handler_lock.lock();
        for (SwitchStatusHandler ssh : switch_status_handlers)
            ssh.removed_switch(this,unique_switch_id);
        handler_lock.unlock();
    }
    
    
    /** ShimInterface methods */
    @Override
    public void subscribe_switch_status_handler(SwitchStatusHandler ssh)
    {
        handler_lock.lock();
        switch_status_handlers.add(ssh);
        handler_lock.unlock();
    }
    @Override
    public void unsubscribe_switch_status_handler(SwitchStatusHandler ssh)
    {
        handler_lock.lock();
        switch_status_handlers.remove(ssh);
        handler_lock.unlock();
    }
    
    @Override
    public boolean switch_rtable_updates(
        String switch_id,List<RTableUpdate> updates)
    {
        return true;
    }

    @Override
    public void start()
    {
    }

    @Override
    public void stop()
    {
    }
}