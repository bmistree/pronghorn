package single_host;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import pronghorn.ShimInterface;
import pronghorn.RTableUpdate;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.pronghornmodule.IPronghornService;
import net.floodlightcontroller.core.Main;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.pronghornmodule.IPronghornService;
/**
   Serves as intermediate layer between Ralph and Floodlight
 */
public class SingleHostFloodlightShim
    implements ShimInterface
{
    private IPronghornService pronghorn_floodlight = null;

    /**
       Whenever we see that there was a new switch or we see that a
       switch went down, we notify these handlers. 
     */
    private ReentrantLock handler_lock = new ReentrantLock();
    
    public SingleHostFloodlightShim()
    {
        try
        {
            final Main.ProviderPronghornTuple ppt =
                Main.get_controller(null);
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
        }
        catch (FloodlightModuleException ex)
        {
            ex.printStackTrace();
            assert(false);
        }
    }

    
    /** ShimInterface methods */
    @Override
    public void subscribe_switch_status_handler(IOFSwitchListener switch_listener)
    {
        pronghorn_floodlight.register_switch_listener(switch_listener);
    }
    
    @Override
    public void unsubscribe_switch_status_handler(IOFSwitchListener switch_listener)
    {
        pronghorn_floodlight.unregister_switch_listener(switch_listener);
    }
    
    @Override
    public boolean switch_rtable_updates(
        String switch_id,List<RTableUpdate> updates)
    {
        FloodlightShimBarrierCallback floodlight_callback =
            new FloodlightShimBarrierCallback();
        
        for (RTableUpdate update : updates)
        {
            try
            {
                int xid = pronghorn_floodlight.add_entry(
                    update.entry,switch_id);
                floodlight_callback.add_xid(xid);
            }
            catch (IOException ex)
            {
                // FIXME: assuming that will not have IOException when
                // sending commands to switch.  Should really remove
                // switch if this happens instead.
                ex.printStackTrace();
                assert(false);
            }
        }

        try
        {
            pronghorn_floodlight.barrier(switch_id,floodlight_callback);
        }
        catch (IOException ex)
        {
            // FIXME: assuming that will not have IOException when
            // sending commands to switch.  Should really remove
            // switch if this happens instead.
            ex.printStackTrace();
            assert(false);
        }
        return floodlight_callback.wait_on_complete();
    }

    @Override
    public void start()
    {
    }

    @Override
    public void stop()
    {
        pronghorn_floodlight.shutdown_all_now();
    }
}