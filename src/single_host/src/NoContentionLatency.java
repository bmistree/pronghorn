package single_host;

import single_host.JavaPronghornInstance.PronghornInstance;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import java.lang.Thread;
import java.util.ArrayList;

public class NoContentionLatency
{
    public static final int FLOODLIGHT_PORT_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 1;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 2)
        {
            print_usage();
            return;
        }
        
        int floodlight_port =
            Integer.parseInt(args[FLOODLIGHT_PORT_ARG_INDEX]);

        int num_ops_to_run = 
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);


        /* Start up pronghorn */
        PronghornInstance prong = null;

        try {
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


        /* wait a while to ensure that all switches are connected */
        try {
            Thread.sleep(SETTLING_TIME_WAIT);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            assert(false);
        }
            

        /* Discover the id of the first connected switch */
        String switch_id = null;
        try {
            NonAtomicInternalList<String,String> switch_list =
                prong.list_switch_ids();

            if (switch_list.get_len(null) == 0)
            {
                System.out.println(
                    "No switches attached to pronghorn: error");
                assert(false);
            }

            // get first switch id from key.  (If used Double(1), would get
            // second element from list)
            Double index_to_get_from = new Double(0);
            switch_id = switch_list.get_val_on_key(null,index_to_get_from);
        } catch (Exception _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        /* perform all operations and determine how long they take */
        ArrayList<Long>all_times = new ArrayList<Long>();
        for (int i = 0; i < num_ops_to_run; ++i)
        {
            long start_time = System.nanoTime();
            try {
                prong.single_op(switch_id);
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
            long total_time = System.nanoTime() - start_time;
            all_times.add(total_time);
        }

        // print csv list of runtimes in ns to stdout
        for (Long time : all_times)
            System.out.print(time.toString() + ",");
        System.out.print("\n");
    }


    public static void print_usage()
    {
        System.out.println(
            "\nSingleHost <int: floodlight port number> " + 
            "<int: num ops to run>\n");
    }
    
}