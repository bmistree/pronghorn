package pronghorn;

import java.util.concurrent.Future;
import java.util.List;
import java.io.IOException;

import org.openflow.protocol.statistics.OFStatistics;

/**
   Shim layer translates between Pronghorn and Floodlight.  Any shim
   must support this interface.
 */
public interface ShimInterface
{
    public void start();
    public void stop();
    public void subscribe_switch_status_handler(
        ISwitchStatusHandler status_handler);
    public void unsubscribe_switch_status_handler(
        ISwitchStatusHandler status_handler);
    public boolean switch_rtable_updates(
        String switch_id,List<FTableUpdate> updates);

    /**
       Can return null, eg., if switch is missing.
     */
    public Future<List<OFStatistics>> get_port_stats(String switch_id)
        throws IOException;
}