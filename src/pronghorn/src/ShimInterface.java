package pronghorn;

import java.util.List;
import net.floodlightcontroller.core.IOFSwitchListener;

/**
   Shim layer translates between Pronghorn and Floodlight.  Any shim
   must support this interface.
 */
public interface ShimInterface
{
    public void start();
    public void stop();
    public void subscribe_switch_status_handler(IOFSwitchListener switch_listener);
    public void unsubscribe_switch_status_handler(IOFSwitchListener switch_listener);
    public boolean switch_rtable_updates(
        String switch_id,List<RTableUpdate> updates);
}