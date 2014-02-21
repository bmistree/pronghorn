package experiments;

import single_host.SingleHostFloodlightShim;
import single_host.SingleHostSwitchStatusHandler;
import single_host.JavaPronghornInstance.PronghornInstance;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import pronghorn.FloodlightRoutingTableToHardware;
import java.lang.Thread;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import RalphDataWrappers.ListTypeDataWrapper;
import pronghorn.RTable._InternalRoutingTableEntry;
import pronghorn.ShimInterface;
import pronghorn.RoutingTableToHardwareFactory;
import pronghorn.RoutingTableToHardware;
import java.util.HashMap;
import java.util.Map.Entry;

import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;


public class SingleControllerError
{
    public static final int NUMBER_OPS_TO_RUN_PER_EXPERIMENT_ARG_INDEX = 0;
    public static final int NUMBER_EXPERIMENTS_TO_RUN_ARG_INDEX = 1;
    public static final int FAILURE_PROB_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 3;

    // wait this long for pronghorn to add all switches
    private static final int SETTLING_TIME_WAIT = 5000;
    private static final int AFTER_FORCE_CLEAR_WAIT_TIME = 1000;

    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 4)
        {
            System.out.println("\nExpected 5 arguments: exiting\n");
            print_usage();
            return;
        }

        int num_ops_to_run_per_experiment = 
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_PER_EXPERIMENT_ARG_INDEX]);

        System.out.println(
            "\nNumber ops to run per experiment: " +
            Integer.toString(num_ops_to_run_per_experiment));
        
        int num_experiments_to_run =
            Integer.parseInt(args[NUMBER_EXPERIMENTS_TO_RUN_ARG_INDEX]);

        // how frequently each switch should independently fail
        double failure_prob =
            Double.parseDouble(args[FAILURE_PROB_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        /* Start up pronghorn */
        PronghornInstance prong = null;

        try {
            prong = new PronghornInstance(
                new RalphGlobals(),new SingleSideConnection());
        } catch (Exception _ex) {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        ErrorShim shim = new ErrorShim();
        ErrorProneFloodlightRoutingTableToHardware.ErrorProneFactory routing_table_to_hardware_factory
            = new ErrorProneFloodlightRoutingTableToHardware.ErrorProneFactory(failure_prob);
        
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                shim,prong,
                routing_table_to_hardware_factory);
        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();


        /* wait a while to ensure that all switches are connected */
        try {
            Thread.sleep(SETTLING_TIME_WAIT);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            assert(false);
        }


        NonAtomicInternalList<String,String> switch_list = null;
        List<String> switch_id_list = new ArrayList<String>();
        int num_switches = -1;
        try {
            switch_list = prong.list_switch_ids();
            num_switches = switch_list.get_len(null);

            if (num_switches == 0)
            {
                System.out.println(
                    "No switches attached to pronghorn: error");
                assert(false);
            }

            for (int i = 0; i < num_switches; ++i)
            {
                String switch_id =
                    switch_list.get_val_on_key(null, new Double((double)i));
                switch_id_list.add(switch_id);
            }
            
        } catch (Exception _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        List<Boolean> run_results = new ArrayList<Boolean>();
        for (int j = 0; j < num_experiments_to_run; ++j)
        {

            shim.force_clear();
            try {
                Thread.sleep(AFTER_FORCE_CLEAR_WAIT_TIME);
            } catch (InterruptedException _ex) {
                _ex.printStackTrace();
                assert(false);
            }        
            
            String a_b_ip = "18.18.";
            try {
                for (int i = 0; i < num_ops_to_run_per_experiment; ++i)
                {
                    int c = (int) (i/256);
                    int d = i % 256;
                    String ip_addr =
                        a_b_ip + Integer.toString(c) + "." + Integer.toString(d);
                    prong.insert_entry_on_all_switches(ip_addr);
                }
            } catch (Exception _ex) {
                _ex.printStackTrace();
                System.out.println(
                    "There was an unknown error.  Should deal with it.");
                assert(false);
            }

            // ensure that each switch in system has correct number of
            // flow table entries.
            HashMap<String,Integer> rules_in_system = shim.num_rules_in_system();
            // first check for agreement betweeen number of flow table
            // entries for each switch.
            int num_rules_in_system = -1;
            boolean result = true;
            for (Entry<String,Integer> pairs : rules_in_system.entrySet())
            {
                int rules_on_switch = pairs.getValue().intValue();
                if (num_rules_in_system == -1)
                    num_rules_in_system = rules_on_switch;

                if (num_rules_in_system != rules_on_switch)
                    result = false;
            }

            // second, check that number of flow table entries on each
            // switch agrees with number we'd expect from having run
            // num_ops_to_run_per_experiment for each test.
            if (num_rules_in_system != num_ops_to_run_per_experiment)
                result = false;

            run_results.add(result);
        }

        // disconnect the shim connection
        shim.stop();

        StringBuffer string_buffer = new StringBuffer();
        for (Boolean result : run_results)
        {
            if (result.booleanValue())
                string_buffer.append("1");
            else
                string_buffer.append("0");
            string_buffer.append(",");
        }
        
        Util.write_results_to_file(output_filename,string_buffer.toString());

        Util.force_shutdown();
    }

    private static void print_usage()
    {
        String usage_string = "";

        // FLOODLIGHT_PORT_ARG_INDEX 
        usage_string += "\n\t<int>: floodlight port to connect to\n";

        // NUMBER_TIMES_TO_RUN_PER_EXPERIMENT_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // NUMBER_EXPERIMENTS_TO_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number experiments to run\n";

        // FAILURE_PROB_ARG_INDEX
        usage_string +=
            "\n\t<double>: Failure probability.  For each transaction a ";
        usage_string +=
            "switch will independently fail with this probability.\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }

    public static class ErrorShim extends SingleHostFloodlightShim
    {
        public ErrorShim()
        {
            super();
        }

        
        /**
           Actually push command to clear routing table to all
           switches.  Definitely use a barrier here.
         */
        public void force_clear()
        {
            /// FIXME: Should reimplement
            System.out.println("Must reimplement force_clear for error shim");
            assert(false);
        }

        /**
           For each switch in system, tells how many rules it has.
           Returns the full number of flow table rules in the system.
         */
        public HashMap<String,Integer> num_rules_in_system()
        {
            /// FIXME: Should reimplement
            System.out.println("Must reimplement force_clear for error shim");
            assert(false);
            return null;
        }
    }


    /**
       Performs the same operations as
       FloodlightRoutingTableToHardware, except every so often
       responds that could not push change to hardware
     */
    public static class ErrorProneFloodlightRoutingTableToHardware
        extends FloodlightRoutingTableToHardware
    {
        public static class ErrorProneFactory
            implements RoutingTableToHardwareFactory
        {
            private double independent_switch_failure_prob = -1.0;
            public ErrorProneFactory (double independent_switch_failure_prob)
            {
                this.independent_switch_failure_prob =
                    independent_switch_failure_prob;
            }
            
            @Override
            public RoutingTableToHardware construct(
                ShimInterface shim, String internal_switch_id)
            {
                return new ErrorProneFloodlightRoutingTableToHardware(
                    shim,internal_switch_id,independent_switch_failure_prob);
            }
        }


        private double independent_switch_failure_prob = -1;
        
        private ErrorProneFloodlightRoutingTableToHardware(
            ShimInterface _shim, String _floodlight_switch_id,
            double independent_switch_failure_prob)
        {
            super(_shim,_floodlight_switch_id);
            this.independent_switch_failure_prob =
                independent_switch_failure_prob;
        }
        
        
        @Override
        public boolean apply_changes_to_hardware(
            ListTypeDataWrapper<
                _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
        {
            boolean real_result = super.apply_changes_to_hardware(dirty);
            if (Math.random() < independent_switch_failure_prob)
                return false;
            return real_result;
        }

        @Override
        public void undo_dirty_changes_to_hardware(
            ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry>
            to_undo)
        {
            super.undo_dirty_changes_to_hardware(to_undo);
        }
    }
}
