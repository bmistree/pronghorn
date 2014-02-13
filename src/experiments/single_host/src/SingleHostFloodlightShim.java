package single_host;

import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import pronghorn.ShimInterface;
import pronghorn.SwitchStatusHandler;
import pronghorn.RTableUpdate;

import net.floodlightcontroller.pronghornmodule.IPronghornService;
import net.floodlightcontroller.core.Main;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.pronghornmodule.IPronghornService;

/**
   Serves as intermediate layer between Ralph and Floodlight
 */
public class SingleHostFloodlightShim implements Runnable, ShimInterface
{
    private IPronghornService pronghorn_floodlight = null;
    
    public SingleHostFloodlightShim(String settings_filename)
    {
        Main.ProviderPronghornTuple ppt = null;
        try
        {
            ppt = Main.get_controller(settings_filename);
            pronghorn_floodlight = ppt.pronghorn;
        }
        catch (FloodlightModuleException ex)
        {
            ex.printStackTrace();
            assert(false);
        }
    }

    @Override
    public void run()
    {
    }
    
    /** ShimInterface methods */
    @Override
    public void subscribe_switch_status_handler(SwitchStatusHandler ssh)
    {
    }
    @Override
    public void unsubscribe_switch_status_handler(SwitchStatusHandler ssh)
    {
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