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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // TCP connections require constructor objects.
    private static final EndpointConstructor SIDE_B_CONSTRUCTOR =
        new EndpointConstructor(false);
    private static final EndpointConstructor SIDE_A_CONSTRUCTOR =
        new EndpointConstructor(true);

    // Ports to listen on and connect to for each side of the connection.
    private static final int TCP_LISTENING_PORT = 30955;
    private static final String HOST_NAME = "127.0.0.1";
    
    // pronghorn controllers on either side
    private static PronghornInstance side_a = null;
    private static PronghornInstance side_b = null;

    // Each controller tries to dump this much work into system when
    // it starts.  (Note: one controller is given preference and
    // begins dumping first)
    final static int NUM_THREADS_EACH_SIDE = 100;
    final static int NUM_EXTERNAL_CALLS = 100;
    // how many ms to wait before requesting b to dump its tasks after a has
    // started its tasks
    final static int A_HEADSTART_TIME_MS = 2;

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

        // actually run all operations 
        run_operations(side_a,side_b);
        
        // actually tell shims to stop.
        shim_a.stop();
        shim_b.stop();

        write_results(result_filename);
    }


    private static void write_results(String result_filename)
    {
        String to_write = "";
        for (String endpoint_id : tsafe_queue)
            to_write += endpoint_id + ",";
        
        // write result string to file
        Writer w;
        try {
            w = new PrintWriter(new FileWriter(result_filename));
            w.write(to_write);
            w.write("\n");
            w.close();
        } catch (IOException e) {
            e.printStackTrace();
            assert(false);
        }
    }
    
    /**
       Dump NUM_EXTERNAL_CALLS operations into system on side_a.  Pause to
       ensure that side_a registers all of them.  Then dump NUM_EXTERNAL_CALLS
       onto side_b.
     */
    public static void run_operations(PronghornInstance side_a, PronghornInstance side_b)
    {
        EndpointTask task_a = new EndpointTask(side_a,ENDPOINT_A_IDENTIFIER);
        EndpointTask task_b = new EndpointTask(side_b,ENDPOINT_B_IDENTIFIER);

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
        private PronghornInstance endpt = null;
        private String endpoint_id = null;

        public EndpointTask(PronghornInstance _endpt, String _endpoint_id)
        {
            endpt = _endpt;
            endpoint_id = _endpoint_id;
        }

        public void run ()
        {
            try {
                endpt.single_op_and_partner();
                tsafe_queue.add(endpoint_id);
            } catch(Exception ex) {
                ex.printStackTrace();
                had_exception.set(true);
            }
        }
    }
        

    public static ExecutorService create_executor()
    {
        ExecutorService executor = Executors.newFixedThreadPool(
            NUM_THREADS_EACH_SIDE,
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
