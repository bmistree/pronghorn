package experiments;

import single_host.SingleHostRESTShim;
import single_host.SingleHostSwitchStatusHandler;
import single_host.JavaPronghornInstance.PronghornInstance;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import pronghorn.FloodlightRoutingTableToHardware;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;
import experiments.Util.LatencyThread;


public class SingleControllerLatency
{
    public static final int FLOODLIGHT_PORT_CSV_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 1;
    public static final int NUMBER_THREADS_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 3;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 4)
        {
            print_usage();
            return;
        }

        Set<Integer> floodlight_port_set = Util.parse_csv_ports(
            args[FLOODLIGHT_PORT_CSV_ARG_INDEX]);
        
        int num_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        int num_threads =
            Integer.parseInt(args[NUMBER_THREADS_ARG_INDEX]);

        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        
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

        Set<SingleHostRESTShim> shim_set = new HashSet<SingleHostRESTShim>();
        for (Integer port : floodlight_port_set)
            shim_set.add ( new SingleHostRESTShim(port.intValue()));
        
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,
                FloodlightRoutingTableToHardware.FLOODLIGHT_ROUTING_TABLE_TO_HARDWARE_FACTORY);

        for (SingleHostRESTShim shim : shim_set)
        {
            shim.subscribe_switch_status_handler(switch_status_handler);
            shim.start();
        }
        

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

        List<LatencyThread> all_threads = new ArrayList<LatencyThread>();
        for (int i = 0; i < num_threads; ++i)
        {
            all_threads.add(
                new LatencyThread(prong,switch_id,num_ops_to_run));
        }

        for (LatencyThread lt : all_threads)
            lt.start();

        // wait for all threads to finish and collect their results
        for (LatencyThread lt : all_threads)
        {
            try {
                lt.join();
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
                return;
            }
        }

        StringBuffer results_buffer = new StringBuffer();
        for (LatencyThread lt : all_threads)
            lt.write_times(results_buffer);
        Util.write_results_to_file(output_filename,results_buffer.toString());
        
        // actually tell shims to stop.
        for (SingleHostRESTShim shim : shim_set)
            shim.stop();
    }

    private static void print_usage()
    {
        String usage_string = "";

        // FLOODLIGHT_PORT_ARG_INDEX 
        usage_string += "\n\t<int>: floodlight port to connect to\n";

        // NUMBER_OPS_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // NUMBER_THREADS_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number threads.\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
}
