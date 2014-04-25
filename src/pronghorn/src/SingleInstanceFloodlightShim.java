package pronghorn;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Future;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import pronghorn.ShimInterface;
import pronghorn.FTableUpdate;
import pronghorn.ISwitchStatusHandler;

import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.pronghornmodule.IPronghornService;
import net.floodlightcontroller.core.Main;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.pronghornmodule.IPronghornService;

import org.openflow.protocol.statistics.OFStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
   Serves as intermediate layer between Ralph and Floodlight
 */
public class SingleInstanceFloodlightShim
    implements ShimInterface
{
    protected static final Logger log =
        LoggerFactory.getLogger(SingleInstanceFloodlightShim.class);
    
    private IPronghornService pronghorn_floodlight = null;

    /**
       Whenever we see that there was a new switch or we see that a
       switch went down, we notify these handlers. 
     */
    private ReentrantLock handler_lock = new ReentrantLock();
    
    public SingleInstanceFloodlightShim()
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
    public Future<List<OFStatistics>> get_stats(String switch_id)
        throws IOException
    {
        return pronghorn_floodlight.get_stats(switch_id);
    }
    
    @Override
    public void subscribe_switch_status_handler(ISwitchStatusHandler status_handler)
    {
        pronghorn_floodlight.register_switch_listener(status_handler);
        pronghorn_floodlight.register_link_discovery_listener(status_handler);
    }
    
    @Override
    public void unsubscribe_switch_status_handler(ISwitchStatusHandler status_handler)
    {
        pronghorn_floodlight.unregister_switch_listener(status_handler);
        log.warn("No method for unregistering for link discovery messages.");
        // still need to register link discovery listener
    }
    
    @Override
    public boolean switch_rtable_updates(
        String switch_id,List<FTableUpdate> updates)
    {
        FloodlightShimBarrierCallback floodlight_callback =
            new FloodlightShimBarrierCallback();
        
        for (FTableUpdate update : updates)
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