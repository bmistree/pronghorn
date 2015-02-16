package experiments;

import java.util.concurrent.ConcurrentLinkedQueue;

import ralph.BoostedManager.DeadlockAvoidanceAlgorithm;
import ralph.RalphGlobals;

import pronghorn.ft_ops.FloodlightFlowTableToHardware;
import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.InstanceJava.Instance;

import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.FairnessApplicationJava.FairnessApplication;


public class SingleControllerFairness
{
    public static final int USE_WOUND_WAIT_ARG_INDEX = 0;
    public static final int NUM_EXTERNAL_CALLS_ARG_INDEX = 1;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_INDEX = 3;
    
    // This queue keeps track of all the work in the system
    final static ConcurrentLinkedQueue<String> tsafe_queue =
        new ConcurrentLinkedQueue<String>();


    // extra debugging flag: something for us to watch out for in case we had an
    // exception.
    public static FairnessApplication fairness_app_a = null;
    public static FairnessApplication fairness_app_b = null;

    public static void main (String[] args)
    {
        if (args.length != 4)
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

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);
        
        String result_filename = args[OUTPUT_FILENAME_INDEX];

        
        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        RalphGlobals ralph_globals = null;
        try
        {
            if (use_wound_wait)
            {
                RalphGlobals.Parameters rg_params =
                    new RalphGlobals.Parameters();
                rg_params.deadlock_avoidance_algorithm =
                    DeadlockAvoidanceAlgorithm.WOUND_WAIT;
                
                ralph_globals = new RalphGlobals(rg_params);
            }
            else
                ralph_globals = new RalphGlobals();

            prong = Instance.create_single_sided(ralph_globals);
            num_switches_app =
                GetNumberSwitches.create_single_sided(ralph_globals);
            fairness_app_a =
                FairnessApplication.create_single_sided(ralph_globals);
            fairness_app_b =
                FairnessApplication.create_single_sided(ralph_globals);
            
            prong.add_application(num_switches_app,Util.ROOT_APP_ID);
            prong.add_application(fairness_app_a,Util.ROOT_APP_ID);
            prong.add_application(fairness_app_b,Util.ROOT_APP_ID);
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
                true,collect_statistics_period_ms);

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