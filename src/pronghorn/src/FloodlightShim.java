package pronghorn;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import pronghorn.IFloodlightShim;
import pronghorn.ft_ops.FTableUpdate;
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
public class FloodlightShim implements IFloodlightShim
{
    protected static final Logger log =
        LoggerFactory.getLogger(FloodlightShim.class);

    private IPronghornService pronghorn_floodlight = null;

    /**
       Whenever we see that there was a new switch or we see that a
       switch went down, we notify these handlers.
     */
    private ReentrantLock handler_lock = new ReentrantLock();

    /**
       Each flow mod we push to a switch must have a unique xid.  This
       way, we know what flow mod error-ed out and can recover.
       Although we only need to generate a unique id per switch (ie.,
       switch 1 can have xid 0 and switch 2 can have xid 0), just
       using a single generator to simplify life.
     */
    private final AtomicInteger xid_generator = new AtomicInteger();

    private static class UpdateInfo {
        private final String switch_id;
        private final Map<Integer, FTableUpdate> xidToUpdate =
            new HashMap<Integer, FTableUpdate>();
        private FloodlightShimBarrierCallback outstanding_callback;

        private UpdateInfo(String switch_id) {
            this.switch_id = switch_id;
        }
    }

    private final Map<String, UpdateInfo> switchIdToUpdateInfo =
        new HashMap<String, UpdateInfo>();

    public FloodlightShim()
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


    /** IFloodlightShim methods */
    @Override
    public Future<List<OFStatistics>> get_port_stats(String switch_id)
        throws IOException
    {
        return pronghorn_floodlight.get_port_stats(switch_id);
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

        UpdateInfo updateInfo = new UpdateInfo(switch_id);
        updateInfo.outstanding_callback = new FloodlightShimBarrierCallback();
        switchIdToUpdateInfo.put(switch_id, updateInfo);
        for (FTableUpdate update : updates)
        {
            try
            {
                int xid = xid_generator.getAndIncrement();
                pronghorn_floodlight.issue_flow_mod(
                    update.to_flow_mod(xid), switch_id);
                updateInfo.xidToUpdate.put(xid, update);
                updateInfo.outstanding_callback.add_xid(xid);
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
            pronghorn_floodlight.barrier(
                switch_id, updateInfo.outstanding_callback);
        }
        catch (IOException ex)
        {
            // FIXME: assuming that will not have IOException when
            // sending commands to switch.  Should really remove
            // switch if this happens instead.
            ex.printStackTrace();
            assert(false);
        }
        return updateInfo.outstanding_callback.wait_on_complete();
    }

    /**
     * Request outstanding_callback to partially undo changes.
     * @return true if partial undo succeeds and false otherwise
     */
    @Override
    public boolean partial_undo(String switch_id) {
        UpdateInfo updateInfo = switchIdToUpdateInfo.get(switch_id);
        FloodlightShimBarrierCallback outstanding_callback = updateInfo.outstanding_callback;
        if (outstanding_callback.get_barrier_failure()) {
            // Can't undo in this case.
            return false;
        }

        FloodlightShimBarrierCallback floodlight_callback = new FloodlightShimBarrierCallback();
        Set<Integer> to_undo_xids = outstanding_callback.get_non_failed_xids();
        List<FTableUpdate> to_update = new ArrayList<FTableUpdate>();
        for (Integer xid : to_undo_xids) {
            FTableUpdate undoer = updateInfo.xidToUpdate.get(xid).create_undo();
            int new_xid = xid_generator.getAndIncrement();
            try {
                pronghorn_floodlight.issue_flow_mod(undoer.to_flow_mod(new_xid), switch_id);
            } catch (IOException ex) {
                ex.printStackTrace();
                throw new RuntimeException(ex);
            }
            floodlight_callback.add_xid(new_xid);
        }
        try {
            pronghorn_floodlight.barrier(switch_id,floodlight_callback);
        } catch(IOException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
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