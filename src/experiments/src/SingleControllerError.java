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
    public static final int OUTPUT_FILENAME_ARG_INDEX = 2;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    public static final boolean SHOULD_SPECULATE = true;

    // gives time to settle changes
    private static final int SLEEP_TIME_BETWEEN_TESTS_MS = 1000;
    private static final int MAX_NUM_OPS_BEFORE_CHECK = 20;
    private static final Random rand = new Random();
    
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

        float failure_probability =
            Float.parseFloat(args[FAILURE_PROBABILITY_ARG_INDEX]);

        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        
        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        OffOnApplication off_on_app = null;
        RalphGlobals ralph_globals = new RalphGlobals();
        try
        {
            prong = new Instance(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            off_on_app = new OffOnApplication(
                ralph_globals,new SingleSideConnection());
            prong.add_application(num_switches_app);
            prong.add_application(off_on_app);
                        
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
                false);

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

        // Contains trues if test succeeded, false if test failed.
        List<Boolean> results_list = new ArrayList<Boolean>();
        
        List<String> switch_id_list = null;
        String faulty_switch_id = "some_switch";
        try
        {
            _InternalSwitch internal_switch =
                ErrorUtil.create_faulty_switch(
                    ralph_globals,faulty_switch_id,SHOULD_SPECULATE,
                    failure_probability);
            prong.add_switch(internal_switch);

            switch_id_list = Util.get_switch_id_list (num_switches_app);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            return;
        }

        int num_switches = switch_id_list.size();
        

        /*
          Successively add and remove flow table entries, even in the
          face of errors.
         */
        for (int i = 0; i < num_ops_to_run; ++i)
        {
            try
            {
                Thread.sleep(SLEEP_TIME_BETWEEN_TESTS_MS);
            }
            catch(InterruptedException ex)
            {
                ex.printStackTrace();
                assert(false);
            }
            
            // perform random number of operations
            int num_ops_to_perform =
                1 + (rand.nextInt() % MAX_NUM_OPS_BEFORE_CHECK);
            for (int j = 0; j < num_ops_to_perform; ++j)
            {
                try
                {
                    if ((j % 2) == 0)
                        off_on_app.block_traffic_all_switches();
                    else
                        off_on_app.remove_first_entry_all_switches();
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                    return;
                }
            }

            boolean logical_correct =
                check_logical_state(
                    switch_id_list,off_on_app,num_ops_to_perform,
                    faulty_switch_id);
            boolean physical_correct =
                check_physical_state(
                    num_ops_to_perform,
                    // subtracting 1 from total number of switches
                    // because one switch is the simulated faulty
                    // software switch
                    num_switches -1);

            
            results_list.add(logical_correct && physical_correct);

            // clear hardware
            clear_hardware(num_switches -1);

            // logical clear switches
            logical_clear_switches(off_on_app,switch_id_list);
        }

        StringBuffer results_buffer = new StringBuffer();
        for (Boolean trial_result : results_list)
        {
            if (trial_result.booleanValue())
                results_buffer.append("1,");
            else
                results_buffer.append("0,");
        }
        Util.write_results_to_file(output_filename,results_buffer.toString());

        // stop and forst stop
        shim.stop();
        Util.force_shutdown();
    }

    
    private static void logical_clear_switches(
        OffOnApplication off_on_app,List<String> switch_list)
    {
        for (String switch_id : switch_list)
        {
            try
            {
                off_on_app.logical_clear_switch_do_not_flush_clear_to_hardware(switch_id);
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                assert(false);
            }
        }
    }
    
    private static void clear_hardware(int num_physical_switches)
    {
        List<String> ovs_switch_names =
            Util.produce_ovs_switch_names(num_physical_switches);

        for (String ovs_switch_name : ovs_switch_names)
            Util.ovs_clear_flows_hardware (ovs_switch_name);
    }

    /**
       Run through all the switches and check that they have the
       expected number of flow table entries on them.

       @param {int} num_physical_switches --- Should be the total
       number of switches in the system, minus one (the one that's
       used to force errors).
     */
    private static boolean check_physical_state(
        int num_ops_performed, int num_physical_switches)
    {
        int expected_number_of_entries = 0;
        if ((num_ops_performed %2) == 1)
            expected_number_of_entries = 1;

        List<String> ovs_switch_names =
            Util.produce_ovs_switch_names(num_physical_switches);

        for (String ovs_switch_name : ovs_switch_names)
        {
            int flow_tab_entries =
                Util.ovs_hardware_flow_table_size(ovs_switch_name);
            if (flow_tab_entries != expected_number_of_entries)
                return false;
        }
        return true;
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
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }

    private static boolean check_logical_state(
        List<String> switch_id_list,OffOnApplication off_on_app,
        int num_ops_to_perform, String faulty_switch_to_skip)
    {
        // now, check that all the switches have the correct
        // number of rules on them, flush hardware, and flush
        // logical rules.
        boolean expected_number_rules_zero =
            (num_ops_to_perform %2) == 0;
        boolean all_expected = true;
        for (String pronghorn_switch_id : switch_id_list)
        {
            // ignore faulty switch id, because we aren't cleaning it
            // up after it fails.  Here, we're more concerned that the
            // other switches stay in sync, despite error.
            if (pronghorn_switch_id.equals(faulty_switch_to_skip))
                continue;
            try
            {
                int num_flow_table_entries =
                    off_on_app.num_flow_table_entries(pronghorn_switch_id).intValue();
                if (expected_number_rules_zero)
                {
                    if (num_flow_table_entries != 0)
                    {
                        all_expected = false;
                    }
                }
                else
                {
                    if (num_flow_table_entries != 1)
                    {
                        all_expected = false;
                    }
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                assert(false);
            }
        }
        return all_expected;
    }
}
