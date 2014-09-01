package experiments;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;

import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import pronghorn.switch_factory.IVersionListenerFactory;

import experiments.Util;
import experiments.Util.LatencyThread;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.ReadOnlyJava.ReadOnly;


public class ReadOnlyLatency
{
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_WARMUP_ARG_INDEX = 1;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 3;
    public static final int VERSION_LISTENER_ARG_INDEX = 4;
    
    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 5)
        {
            System.out.println("\nExpecting 5 arguments.\n");
            print_usage();
            return;
        }

        int num_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);
        
        int num_warmup_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_WARMUP_ARG_INDEX]);
        
        int num_threads = 1;

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);

        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];
        
        RalphGlobals ralph_globals = new RalphGlobals();
        IVersionListenerFactory version_listener_factory =
            VersionListenerFactoryArgs.produce_factory(
                args[VERSION_LISTENER_ARG_INDEX],ralph_globals);

        
        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        ReadOnly read_only_app = null;
        try
        {
            prong = new Instance(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            read_only_app = new ReadOnly(
                ralph_globals,new SingleSideConnection());
            
            prong.add_application(num_switches_app,Util.ROOT_APP_ID);
            prong.add_application(read_only_app,Util.ROOT_APP_ID);
            
        }
        catch (Exception _ex)
        {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        FloodlightShim shim = new FloodlightShim();
        
        SwitchStatusHandler switch_status_handler =
            new SwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                true,collect_statistics_period_ms,
                version_listener_factory);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();

        // wait for first switch to connect
        Util.wait_on_switches(num_switches_app);

        List<LatencyThread> warmup_threads= new ArrayList<LatencyThread>();
        List<LatencyThread> all_threads = new ArrayList<LatencyThread>();
        try
        {
            // warmups
            for (int i = 0; i < num_threads; ++i)
            {
                warmup_threads.add(
                    new LatencyThread(read_only_app,"",num_warmup_ops_to_run));
            }   
            for (LatencyThread lt : warmup_threads)
                lt.start();
            for (LatencyThread lt : warmup_threads)
                lt.join();
            

            // real calculated values         
            for (int i = 0; i < num_threads; ++i)
            {
                all_threads.add(
                    new LatencyThread(read_only_app,"",num_ops_to_run));
            }
            
            for (LatencyThread lt : all_threads)
                lt.start();
            
            // wait for all threads to finish and collect their results
            for (LatencyThread lt : all_threads)
                lt.join();
            
        } catch (Exception _ex) {
            _ex.printStackTrace();
            assert(false);
            return;
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

        // NUMBER_TIMES_TO_WARMUP_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to use for warmup\n";
        
        // COLLECT_STATISTICS_ARG_INDEX
        usage_string +=
            "\n\t<int> : period for collecting individual switch stastics " +
            "in ms.  < 0 if should not collect any statistics\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";
        
        // VERSION_LISTENER_ARG_INDEX
        usage_string += VersionListenerFactoryArgs.usage_string();

        System.out.println(usage_string);
    }
}
