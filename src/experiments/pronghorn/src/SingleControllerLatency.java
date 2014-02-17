package experiments;

import single_host.SingleHostFloodlightShim;
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
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int NUMBER_THREADS_ARG_INDEX = 1;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 2;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 3)
        {
            print_usage();
            return;
        }

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

        SingleHostFloodlightShim shim = new SingleHostFloodlightShim();
        
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,
                FloodlightRoutingTableToHardware.FLOODLIGHT_ROUTING_TABLE_TO_HARDWARE_FACTORY);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        
        // wait for first switch to connect
        Util.wait_on_switches(prong);
        // what's the first switch's id.
        String switch_id = Util.first_connected_switch_id(prong);


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

        shim.stop();
    }

    private static void print_usage()
    {
        String usage_string = "";

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
