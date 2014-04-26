package experiments;

import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.PronghornConnectionJava.PronghornConnection;
import experiments.MultiControllerOffOnJava.MultiControllerOffOn;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import pronghorn.FloodlightFlowTableToHardware;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;

import ralph.RalphGlobals;
import ralph.EndpointConstructorObj;
import ralph.Endpoint;
import ralph.Ralph;



public class MultiControllerThroughput
{
    public static final int CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX = 0;
    public static final int PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX = 1;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 2;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 3;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 4;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;

    private static Instance prong = null;
    private static MultiControllerOffOn mc_off_on_app = null;
    private static GetNumberSwitches num_switches_app = null;    
    private static final RalphGlobals ralph_globals = new RalphGlobals();

    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 5)
        {
            print_usage();
            return;
        }

        Set<HostPortPair> children_to_contact_hpp = null;
        if (! args[CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX].equals("-1"))
            children_to_contact_hpp = Util.parse_csv_host_port_pairs(
                args[CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX]);
        else
            children_to_contact_hpp = new HashSet<HostPortPair>();

        int port_to_listen_on =
            Integer.parseInt(args[PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX]);

        int num_ops_to_run = 
                Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        int threads_per_switch = 1;

        /* Start up pronghorn */
        try {
            prong = new Instance(
                ralph_globals, new SingleSideConnection());

            mc_off_on_app = new MultiControllerOffOn(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            
            prong.add_application(mc_off_on_app,Util.ROOT_APP_ID);
            prong.add_application(num_switches_app,Util.ROOT_APP_ID);
        } catch (Exception _ex) {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }


        SingleInstanceFloodlightShim shim = new SingleInstanceFloodlightShim();
        SingleInstanceSwitchStatusHandler switch_status_handler =
            new SingleInstanceSwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                true,collect_statistics_period_ms);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        

        // start listening for connections from parents
        Ralph.tcp_accept(
            new DummyConnectionConstructor(), "0.0.0.0", port_to_listen_on,ralph_globals);

        // now actually try to conect to children
        for (HostPortPair hpp : children_to_contact_hpp)
        {
            PronghornConnection connection = null;
            try {
                System.out.println("\nConnecting to " + hpp.host + "  " + hpp.port);
                connection = (PronghornConnection)Ralph.tcp_connect(
                    new DummyConnectionConstructor(), hpp.host, hpp.port,ralph_globals);

                connection.set_off_on_app(mc_off_on_app);
                mc_off_on_app.add_child_connection(connection);
            } catch(Exception e) {
                e.printStackTrace();
                assert(false);
            }
        }

        /* wait a while to ensure that all switches are connected */
        Util.wait_on_switches(num_switches_app);

        /* Spawn thread per switch to operate on it */
        ArrayList<Thread> threads = new ArrayList<Thread>();
        // each thread has a unique index into this results map
        ConcurrentHashMap<String,List<Long>> results =
            new ConcurrentHashMap<String,List<Long>>();

        // list of all switches in the system
        List<String> switch_id_list =
            Util.get_switch_id_list (num_switches_app);
        int num_switches = switch_id_list.size();
        
        // generate throughput tasks for each switch
        long start = System.nanoTime();
        for (String switch_id : switch_id_list)
        {
            ThroughputThread t =
                new ThroughputThread(
                    switch_id, mc_off_on_app, num_ops_to_run, results);
            
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
        }
        long end = System.nanoTime();
        long elapsedNano = end-start;

        
        // produce results string
        StringBuffer result_string = produce_result_string(results);
        Util.write_results_to_file(output_filename,result_string.toString());

        // print to stdout the throughput of this experiments
        print_throughput_results(
            num_switches,threads_per_switch,num_ops_to_run,elapsedNano);

        // run indefinitely
        while (true)
        {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException _ex) {
                _ex.printStackTrace();
                break;
            }
        }
        
        // actually tell shims to stop.
        shim.stop();
        Util.force_shutdown();        
    }

    public static void print_usage()
    {
        String usage_string = "";

        // CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX 
        usage_string +=
            "\n\t<csv>: Children to contact host port csv.  Pronghorn ";
        usage_string += "controllers to connect to.  ";
        usage_string += "Format host:port,host:port\n";

        // PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX
        usage_string +=
            "\n\t<int>: Port to listen for connections on.\n";

        // NUMBER_OPS_TO_RUN_PER_EXPERIMENT_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // COLLECT_STATISTICS_ARG_INDEX
        usage_string +=
            "\n\t<int> : period for collecting individual switch stastics " +
            "in ms.  < 0 if should not collect any statistics\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
    
    public static class ThroughputThread extends Thread {

        private static final AtomicInteger atom_int = new AtomicInteger(0);
        
        String switch_id;
        int num_ops_to_run;
        private final MultiControllerOffOn mc_off_on_app;
        ConcurrentHashMap<String,List<Long>> results;
        String result_id = null;
        
        public ThroughputThread(
            String switch_id, MultiControllerOffOn mc_off_on_app, int num_ops_to_run,
            ConcurrentHashMap<String,List<Long>> results)
        {
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.mc_off_on_app = mc_off_on_app;
            this.results = results;
            this.result_id = switch_id + atom_int.getAndIncrement();
    	}

    	public void run() {
            ArrayList<Long> completion_times = new ArrayList<Long>();
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                try {
                    mc_off_on_app.single_op_and_ask_children_for_single_op_switch_id(switch_id);
                } catch (Exception _ex) {
                    _ex.printStackTrace();
                    assert(false);
                }
                completion_times.add(System.nanoTime());
            }
            results.put(result_id,completion_times);
    	}
    }


    private static StringBuffer produce_result_string(
        ConcurrentHashMap<String,List<Long>> results)
    {
        StringBuffer result_string = new StringBuffer();
        for (String switch_id : results.keySet())
        {
            List<Long> times = results.get(switch_id);
            String line = "";
            for (Long time : times)
                line += time.toString() + ",";
            if (line != "")
            {
                // trim off trailing comma
                line = line.substring(0, line.length() - 1);
            }
            result_string.append(line).append("\n");
        }
        return result_string;
    }

    private static void print_throughput_results(
        int num_switches,int threads_per_switch,int num_ops_to_run,
        long elapsedNano)
    {
        double throughputPerS =
            ((double) (num_switches * threads_per_switch * num_ops_to_run)) /
            ((double)elapsedNano/1000000000);
        System.out.println(
            "Switches: " + num_switches + " Throughput(op/s): " +
            throughputPerS);
    }

    
    private static class DummyConnectionConstructor implements EndpointConstructorObj
    {
        @Override
        public Endpoint construct(
            RalphGlobals globals, RalphConnObj.ConnectionObj conn_obj)
        {
            PronghornConnection to_return = null;
            System.out.println("\nBuilt a connection\n\n");
            try {
                to_return = new PronghornConnection(ralph_globals,conn_obj);
                to_return.set_off_on_app(mc_off_on_app);
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
            return to_return;
        }
    }
}
