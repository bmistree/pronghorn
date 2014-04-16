package experiments;

import java.io.IOException;
import java.lang.Thread;
import java.util.concurrent.ConcurrentLinkedQueue;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.Endpoint;
import ralph.EndpointConstructorObj;
import ralph.Ralph;
import ralph.RalphGlobals;
import ralph.BoostedManager.DeadlockAvoidanceAlgorithm;


import pronghorn.FloodlightFlowTableToHardware;
import pronghorn.FTableUpdate;
import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.InstanceJava.Instance;

import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.MultiControllerOffOnJava.MultiControllerOffOn;
import experiments.Util;

/**
   Experiment, start a number of transactions on one controller, then,
   after those have gotten going, dump a bunch more transactions into
   the system on the other controller.

   The transactions that each controller performs should conflict, so
   that transactions can only run serially.  
 */

public class MultiControllerFairness
{
    public static final int USE_WOUND_WAIT_ARG_INDEX = 0;
    public static final int NUM_EXTERNAL_CALLS_ARG_INDEX = 1;
    public static final int OUTPUT_FILENAME_INDEX = 2;
    
    // wait this long for pronghorn to add all switches
    public static final int STARTUP_SETTLING_TIME_WAIT = 5000;

    // Ports to listen on and connect to for each side of the connection.
    private static final int TCP_LISTENING_PORT = 30955;
    private static final String HOST_NAME = "127.0.0.1";
    
    // pronghorn controllers on either side
    private static Instance side_a = null;
    private static MultiControllerOffOn off_on_app_a = null;
    private static Instance side_b = null;
    private static MultiControllerOffOn off_on_app_b = null;

    // exact numbers don't matter; they just need to be distinct
    private static final int TCP_SERVICE_REFERENCE_PORT_A = 39318;
    private static final int TCP_SERVICE_REFERENCE_PORT_B = 39319;
    
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 3)
        {
            FairnessUtil.print_usage();
            return;
        }

        boolean use_wound_wait =
            Boolean.parseBoolean(args[USE_WOUND_WAIT_ARG_INDEX]);
        
        System.out.println(
            "\nUsing wound wait: " + Boolean.toString(use_wound_wait) + "\n");

        // Each controller tries to dump this much work into system
        // when it starts.  (Note: one controller is given preference
        // and begins dumping first)
        int num_external_calls =
            Integer.parseInt(args[NUM_EXTERNAL_CALLS_ARG_INDEX]);
        
        String result_filename = args[OUTPUT_FILENAME_INDEX];

        // TCP connections require constructor objects.
        EndpointConstructor side_b_constructor =
            new EndpointConstructor(false,use_wound_wait);
        EndpointConstructor side_a_constructor =
            new EndpointConstructor(true,use_wound_wait);

        RalphGlobals a_globals = side_a_constructor.ralph_globals;
        RalphGlobals b_globals = side_b_constructor.ralph_globals;

        
        /* Start up pronghorn */

        // listen for connections to side b on port TCP_LISTENING_PORT
        Ralph.tcp_accept(
            side_b_constructor, HOST_NAME, TCP_LISTENING_PORT,
            b_globals);

        // wait for things to settle down
        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException _ex)
        {
            _ex.printStackTrace();
            assert(false);
        }

        // try to connect to other side
        try
        {
            side_a = (Instance)Ralph.tcp_connect(
                side_a_constructor, HOST_NAME, TCP_LISTENING_PORT,
                a_globals);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            assert(false);
        }

        // now that both sides are connected, connect shims to them to
        // connect to floodlight.
        SingleInstanceFloodlightShim shim_a = create_started_shim(side_a);
        SingleInstanceFloodlightShim shim_b = create_started_shim(side_b);

        /* wait a while to ensure that all switches are connected,etc. */
        try
        {
            Thread.sleep(STARTUP_SETTLING_TIME_WAIT);
        }
        catch (InterruptedException _ex)
        {
            _ex.printStackTrace();
            assert(false);
        }

        /*actually run all operations*/
        // in multi-controller case, know that will just perform
        // operations across all switches, regardless of what's passed
        // in, so just passing in dummy switch id.
        String dummy_switch_id = "";
        // This queue keeps track of all the work in the system
        ConcurrentLinkedQueue<String> tsafe_queue =
            new ConcurrentLinkedQueue<String>();

        FairnessUtil.run_operations(
            off_on_app_a,off_on_app_b,dummy_switch_id,num_external_calls,
            tsafe_queue);

        FairnessUtil.write_results(result_filename,tsafe_queue);
        
        // actually tell shims to stop.
        shim_a.stop();
        shim_b.stop();


        Util.force_shutdown();
    }

    /**
       Constructs side_b pronghorn instance in response to a tcp
       connection.
     */
    private static class EndpointConstructor implements EndpointConstructorObj
    {
        private boolean for_side_a;
        public final RalphGlobals ralph_globals;
        
        public EndpointConstructor(boolean for_side_a,boolean use_wound_wait)
        {
            this.for_side_a = for_side_a;

            if (use_wound_wait)
            {
                if (for_side_a)
                {
                    ralph_globals = new RalphGlobals(
                        DeadlockAvoidanceAlgorithm.WOUND_WAIT,
                        TCP_SERVICE_REFERENCE_PORT_A);
                }
                else
                {
                    ralph_globals = new RalphGlobals(
                        DeadlockAvoidanceAlgorithm.WOUND_WAIT,
                        TCP_SERVICE_REFERENCE_PORT_B);
                }
            }
            else
            {
                if (for_side_a)
                {
                    ralph_globals =
                        new RalphGlobals(TCP_SERVICE_REFERENCE_PORT_A);
                }
                else
                {
                    ralph_globals =
                        new RalphGlobals(TCP_SERVICE_REFERENCE_PORT_B);
                }
            }
        }
        
        @Override
        public Endpoint construct(
            RalphGlobals globals, RalphConnObj.ConnectionObj conn_obj)
        {
            //// DEBUG: Sanity check: globals passed in should be same
            //// as ralph_globals registered locally.
            if (globals != ralph_globals)
            {
                System.out.println(
                    "\nPassed in unexpected ralph globals when " +
                    "constructing.\n");
                assert(false);
            }
            //// END DEBUG
            
            Instance to_return = null;
            try
            {
                to_return = new Instance(globals,conn_obj);
                if (for_side_a)
                {
                    side_a = to_return;
                    off_on_app_a = new MultiControllerOffOn(
                        globals,new SingleSideConnection());
                    side_a.add_application(off_on_app_a);
                }
                else
                {
                    side_b = to_return;
                    off_on_app_b = new MultiControllerOffOn(
                        globals,new SingleSideConnection());
                    side_b.add_application(off_on_app_b);
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                assert(false);
            }
            return to_return;
        }
    }
    
    private static SingleInstanceFloodlightShim create_started_shim(
        Instance prong)
    {
        SingleInstanceFloodlightShim shim =
            new SingleInstanceFloodlightShim();
        SingleInstanceSwitchStatusHandler switch_status_handler =
            new SingleInstanceSwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                false);
        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        return shim;
    }
}
