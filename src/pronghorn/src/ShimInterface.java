package pronghorn;

import java.util.List;

/**
   Shim layer translates between Pronghorn and Floodlight.  Any shim
   must support this interface.
 */
public interface ShimInterface
{
    public void start();
    public void stop();
    public void subscribe_switch_status_handler(SwitchStatusHandler ssh);
    public void unsubscribe_switch_status_handler(SwitchStatusHandler ssh);
    public boolean switch_rtable_updates(
        String switch_id,List<RTableUpdate> updates);
}