package experiments;

import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.PronghornConnectionSpeculationJava.PronghornConnectionSpeculation;
import experiments.MultiControllerSpeculationJava.MultiControllerSpeculation;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import pronghorn.FloodlightFlowTableToHardware;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
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



public class MultiControllerSpeculationThroughput
{
    public static final int CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX = 0;
    public static final int PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX = 1;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 2;
    public static final int SPECULATION_ON_ARG_INDEX = 3;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 4;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 5;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;

    private static Instance prong = null;
    private static MultiControllerSpeculation mc_speculation_app = null;
    private static GetNumberSwitches num_switches_app = null;    
    private static final RalphGlobals ralph_globals = new RalphGlobals();


    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 6)
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

        boolean should_speculate =
            Boolean.parseBoolean(args[SPECULATION_ON_ARG_INDEX]);
        
        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        int threads_per_switch = 1;

        /* Start up pronghorn */
        try {
            prong = new Instance(
                ralph_globals, new SingleSideConnection());

            mc_speculation_app = new MultiControllerSpeculation(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            
            prong.add_application(mc_speculation_app,Util.ROOT_APP_ID);
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
                should_speculate,collect_statistics_period_ms);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        

        // start listening for connections from parents
        Ralph.tcp_accept(
            new DummyConnectionConstructor(), "0.0.0.0", port_to_listen_on,ralph_globals);

        // now actually try to conect to children
        for (HostPortPair hpp : children_to_contact_hpp)
        {
            PronghornConnectionSpeculation connection = null;
            try {
                System.out.println("\nConnecting to " + hpp.host + "  " + hpp.port);
                connection = (PronghornConnectionSpeculation)Ralph.tcp_connect(
                    new DummyConnectionConstructor(), hpp.host, hpp.port,ralph_globals);

                connection.set_speculation_app(mc_speculation_app);
                mc_speculation_app.add_child_connection(connection);
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
        int counter = 0;
        for (String switch_id : switch_id_list)
        {
            SpeculationThroughputThread stt1 =
                new SpeculationThroughputThread(
                    counter, mc_speculation_app, num_ops_to_run, results);
            SpeculationThroughputThread stt2 =
                new SpeculationThroughputThread(
                    counter, mc_speculation_app, num_ops_to_run, results);
            ++counter;
            stt1.start();
            stt2.start();
            threads.add(stt1);
            threads.add(stt2);
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
        StringBuffer result_string = Util.produce_result_string(results);
        Util.write_results_to_file(output_filename,result_string.toString());

        // print to stdout the throughput of this experiments
        Util.print_throughput_results(
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

        // SPECULATION_ON_OFF_ARG_INDEX
        usage_string +=
            "\n\t<boolean>: true if should speculate; false if should not\n";
        
        // COLLECT_STATISTICS_ARG_INDEX
        usage_string +=
            "\n\t<int> : period for collecting individual switch stastics " +
            "in ms.  < 0 if should not collect any statistics\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
    
    public static class SpeculationThroughputThread extends Thread
    {
        private static final AtomicInteger atom_int = new AtomicInteger(0);
        
        int switch_num;
        int num_ops_to_run;
        private final MultiControllerSpeculation mc_speculation_app;
        ConcurrentHashMap<String,List<Long>> results;
        String result_id = null;
        
        public SpeculationThroughputThread(
            int switch_num, MultiControllerSpeculation mc_speculation_app, int num_ops_to_run,
            ConcurrentHashMap<String,List<Long>> results)
        {
            this.switch_num = switch_num;
            this.num_ops_to_run = num_ops_to_run;
            this.mc_speculation_app = mc_speculation_app;
            this.results = results;
            this.result_id = Integer.toString(atom_int.getAndIncrement());
    	}

    	public void run() {
            ArrayList<Long> completion_times = new ArrayList<Long>();
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                try {
                    mc_speculation_app.single_op(new Double(switch_num));
                } catch (Exception _ex) {
                    _ex.printStackTrace();
                    assert(false);
                }
                completion_times.add(System.nanoTime());
            }
            results.put(result_id,completion_times);
    	}
    }

    
    private static class DummyConnectionConstructor implements EndpointConstructorObj
    {
        @Override
        public Endpoint construct(
            RalphGlobals globals, RalphConnObj.ConnectionObj conn_obj)
        {
            PronghornConnectionSpeculation to_return = null;
            System.out.println("\nBuilt a connection\n\n");
            try {
                to_return = new PronghornConnectionSpeculation(ralph_globals,conn_obj);
                to_return.set_speculation_app(mc_speculation_app);
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
            return to_return;
        }
    }
}
