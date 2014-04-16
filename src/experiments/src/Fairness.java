package experiments;

import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import experiments.OffOnApplicationJava.OffOnApplication;
import experiments.IOffOnApplicationJava.IOffOnApplication;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.MultiControllerOffOnJava.MultiControllerOffOn;


import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import ralph.Endpoint;
import ralph.EndpointConstructorObj;
import pronghorn.FloodlightFlowTableToHardware;
import java.lang.Thread;
import java.util.ArrayList;
import pronghorn.FTableUpdate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import ralph.Ralph;
import ralph.RalphGlobals;
import ralph.BoostedManager.DeadlockAvoidanceAlgorithm;

import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;

/**
   Experiment, start a number of transactions on one controller, then,
   after those have gotten going, dump a bunch more transactions into
   the system on the other controller.

   The transactions that each controller performs should conflict, so
   that transactions can only run serially.  
 */

public class Fairness
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

    // Each controller tries to dump this much work into system when
    // it starts.  (Note: one controller is given preference and
    // begins dumping first)
    static int NUM_EXTERNAL_CALLS = 1000;
    
    // how many ms to wait before requesting b to dump its tasks after a has
    // started its tasks
    final static int A_HEADSTART_TIME_MS = 8;

    // These identifiers are associated with each endpoint and appear (in order)
    // in the threadsafe queue.
    final static String ENDPOINT_A_IDENTIFIER = "1";
    final static String ENDPOINT_B_IDENTIFIER = "0";

    // extra debugging flag: something for us to watch out for in case we had an
    // exception.
    final static AtomicBoolean had_exception = new AtomicBoolean(false);
    // This queue keeps track of all the work in the system
    final static ConcurrentLinkedQueue<String> tsafe_queue =
        new ConcurrentLinkedQueue<String>();

    // exact numbers don't matter; they just need to be distinct
    private static final int TCP_SERVICE_REFERENCE_PORT_A = 39318;
    private static final int TCP_SERVICE_REFERENCE_PORT_B = 39319;
    
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 3)
        {
            print_usage();
            return;
        }

        boolean use_wound_wait =
            Boolean.parseBoolean(args[USE_WOUND_WAIT_ARG_INDEX]);
        
        System.out.println(
            "\nUsing wound wait: " + Boolean.toString(use_wound_wait) + "\n");
        
        NUM_EXTERNAL_CALLS =
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
        run_operations(off_on_app_a,off_on_app_b,dummy_switch_id);

        write_results(result_filename);
        
        // actually tell shims to stop.
        shim_a.stop();
        shim_b.stop();


        Util.force_shutdown();
    }


    private static void write_results(String result_filename)
    {
        String to_write = "";
        for (String endpoint_id : tsafe_queue)
            to_write += endpoint_id + ",";

        Util.write_results_to_file(result_filename,to_write);
    }

    private static void print_usage()
    {
        String usage_string = "";

        // NUMBER_THREADS_ARG_INDEX
        usage_string +=
            "\n\t<boolean>: true if should run using wound-wait; " +
            "false if should use ralph scheduler.\n";
        
        // NUMBER_OPS_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
    
    /**
       Dump NUM_EXTERNAL_CALLS operations into system on side_a.  Pause to
       ensure that side_a registers all of them.  Then dump NUM_EXTERNAL_CALLS
       onto side_b.
     */
    public static void run_operations(
        MultiControllerOffOn app_a, MultiControllerOffOn app_b,String switch_id)
    {
        EndpointTask task_a = new EndpointTask(app_a,ENDPOINT_A_IDENTIFIER,switch_id);
        EndpointTask task_b = new EndpointTask(app_b,ENDPOINT_B_IDENTIFIER,switch_id);

        ExecutorService executor_a = create_executor();
        ExecutorService executor_b = create_executor();
        
        // put a bunch of tasks rooted at A into system.
        for (int i = 0; i < NUM_EXTERNAL_CALLS; ++i)
            executor_a.execute(task_a);

        // give those tasks a head start to get started before b can start its
        // tasks
        try {
            Thread.sleep(A_HEADSTART_TIME_MS);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            had_exception.set(true);
            assert(false);
            return;
        }
        
        // put a bunch of tasks rooted at B into system
        for (int i = 0; i < NUM_EXTERNAL_CALLS; ++i)
            executor_b.execute(task_b);
        
        // join on executor services
        executor_a.shutdown();
        executor_b.shutdown();
        while (!(executor_a.isTerminated() && executor_b.isTerminated()))
        {
            try {
                Thread.sleep(50);
            } catch (InterruptedException _ex) {
                _ex.printStackTrace();
                had_exception.set(true);
                assert(false);
                break;
            }
        }
    }

    
    private static class EndpointTask implements Runnable
    {
        private final IOffOnApplication app;
        private final String principal_id;
        private final String switch_id;
        
        public EndpointTask(
            IOffOnApplication _app, String _principal_id, String _switch_id)
        {
            app = _app;
            principal_id = _principal_id;
            switch_id = _switch_id;
        }

        public void run ()
        {
            try
            {
                app.single_op(switch_id);
                tsafe_queue.add(principal_id + "|" + System.nanoTime());
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                had_exception.set(true);
            }
        }
    }
        

    public static ExecutorService create_executor()
    {
        ExecutorService executor = Executors.newCachedThreadPool(
            new ThreadFactory()
            {
                // each thread created is a daemon
                public Thread newThread(Runnable r)
                {
                    Thread t=new Thread(r);
                    t.setDaemon(true);
                    return t;
                }
            });
        return executor;
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
