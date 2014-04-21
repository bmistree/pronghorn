package experiments;

import java.util.concurrent.ConcurrentLinkedQueue;

import ralph.BoostedManager.DeadlockAvoidanceAlgorithm;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;

import pronghorn.FloodlightFlowTableToHardware;
import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.FairnessApplicationJava.FairnessApplication;


public class SingleControllerFairness
{
    public static final int USE_WOUND_WAIT_ARG_INDEX = 0;
    public static final int NUM_EXTERNAL_CALLS_ARG_INDEX = 1;
    public static final int OUTPUT_FILENAME_INDEX = 2;

    // This queue keeps track of all the work in the system
    final static ConcurrentLinkedQueue<String> tsafe_queue =
        new ConcurrentLinkedQueue<String>();


    // extra debugging flag: something for us to watch out for in case we had an
    // exception.
    public static FairnessApplication fairness_app_a = null;
    public static FairnessApplication fairness_app_b = null;

    public static void main (String[] args)
    {
        if (args.length != 3)
        {
            FairnessUtil.print_usage();
            return;
        }

        /* Grab args */ 
        boolean use_wound_wait =
            Boolean.parseBoolean(args[USE_WOUND_WAIT_ARG_INDEX]);
        
        System.out.println(
            "\nUsing wound wait: " + Boolean.toString(use_wound_wait) + "\n");
        
        int num_external_calls = 
            Integer.parseInt(args[NUM_EXTERNAL_CALLS_ARG_INDEX]);
        
        String result_filename = args[OUTPUT_FILENAME_INDEX];

        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        try
        {
            RalphGlobals ralph_globals = null;
            if (use_wound_wait)
            {
                ralph_globals = new RalphGlobals(
                    DeadlockAvoidanceAlgorithm.WOUND_WAIT);
            }
            else
                ralph_globals = new RalphGlobals();

            prong = new Instance(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            fairness_app_a = new FairnessApplication(
                ralph_globals,new SingleSideConnection());
            fairness_app_b = new FairnessApplication(
                ralph_globals,new SingleSideConnection());
            
            prong.add_application(num_switches_app);
            prong.add_application(fairness_app_a);
            prong.add_application(fairness_app_b);
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
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                false);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        
        // wait for switches to connect
        Util.wait_on_switches(num_switches_app);
        // what is the first switch's id (this is the switch that will
        // contend for).
        String switch_id = Util.first_connected_switch_id(num_switches_app);
        // This queue keeps track of all the work in the system
        ConcurrentLinkedQueue<String> tsafe_queue =
            new ConcurrentLinkedQueue<String>();
        FairnessUtil.run_operations(
            fairness_app_a,fairness_app_b,switch_id,num_external_calls,
            tsafe_queue);

        FairnessUtil.write_results(result_filename,tsafe_queue);
        // actually tell shims to stop.
        shim.stop();
        Util.force_shutdown();
    }
}