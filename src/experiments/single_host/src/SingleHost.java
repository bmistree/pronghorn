package single_host;

import single_host.JavaPronghornInstance.PronghornInstance;
import RalphConnObj.SingleSideConnection;
import pronghorn.FloodlightRoutingTableToHardware;
import ralph.RalphGlobals;
import java.lang.Thread;

public class SingleHost
{
    public static void main (String[] args)
    {
        PronghornInstance prong = null;
        try
        {
            prong = new PronghornInstance(
                new RalphGlobals(),
                "", new SingleSideConnection());
        } catch (Exception _ex) {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        SingleHostFloodlightShim shim = new SingleHostFloodlightShim();
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,
                FloodlightRoutingTableToHardware.FLOODLIGHT_ROUTING_TABLE_TO_HARDWARE_FACTORY);
        shim.subscribe_switch_status_handler(switch_status_handler);
        boolean block_traffic = true;
        while (true)
        {
            try{
                Thread.sleep(3000);
            } catch(InterruptedException ex)
            {
                break;
            }
            try {
                Double num_switches = prong.num_switches();
                System.out.println("Num switches: " + num_switches.toString());

                // cycle between allowing traffic between 10.0.0.1 and 10.0.0.2
                // for all switches in system and blocking it
                if (block_traffic)
                    prong.block_traffic_all_switches("10.0.0.1","10.0.0.2");
                else
                    prong.remove_first_entry_all_switches();

                System.out.println("Blocking traffic: " + block_traffic + "\n");
                block_traffic = ! block_traffic;
                
            } catch (Exception _ex)
            {
                _ex.printStackTrace();
                break;
            }
        }
    }
}