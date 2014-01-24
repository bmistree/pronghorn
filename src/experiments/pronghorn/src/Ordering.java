package experiments;

import single_host.SingleHostRESTShim;
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

/**
   Experiment.  Run one transaction that inserts a flow table entry on
   a switch, then run another transaction that removes it.  If third
   boolean argument passed into main is set to true, system returns
   before barrier is processed and randomly re-orders updates.
   Otherwise, performs normally.

   Usage:
       ordering <int> <int> <boolean> <string>
       
   <int> : The port that a floodlight controller is running on.
   <int> : The number of times to run experiment on controller.
   <boolean> : true if should allow internal reordering, false otherwise
   <string> : filename to save final result to.  true if rule exists.
   false otherwise.
 */

public class Ordering
{
    public static final int FLOODLIGHT_PORT_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 1;
    public static final int ENSURE_ORDERING_ARG_INDEX = 2;
    public static final int RESULT_FILENAME_INDEX = 3;
    
    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 4)
        {
            assert(false);
            return;
        }
        
        int floodlight_port =
            Integer.parseInt(args[FLOODLIGHT_PORT_ARG_INDEX]);

        int num_ops_to_run = 
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        boolean ensure_ordering =
            Boolean.parseBoolean(args[ENSURE_ORDERING_ARG_INDEX]);

        String result_filename = args[RESULT_FILENAME_INDEX];


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

        OrderingRESTShim shim = new OrderingRESTShim(floodlight_port,false,0);
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,shim,
                FloodlightRoutingTableToHardware.FLOODLIGHT_ROUTING_TABLE_TO_HARDWARE_FACTORY);
        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        

        /* wait a while to ensure that all switches are connected */
        try {
            Thread.sleep(SETTLING_TIME_WAIT);
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

        /* perform all operations and determine how long they take */
        for (int i = 0; i < num_ops_to_run; ++i)
        {
            try {
                prong.single_op(switch_id);
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
        }
        // actually tell shim to stop.
        shim.stop();
    }


    /**
       Creating a private subclass of shim that allows reordering 
     */
    private static class OrderingRESTShim extends SingleHostRESTShim
    {
        private boolean allow_reordering;
        private int num_outstanding_before_push;
        private HashMap<String,List<RTableUpdate>> outstanding_updates =
            new HashMap<String,List<RTableUpdate>>();

        /**
           @param _num_outstanding_before_push --- The number of
           operations to allow to buffer before we actually force
           pushing updates.
         */
        public OrderingRESTShim(
            int _floodlight_port, boolean _allow_reordering,
            int _num_outstanding_before_push)
        {
            super(_floodlight_port);
            allow_reordering = _allow_reordering;
            num_outstanding_before_push = _num_outstanding_before_push;
        }

        /**
           Actually push command to clear routing table to all
           switches.  Definitely use a barrier here.
         */
        public void force_clear()
        {
            // still a stub method
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
            // we're assuming that only a single switch is connected.
            // Get that single switch's id.
            String switch_id = null;
            for (String some_switch_id : switch_id_set)
            {
                switch_id = some_switch_id;
                break;
            }
            if (switch_id == null)
                assert(false);

            String rules_exist_resource =
                "/wm/staticflowentrypusher/list/" + switch_id + "/json";

            String get_result = issue_get (rules_exist_resource);

            System.out.println("\n" + get_result + "\n");
            return true;
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

            if (switch_outstanding_updates.size() > num_outstanding_before_push)
            {
                // grab the first few commands to push to switch
                List<RTableUpdate> to_push =
                    switch_outstanding_updates.subList(
                        0,num_outstanding_before_push);
                
                //remove those commads from list of outstanding updates
                switch_outstanding_updates =
                    switch_outstanding_updates.subList(
                        num_outstanding_before_push,
                        switch_outstanding_updates.size() -1);

                Collections.shuffle(to_push);
                super.switch_rtable_updates(switch_id,to_push);
            }
            // for time being, we're assuming that switch will apply
            // all updates.
            return true;
        }
    }
}
