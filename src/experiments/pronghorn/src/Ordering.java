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
import pronghorn.RTableUpdate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;


/**
   Experiment.  Run one transaction that inserts a flow table entry on
   a switch, then run another transaction that removes it.  If third
   boolean argument passed into main is set to true, system returns
   before barrier is processed and randomly re-orders updates.
   Otherwise, performs normally.

   Usage:
       ordering <int> <boolean> <string>
       
   <int> : The number of times to run experiment on controller.
   <boolean> : true if should allow internal reordering, false otherwise
   <string> : filename to save final result to.  true if rule exists.
   false otherwise.
 */

public class Ordering
{
    private static final int NUMBER_TIMES_TO_RUN_ARG_INDEX = 0;
    private static final int ENSURE_ORDERING_ARG_INDEX = 1;
    private static final int RESULT_FILENAME_ARG_INDEX = 2;
    
    // wait this long for pronghorn to add all switches
    private static final int STARTUP_SETTLING_TIME_WAIT = 5000;
    private static final int SETTLING_TIME_WAIT = 1000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 3)
        {
            System.out.println("\nExpected 4 arguments.\n");
            print_usage();
            return;
        }

        int num_times_to_run = 
            Integer.parseInt(args[NUMBER_TIMES_TO_RUN_ARG_INDEX]);

        boolean ensure_ordering =
            Boolean.parseBoolean(args[ENSURE_ORDERING_ARG_INDEX]);
        
        String result_filename = args[RESULT_FILENAME_ARG_INDEX];

        System.out.println(
            "\nEnsure ordering " + Boolean.toString(ensure_ordering));
        
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

        boolean allow_reordering = ! ensure_ordering;
        OrderingShim shim = new OrderingShim(allow_reordering);
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                shim,prong,
                FloodlightRoutingTableToHardware.FLOODLIGHT_ROUTING_TABLE_TO_HARDWARE_FACTORY);
        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        

        /* wait a while to ensure that all switches are connected */
        try {
            Thread.sleep(STARTUP_SETTLING_TIME_WAIT);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        /* Discover the id of the first connected switch */
        String switch_id = null;
        try {
            NonAtomicInternalList<String,String> switch_list =
                prong.list_switch_ids();

            if (switch_list.get_len(null) == 0)
            {
                System.out.println(
                    "No switches attached to pronghorn: error");
                assert(false);
            }

            // get first switch id from key.  (If used Double(1), would get
            // second element from list)
            Double index_to_get_from = new Double(0);
            switch_id = switch_list.get_val_on_key(null,index_to_get_from);
        } catch (Exception _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        // get results from re-ordering
        List<Boolean> results = new ArrayList<Boolean>();
        for (int i =0; i < num_times_to_run; ++i)
        {
            boolean result = run_once(prong,switch_id,shim,allow_reordering);
            results.add(result);
        }
        
        // actually tell shim to stop.
        shim.stop();

        // write results to file
        write_results_to_file(result_filename,results);

        Util.force_shutdown();
    }


    private static void print_usage()
    {
        String usage_string = "";

        // NUMBER_TIMES_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // ENSURE_ORDERING_ARG_INDEX
        usage_string +=
            "\n\t<boolean>: true if should ensure ordering false otherwise.\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
    
    private static void write_results_to_file(String filename,List<Boolean> results)
    {
        StringBuffer string_buffer = new StringBuffer();
        // produce result string
        for (Boolean result : results)
        {
            if (result.booleanValue())
                string_buffer.append("1");
            else
                string_buffer.append("0");
            string_buffer.append(",");
        }

        Util.write_results_to_file(filename,string_buffer.toString());
    }
    


    /**
       Returns true if after running everything, switch does not have
       any routing table rules (ie, final operations applied in
       order).  False otherwise.
     */
    private static boolean run_once(
        PronghornInstance prong, String switch_id,
        OrderingShim shim,boolean allow_reordering)
    {
        // Run operation a random number of times: the below should
        // produce a random number from 2-10.
        int number_times_to_run = ((int)(Math.random()*9)) + 2;
        shim.set_num_outstanding_before_push(number_times_to_run);

        shim.force_clear();
        try {
            prong.logical_clear_switch_do_not_flush_clear_to_hardware();
        } catch (Exception _ex) {
            _ex.printStackTrace();
            assert(false);
        }
            
        // sleep to ensure that all those changes have gone through
        try {
            Thread.sleep(SETTLING_TIME_WAIT);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try{
            for (int i = 0; i < number_times_to_run; ++i)
                prong.single_op(switch_id);
        }
        catch (Exception _ex)
        {
            _ex.printStackTrace();
            assert(false);
        }

        // sleep to ensure that all those changes have gone through
        try {
            Thread.sleep(SETTLING_TIME_WAIT);
        } catch (Exception e) {
            e.printStackTrace();
        }


        boolean rules_exist = shim.rules_exist();
        // get rid of any rules that might be on switch
        shim.force_clear();

        boolean got_it_right = false;
        if ((number_times_to_run %2) == 0)
        {
            // if we ran an even number of instructions, no rules
            // should exist.
            if (!rules_exist)
                got_it_right = true;
        }
        else
        {
            // if we ran an odd number of instructions, a rule should
            // exist.
            if (rules_exist)
                got_it_right = true;
        }
        return got_it_right;
    }
    

    /**
       Creating a private subclass of shim that allows reordering 
     */
    private static class OrderingShim extends SingleHostFloodlightShim
    {
        private boolean allow_reordering;
        private int num_outstanding_before_push = -1;
        private HashMap<String,List<RTableUpdate>> outstanding_updates =
            new HashMap<String,List<RTableUpdate>>();

        /**
           @param _num_outstanding_before_push --- The number of
           operations to allow to buffer before we actually force
           pushing updates.
         */
        public OrderingShim(boolean _allow_reordering)
        {
            super();
            allow_reordering = _allow_reordering;
        }

        public void set_num_outstanding_before_push(
            int _num_outstanding_before_push)
        {
            num_outstanding_before_push = _num_outstanding_before_push;
        }
        

        /**
           Actually push command to clear routing table to all
           switches.  Definitely use a barrier here.
         */
        public void force_clear()
        {
            /// FIXME: Must reimplement
            System.out.println(
                "FIXME: Must reimplement force_clear in ordering test.");
            assert(false);
        }

        /**
           Note: should only call this after force_clear.
         */
        public void set_reordering(boolean to_set_to)
        {
            allow_reordering = to_set_to;
        }
        
        public boolean rules_exist()
        {
            /// FIXME: Must reimplement
            System.out.println(
                "FIXME: Must reimplement rules_exist in ordering test.");
            assert(false);
            return false;
        }
        
        
        @Override
        public boolean switch_rtable_updates(
            String switch_id,List<RTableUpdate> updates)
        {
            // if we are not allowing reordering, just call parent
            // method.
            if (! allow_reordering)
                return super.switch_rtable_updates(switch_id,updates);

            // if we are allowing reordering, then try reordering.
            List<RTableUpdate> switch_outstanding_updates =
                outstanding_updates.get(switch_id);
            if (switch_outstanding_updates == null)
            {
                switch_outstanding_updates = new ArrayList<RTableUpdate>();
                outstanding_updates.put(
                    switch_id,switch_outstanding_updates);
            }

            switch_outstanding_updates.addAll(updates);

            if (switch_outstanding_updates.size() >= num_outstanding_before_push)
            {
                // grab the first few commands to push to switch
                List<RTableUpdate> to_push =
                    switch_outstanding_updates.subList(
                        0,num_outstanding_before_push);
                
                //remove those commads from list of outstanding updates
                switch_outstanding_updates =
                    switch_outstanding_updates.subList(
                        num_outstanding_before_push,
                        switch_outstanding_updates.size());

                Collections.shuffle(to_push);
                super.switch_rtable_updates(switch_id,to_push);
            }
            // for time being, we're assuming that switch will apply
            // all updates.
            return true;
        }
    }
}
