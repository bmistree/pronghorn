package experiments;

import single_host.SingleHostRESTShim;
import single_host.SingleHostSwitchStatusHandler;
import single_host.JavaPronghornInstance.PronghornInstance;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import ralph.Endpoint;
import ralph.EndpointConstructorObj;
import pronghorn.FloodlightRoutingTableToHardware;
import java.lang.Thread;
import java.util.ArrayList;
import pronghorn.RTableUpdate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.io.*;
import ralph.Ralph;

/**
   Experiment, start a number of transactions on one controller, then,
   after those have gotten going, dump a bunch more transactions into
   the system on the other controller.

   The transactions that each controller performs should conflict, so
   that transactions can only run serially.  


   Insert artificial delay between messages on controller.  
   
   Usage:
       fairness <int> <int> <boolean> <string>
       
   <int> : The port that a floodlight controller is running on.
   <int> : The number of times to run experiment on controller.
   <boolean> : true if should allow internal reordering, false otherwise
   <string> : filename to save final result to.  true if rule exists.
   false otherwise.
 */

public class Fairness
{
    public static final int FLOODLIGHT_A_PORT_ARG_INDEX = 0;
    public static final int FLOODLIGHT_B_PORT_ARG_INDEX = 1;
    public static final int USE_WOUND_WAIT_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_INDEX = 3;
    
    // wait this long for pronghorn to add all switches
    public static final int STARTUP_SETTLING_TIME_WAIT = 5000;
    public static final int SETTLING_TIME_WAIT = 1000;

    private static final EndpointConstructor SIDE_B_CONSTRUCTOR =
        new EndpointConstructor(false);
    private static final EndpointConstructor SIDE_A_CONSTRUCTOR =
        new EndpointConstructor(true);

    private static final int TCP_LISTENING_PORT = 30955;
    private static final String HOST_NAME = "127.0.0.1";
    
    // pronghorn controllers on either side
    private static PronghornInstance side_a = null;
    private static PronghornInstance side_b = null;


    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 4)
        {
            assert(false);
            return;
        }
        
        int floodlight_port_a =
            Integer.parseInt(args[FLOODLIGHT_A_PORT_ARG_INDEX]);

        int floodlight_port_b =
            Integer.parseInt(args[FLOODLIGHT_B_PORT_ARG_INDEX]);

        boolean use_wound_wait =
            Boolean.parseBoolean(args[USE_WOUND_WAIT_ARG_INDEX]);

        String result_filename = args[OUTPUT_FILENAME_INDEX];


        /* Start up pronghorn */
        PronghornInstance prong_a = null;
        PronghornInstance prong_b = null;

        // listen for connections to side b on port TCP_LISTENING_PORT
        Ralph.tcp_accept(
            SIDE_B_CONSTRUCTOR, HOST_NAME, TCP_LISTENING_PORT);

        // wait for things to settle down
        try {        
            Thread.sleep(1000);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        // try to connect to other side
        try {
            side_a = (PronghornInstance)Ralph.tcp_connect(
                SIDE_A_CONSTRUCTOR, HOST_NAME, TCP_LISTENING_PORT);
        } catch (IOException e) {
            e.printStackTrace();
            assert(false);
        }

        // now that both sides are connected, connect shims to them to
        // connect to floodlight.
        SingleHostRESTShim shim_a =
            create_started_shim(side_a,floodlight_port_a);
        SingleHostRESTShim shim_b =
            create_started_shim(side_b,floodlight_port_b);

        
        /* wait a while to ensure that all switches are connected,etc. */
        try {
            Thread.sleep(STARTUP_SETTLING_TIME_WAIT);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        try{
            side_a.single_op_and_partner();
        } catch (Exception _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        
        System.out.println("\nGot into the middle of fairness\n");

        
        // actually tell shims to stop.
        shim_a.stop();
        shim_b.stop();
    }

    /**
       Constructs side_b pronghorn instance in response to a tcp
       connection.
     */
    private static class EndpointConstructor implements EndpointConstructorObj
    {
        private boolean for_side_a;
        public EndpointConstructor(boolean _for_side_a)
        {
            for_side_a = _for_side_a;
        }
        
        @Override
        public Endpoint construct(
            RalphGlobals globals, String host_uuid,
            RalphConnObj.ConnectionObj conn_obj)
        {
            PronghornInstance to_return = null;
            try {
                to_return = new PronghornInstance(globals,host_uuid,conn_obj);
                if (for_side_a)
                    side_a = to_return;
                else
                    side_b = to_return;
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
            return to_return;
        }
    }
    
    
    private static SingleHostRESTShim create_started_shim(
        PronghornInstance prong, int floodlight_port_to_connect_to)
    {
        SingleHostRESTShim shim =
            new SingleHostRESTShim(floodlight_port_to_connect_to);
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,shim,
                FloodlightRoutingTableToHardware.FLOODLIGHT_ROUTING_TABLE_TO_HARDWARE_FACTORY);
        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        return shim;
    }
}
