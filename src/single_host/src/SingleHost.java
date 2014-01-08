package single_host;

import single_host.JavaPronghornInstance.PronghornInstance;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import java.lang.Thread;

public class SingleHost
{
    public static final int FLOODLIGHT_PORT_ARG_INDEX = 0;
    
    public static void main (String[] args)
    {
        if (args.length != 1)
        {
            print_usage();
            return;
        }

        int floodlight_port =
            Integer.parseInt(args[FLOODLIGHT_PORT_ARG_INDEX]);

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

        SingleHostRESTShim shim = new  SingleHostRESTShim(floodlight_port);
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(prong,shim);
        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        while (true)
        {
            try{
                Thread.sleep(1000);
            } catch(InterruptedException ex)
            {
                break;
            }
            try {
                Double num_switches = prong.num_switches();
                System.out.println("Num switches: " + num_switches.toString());
                // add garbage entries to all switches in system
                prong.add_entries_to_all();
            } catch (Exception _ex)
            {
                _ex.printStackTrace();
                break;
            }
        }
    }


    public static void print_usage()
    {
        System.out.println("\nSingleHost <int: floodlight port number>\n");
    }
    
}