package experiments;

import single_host.SingleHostRESTShim;
import single_host.SingleHostSwitchStatusHandler;
import single_host.JavaPronghornInstance.PronghornInstance;
import single_host.JavaPronghornConnection.PronghornConnection;
import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import pronghorn.FloodlightRoutingTableToHardware;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;

import ralph.RalphGlobals;
import ralph.EndpointConstructorObj;
import ralph.Endpoint;
import ralph.Ralph;


public class MultiControllerLatency
{
    public static final int FLOODLIGHT_PORT_CSV_ARG_INDEX = 0;
    public static final int CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX = 1;
    public static final int PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX = 2;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 3;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 4;

    public static PronghornInstance prong = null;
    
    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;

    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 5)
        {
            print_usage();
            return;
        }
        
        Set<Integer> floodlight_port_set = Util.parse_csv_ports(
            args[FLOODLIGHT_PORT_CSV_ARG_INDEX]);

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

        int num_threads = 1;


        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        
        /* Start up pronghorn */
        try {
            prong = new PronghornInstance(
                new RalphGlobals(),
                "", new SingleSideConnection());
        } catch (Exception _ex) {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        Set<SingleHostRESTShim> shim_set = new HashSet<SingleHostRESTShim>();
        for (Integer port : floodlight_port_set)
            shim_set.add ( new SingleHostRESTShim(port.intValue()));
        
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,
                FloodlightRoutingTableToHardware.FLOODLIGHT_ROUTING_TABLE_TO_HARDWARE_FACTORY);

        for (SingleHostRESTShim shim : shim_set)
        {
            shim.subscribe_switch_status_handler(switch_status_handler);
            shim.start();
        }

        // start listening for connections from parents
        Ralph.tcp_accept(
            new DummyConnectionConstructor(), "0.0.0.0", port_to_listen_on);
        

        // now actually try to conect to parent
        for (HostPortPair hpp : children_to_contact_hpp)
        {
            PronghornConnection connection = null;
            try {
                System.out.println("\nConnecting to " + hpp.host + "  " + hpp.port);
                connection = (PronghornConnection)Ralph.tcp_connect(
                    new DummyConnectionConstructor(), hpp.host, hpp.port);
                connection.set_service(prong);
                prong.add_child_connection(connection);
            } catch(Exception e) {
                e.printStackTrace();
                assert(false);
            }
        }

        
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


        if (num_ops_to_run != 0)
        {
            List<LatencyThread> all_threads = new ArrayList<LatencyThread>();
            for (int i = 0; i < num_threads; ++i)
                all_threads.add(new LatencyThread(prong,switch_id,num_ops_to_run));

            for (LatencyThread lt : all_threads)
                lt.start();

            // wait for all threads to finish and collect their results
            for (LatencyThread lt : all_threads)
            {
                try {
                    lt.join();
                } catch (Exception _ex) {
                    _ex.printStackTrace();
                    assert(false);
                    return;
                }
            }

            // print csv list of runtimes to file
            Writer w;
            try {
                w = new PrintWriter(new FileWriter(output_filename));
                for (LatencyThread lt : all_threads)
                {
                    lt.write_times(w);
                    w.write("\n");
                }
                w.close();
            } catch (IOException e) {
                e.printStackTrace();
                assert(false);
            }
        }

        while (true)
        {
            try{
                Thread.sleep(1000);
            } catch (InterruptedException _ex) {
                _ex.printStackTrace();
                break;
            }
        }

        // actually tell shims to stop.
        for (SingleHostRESTShim shim : shim_set)
            shim.stop();
    }


    public static void print_usage()
    {
        System.out.println(
            "\nSingleHost <int: floodlight port number> " + 
            "<int: num ops to run> <output_filename>\n");
    }


    private static class DummyConnectionConstructor implements EndpointConstructorObj
    {
        @Override
        public Endpoint construct(
            RalphGlobals globals, String host_uuid,
            RalphConnObj.ConnectionObj conn_obj)
        {
            PronghornConnection to_return = null;
            System.out.println("\nBuilt a connection\n\n");
            try {
                to_return = new PronghornConnection(globals,host_uuid,conn_obj);
                to_return.set_service(prong);
                // prong.add_child_connection(to_return);
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
            return to_return;
        }
    }


    private static class LatencyThread extends Thread
    {
        public List <Long> all_times = new ArrayList<Long>();

        
        private PronghornInstance prong = null;
        private String switch_id = null;
        private int num_ops_to_run = -1;
        
        public LatencyThread(
            PronghornInstance prong, String switch_id, int num_ops_to_run)
        {
            this.prong = prong;
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
        }

        public void run()
        {
            /* perform all operations and determine how long they take */
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                long start_time = System.nanoTime();
                try {
                    System.out.println("\nRunning\n");
                    prong.single_op_and_ask_children_for_single_op();
                } catch (Exception _ex) {
                    _ex.printStackTrace();
                    assert(false);
                }
                long total_time = System.nanoTime() - start_time;
                all_times.add(total_time);
            }
        }
        
        /**
           Write the latencies that each operation took as a csv
         */
        public void write_times(Writer writer) throws IOException
        {
            for (Long latency : all_times)
                writer.write(latency.toString() + ",");
        }
    }
}
