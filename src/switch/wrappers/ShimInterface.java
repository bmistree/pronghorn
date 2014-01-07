package pronghorn;


/**
   Shim layer translates between Pronghorn and Floodlight.  Any shim
   must support this interface.
 */
public interface ShimInterface
{
    public void start();
    public void subscribe_switch_status_handler(SwitchStatusHandler ssh);
    public void unsubscribe_switch_status_handler(SwitchStatusHandler ssh);
    /**
       FIXME: need to actually define format of this more carefully.
     */
    public boolean switch_rtable_update(String switch_id);
}