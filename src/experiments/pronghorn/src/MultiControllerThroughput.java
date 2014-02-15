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
import java.io.*;
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



public class MultiControllerThroughput {
	
    public static final int FLOODLIGHT_PORT_CSV_ARG_INDEX = 0;
    public static final int CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX = 1;
    public static final int PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX = 2;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 3;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 4;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;

    private static PronghornInstance prong = null;
    

    private static final RalphGlobals ralph_globals = new RalphGlobals();
    
    
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
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        int threads_per_switch = 1;

        
        /* Start up pronghorn */
        try {
            prong = new PronghornInstance(
                ralph_globals,"", new SingleSideConnection());
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
            new DummyConnectionConstructor(), "0.0.0.0", port_to_listen_on,ralph_globals);

        // now actually try to conect to children
        for (HostPortPair hpp : children_to_contact_hpp)
        {
            PronghornConnection connection = null;
            try {
                System.out.println("\nConnecting to " + hpp.host + "  " + hpp.port);
                connection = (PronghornConnection)Ralph.tcp_connect(
                    new DummyConnectionConstructor(), hpp.host, hpp.port,ralph_globals);
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


        NonAtomicInternalList<String,String> switch_list = null;
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
        } catch (Exception _ex) {
            _ex.printStackTrace();
            assert(false);
        }


        /* warmup */
        if (num_ops_to_run != 0) {
            ArrayList<Thread> threads = new ArrayList<Thread>();
            // each thread has a unique index into this results map
            ConcurrentHashMap<String,List<Long>> results =
                new ConcurrentHashMap<String,List<Long>>();

            for (int i = 0; i < num_switches; i++) {
                String switch_id = null;
                try { 
                    switch_id = switch_list.get_val_on_key(null, new Double((double)i));
                } catch (Exception _ex) {
                    _ex.printStackTrace();
                    assert(false);
                }

                ThroughputThread t =
                    new ThroughputThread(
                        switch_id, prong, num_ops_to_run, results);
            
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
            System.out.println("warmed up, sleep");
            try {
                Thread.sleep(5000);
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
        }

        
        /* Spawn thread per switch to operate on it */
        ArrayList<Thread> threads = new ArrayList<Thread>();
        // each thread has a unique index into this results map
        ConcurrentHashMap<String,List<Long>> results =
            new ConcurrentHashMap<String,List<Long>>();

        long start = System.nanoTime();
        for (int i = 0; i < num_switches; i++) {
            String switch_id = null;
            try { 
                switch_id = switch_list.get_val_on_key(null, new Double((double)i));
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }

            ThroughputThread t =
                new ThroughputThread(
                    switch_id, prong, num_ops_to_run, results);
            
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

        Writer w;
        try {
            w = new PrintWriter(new FileWriter(output_filename));

            // TODO maybe use a csv library...
            for (String switch_id : results.keySet()) {
                List<Long> times = results.get(switch_id);
                String line = "";
                for (Long time : times)
                    line += time.toString() + ",";
                if (line != "") {
                    // trim off trailing comma
                    line = line.substring(0, line.length() - 1);
                }
                w.write(line);
                w.write("\n");
            }
            w.close();
        } catch (IOException e) {
            e.printStackTrace();
            assert(false);
        }

        double throughputPerS =
            ((double) (num_switches * threads_per_switch * num_ops_to_run)) /
            ((double)elapsedNano/1000000000);
        System.out.println(
            "Switches: " + num_switches + " Throughput(op/s): " +
            throughputPerS);

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
        for (SingleHostRESTShim shim : shim_set)
            shim.stop();
    }

    public static void print_usage()
    {
        String usage_string = "";

        // FLOODLIGHT_PORT_ARG_INDEX 
        usage_string += "\n\t<int>: floodlight port to connect to\n";

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

        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
    
    public static class ThroughputThread extends Thread {

        private static final AtomicInteger atom_int = new AtomicInteger(0);
        
        String switch_id;
        int num_ops_to_run;
        PronghornInstance prong;
        ConcurrentHashMap<String,List<Long>> results;
        String result_id = null;
        
        public ThroughputThread(
            String switch_id, PronghornInstance prong, int num_ops_to_run,
            ConcurrentHashMap<String,List<Long>> results)
        {
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.prong = prong;
            this.results = results;
            this.result_id = switch_id + atom_int.getAndIncrement();
    	}

    	public void run() {
            ArrayList<Long> completion_times = new ArrayList<Long>();
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                try {
                    prong.single_op_and_ask_children_for_single_op_switch_id(switch_id);
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
            RalphGlobals globals, String host_uuid,
            RalphConnObj.ConnectionObj conn_obj)
        {
            PronghornConnection to_return = null;
            System.out.println("\nBuilt a connection\n\n");
            try {
                to_return = new PronghornConnection(ralph_globals,host_uuid,conn_obj);
                to_return.set_service(prong);
                // prong.add_child_connection(to_return);
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
            return to_return;
        }
    }
    
}
