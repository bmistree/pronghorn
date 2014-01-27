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

public class SingleControllerError {
	
    public static final int FLOODLIGHT_PORT_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 1;
    public static final int FAILURE_PROB_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 3;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;

    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 4)
        {
            System.out.println("\nExpected 4 arguments: exiting\n");
            return;
        }
        
        int floodlight_port =
            Integer.parseInt(args[FLOODLIGHT_PORT_ARG_INDEX]);
        int num_ops_to_run = 
                Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

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
        SingleHostRESTShim shim = new  SingleHostRESTShim(floodlight_port);

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

        String switch_id = switch_id_list.get(0);
        for (int i = 0; i < num_ops_to_run; ++i)
        {
            try {
                prong.single_op(switch_id);
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
        }
        
        shim.stop();
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
