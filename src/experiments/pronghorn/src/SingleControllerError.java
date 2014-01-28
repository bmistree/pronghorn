package experiments;

import single_host.SingleHostRESTShim;
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

public class SingleControllerError {
	
    public static final int FLOODLIGHT_PORT_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_PER_EXPERIMENT_ARG_INDEX = 1;
    public static final int NUMBER_EXPERIMENTS_TO_RUN_ARG_INDEX = 2;
    public static final int FAILURE_PROB_ARG_INDEX = 3;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 4;

    // wait this long for pronghorn to add all switches
    private static final int SETTLING_TIME_WAIT = 5000;
    private static final int AFTER_FORCE_CLEAR_WAIT_TIME = 1000;

    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 5)
        {
            System.out.println("\nExpected 5 arguments: exiting\n");
            return;
        }
        
        int floodlight_port =
            Integer.parseInt(args[FLOODLIGHT_PORT_ARG_INDEX]);
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
                new RalphGlobals(),
                "", new SingleSideConnection());
        } catch (Exception _ex) {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        ErrorShim shim = new ErrorShim(floodlight_port);
        ErrorProneFloodlightRoutingTableToHardware.ErrorProneFactory routing_table_to_hardware_factory
            = new ErrorProneFloodlightRoutingTableToHardware.ErrorProneFactory(failure_prob);
        
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,shim,
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

            System.out.println(result);
            run_results.add(result);

        }
        shim.stop();
    }


    private static class ErrorShim extends SingleHostRESTShim
    {
        public ErrorShim(int _floodlight_port)
        {
            super(_floodlight_port);
        }

        /**
           Actually push command to clear routing table to all
           switches.  Definitely use a barrier here.
         */
        public void force_clear()
        {
            String force_clear_resource =
                "/wm/staticflowentrypusher/clear/all/json";
            String get_result = issue_get (force_clear_resource);
        }

        /**
           For each switch in system, tells how many rules it has.
           Returns the full number of flow table rules in the system.
         */
        public HashMap<String,Integer> num_rules_in_system()
        {
            // we're assuming that only a single switch is connected.
            // Get that single switch's id.
            HashMap<String,Integer> to_return = new HashMap<String,Integer>();
            for (String some_switch_id : switch_id_set)
            {
                String switch_id = some_switch_id;
                to_return.put(
                    switch_id,
                    num_rules_single_switch(switch_id));
            }
            return to_return;
        }

        private int num_rules_single_switch (String switch_id)
        {
            String rules_exist_resource =
                "/wm/staticflowentrypusher/list/" + switch_id + "/json";
            String get_result = issue_get (rules_exist_resource);

            // all rule names have an underscore in them; if the
            // returned result has an underscore, it means that rules
            // exist.
            int last_index = 0;
            int rule_count = 0;
            String needle = "_";
            while(last_index != -1)
            {
                last_index = get_result.indexOf(needle,last_index);

                if( last_index != -1)
                {
                    ++rule_count;
                    last_index += needle.length();
                }
            }
            
            System.out.println(
                "Rule count: " + Integer.toString(rule_count));
            System.out.println(get_result);
            
            return rule_count;
        }
    }
    

    /**
       Performs the same operations as
       FloodlightRoutingTableToHardware, except every so often
       responds that could not push change to hardware
     */
    private static class ErrorProneFloodlightRoutingTableToHardware
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
