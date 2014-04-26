package experiments;

import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.ErrorApplicationJava.ErrorApplication;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import pronghorn.FloodlightFlowTableToHardware;
import pronghorn.SwitchJava._InternalSwitch;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import experiments.Util.HostPortPair;
import experiments.Util;
import experiments.Util.LatencyThread;


public class SingleControllerError
{
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int FAILURE_PROBABILITY_ARG_INDEX = 1;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 3;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    public static final boolean SHOULD_SPECULATE = true;
    
    
    public static void main (String[] args) 
    {
        /* Grab arguments */
        if (args.length != 4)
        {
            print_usage();
            return;
        }

        int num_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        float failure_probability =
            Float.parseFloat(args[FAILURE_PROBABILITY_ARG_INDEX]);

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        
        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        ErrorApplication error_app = null;
        RalphGlobals ralph_globals = new RalphGlobals();
        try
        {
            prong = new Instance(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            error_app = new ErrorApplication(
                ralph_globals,new SingleSideConnection());
            prong.add_application(num_switches_app,Util.ROOT_APP_ID);
            prong.add_application(error_app,Util.ROOT_APP_ID);
                        
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        SingleInstanceFloodlightShim shim = new SingleInstanceFloodlightShim();
        
        SingleInstanceSwitchStatusHandler switch_status_handler =
            new SingleInstanceSwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                SHOULD_SPECULATE,collect_statistics_period_ms);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();

        try
        {
            Thread.sleep(1000);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            assert(false);
        }
        
        // // wait for first switch to connect
        Util.wait_on_switches(num_switches_app);
        String faulty_switch_id = "some_switch";
        ErrorUtil.add_faulty_switch(
            ralph_globals,faulty_switch_id, SHOULD_SPECULATE,
            failure_probability,prong);
        
        List<String> switch_id_list =
            Util.get_switch_id_list (num_switches_app);

        
        // Contains trues if test succeeded, false if test failed.
        List<Boolean> results_list =
            ErrorUtil.run_operations(
                num_ops_to_run,error_app,faulty_switch_id,switch_id_list);
        ErrorUtil.write_results(output_filename,results_list);

        // stop and forst stop
        shim.stop();
        Util.force_shutdown();
    }

        
    
    private static void print_usage()
    {
        String usage_string = "";

        // NUMBER_OPS_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // FAILURE_PROBABILITY_ARG_INDEX
        usage_string +=
            "\n\t<float>: failure probability.\n";

        // COLLECT_STATISTICS_ARG_INDEX
        usage_string +=
            "\n\t<int> : period for collecting individual switch stastics " +
            "in ms.  < 0 if should not collect any statistics\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }

}
