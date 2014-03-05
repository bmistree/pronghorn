package experiments;

import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.OffOnApplicationJava.OffOnApplication;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import pronghorn.FloodlightFlowTableToHardware;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;
import experiments.Util.LatencyThread;


public class ReadOnlyLatency
{
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 1;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 2)
        {
            System.out.println("\nExpecting 2 arguments.\n");
            print_usage();
            return;
        }

        int num_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        int num_threads = 1;


        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        
        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        OffOnApplication off_on_app = null;
        try
        {
            RalphGlobals ralph_globals = new RalphGlobals();
            prong = new Instance(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            off_on_app = new OffOnApplication(
                ralph_globals,new SingleSideConnection());
            
            prong.add_application(num_switches_app);
            prong.add_application(off_on_app);
            
        }
        catch (Exception _ex)
        {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        SingleInstanceFloodlightShim shim = new SingleInstanceFloodlightShim();
        
        SingleInstanceSwitchStatusHandler switch_status_handler =
            new SingleInstanceSwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();

        /* wait a while to ensure that all switches are connected */
        try {
            Thread.sleep(SETTLING_TIME_WAIT);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            assert(false);
        }
            
        List<LatencyThread> all_threads = new ArrayList<LatencyThread>();
        for (int i = 0; i < num_threads; ++i)
            all_threads.add(new LatencyThread(off_on_app,"",num_ops_to_run,true));

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

        StringBuffer string_buffer = new StringBuffer();
        for (LatencyThread lt : all_threads)
            lt.write_times(string_buffer);

        Util.write_results_to_file(output_filename,string_buffer.toString());
        
        // actually tell shims to stop.
        shim.stop();
        Util.force_shutdown();        
    }

    private static void print_usage()
    {
        String usage_string = "";

        // NUMBER_TIMES_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
}
