package experiments;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;

import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.StatisticsUpdater;
import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;
import pronghorn.ft_ops.FlowTableToHardware;
import pronghorn.ft_ops.FTableUpdate;

import pronghorn.switch_factory.SwitchFactory;
import pronghorn.switch_factory.PronghornInternalSwitch;

import experiments.Util;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.OffOnApplicationJava.OffOnApplication;


public class SimulatedSwitchThroughput
{
    public static final int NUMBER_SWITCHES_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 1;
    public static final int NUMBER_OPS_TO_WARMUP_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 3;
    
    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    public static final boolean SHOULD_SPECULATE = false;

    public static final boolean COARSE_LOCKING = false;
    public static final int THREADS_PER_SWITCH = 1;

    
    public static final AtomicBoolean had_exception = new AtomicBoolean(false);
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 3)
        {
            print_usage();
            return;
        }

        int num_switches = 
            Integer.parseInt(args[NUMBER_SWITCHES_ARG_INDEX]);
        
        int num_ops_to_run = 
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        int num_warmup_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_WARMUP_ARG_INDEX]);

        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        RalphGlobals ralph_globals = new RalphGlobals();
        
        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        try
        {
            prong = Instance.create_single_sided(ralph_globals);

            num_switches_app =
                GetNumberSwitches.create_single_sided(ralph_globals);

            prong.add_application(num_switches_app,Util.ROOT_APP_ID);            
        }
        catch (Exception _ex)
        {
            System.out.println("\n\nERROR CONNECTING\n\n");
            had_exception.set(true);
            return;
        }

        List<String> switch_ids = new ArrayList<String>();
        for (int i = 0; i < num_switches; ++i)
        {
            String switch_id = Integer.toString(i);
            switch_ids.add(switch_id);
        }

        FloodlightShim shim = new FloodlightShim();
        SwitchStatusHandler switch_status_handler =
            new SwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                SHOULD_SPECULATE,-1);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();

        SwitchFactory switch_factory =
            new SwitchFactory(
                ralph_globals,SHOULD_SPECULATE,-1);

        SimulatedFlowTableToHardware simulated_flow_table_to_hardware =
            new SimulatedFlowTableToHardware();
        
        StatisticsUpdater stats_updater = new StatisticsUpdater(prong);
        for (String switch_id : switch_ids)
        {
            PronghornInternalSwitch internal_switch =
                switch_factory.construct(
                    10.,simulated_flow_table_to_hardware,shim,
                    switch_id,stats_updater);
            try
            {
                prong.add_switch(internal_switch);
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                had_exception.set(true);
                assert(false);
            }
        }

        List<String> internal_switch_id_list =
            Util.get_switch_id_list ( num_switches_app);
        
        List<OffOnApplication> off_on_app_list =
            SingleControllerThroughput.create_off_on_app_list(
                num_switches,prong,ralph_globals);

        SingleControllerThroughput.run_experiments(
            internal_switch_id_list,off_on_app_list,num_ops_to_run,
            num_warmup_ops_to_run, COARSE_LOCKING,
            THREADS_PER_SWITCH,output_filename,num_switches);
        
        // actually tell shims to stop.
        shim.stop();

        Util.force_shutdown();
    }

    
    
    private static void print_usage()
    {
        String usage_string = "";

        // NUMBER_SWITCHES_ARG_INDEX
        usage_string += "\n\t<int>: Number switches to run\n";
        
        // NUMBER_TIMES_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // NUMBER_TIMES_TO_WARMUP_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to use for warmup\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
        
    }


    public static class SimulatedFlowTableToHardware extends FlowTableToHardware
    {
        @Override
        public boolean apply(List<FTableUpdate> to_apply)
        {
            return true;
        }

        @Override    
        public boolean undo(List<FTableUpdate> to_undo)
        {
            return true;
        }
    }
    
}
