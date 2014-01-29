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
import RalphDataWrappers.ListTypeDataWrapper;
import pronghorn.RTable._InternalRoutingTableEntry;
import pronghorn.ShimInterface;
import pronghorn.RoutingTableToHardwareFactory;
import pronghorn.RoutingTableToHardware;
import java.util.HashMap;
import java.util.Map.Entry;

import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;

import experiments.SingleControllerError.ErrorShim;
import experiments.SingleControllerError.ErrorProneFloodlightRoutingTableToHardware;

import ralph.RalphGlobals;
import ralph.EndpointConstructorObj;
import ralph.Endpoint;
import ralph.Ralph;


public class MultiControllerError {
	
    public static final int FLOODLIGHT_PORT_ARG_INDEX = 0;

    public static final int CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX = 1;
    public static final int PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX = 2;
    public static final int NUMBER_OPS_TO_RUN_PER_EXPERIMENT_ARG_INDEX = 3;
    public static final int FAILURE_PROB_ARG_INDEX = 4;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 5;


    // wait this long for pronghorn to add all switches
    private static final int SETTLING_TIME_WAIT = 5000;
    private static final int AFTER_FORCE_CLEAR_WAIT_TIME = 1000;

    private static final RalphGlobals ralph_globals = new RalphGlobals();
    private static PronghornInstance prong = null;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 6)
        {
            System.out.println("\nExpected 6 arguments: exiting\n");
            return;
        }

        Set<Integer> port_set =
            Util.parse_csv_ports(args[FLOODLIGHT_PORT_ARG_INDEX]);

        int floodlight_port = -1;
        for (Integer port : port_set)
            floodlight_port = port.intValue();


        Set<HostPortPair> children_to_contact_hpp = null;
        if (! args[CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX].equals("-1"))
            children_to_contact_hpp = Util.parse_csv_host_port_pairs(
                args[CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX]);
        else
            children_to_contact_hpp = new HashSet<HostPortPair>();

        int port_to_listen_on =
            Integer.parseInt(args[PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX]);
        
        int num_ops_to_run_per_experiment = 
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_PER_EXPERIMENT_ARG_INDEX]);

        System.out.println(
            "\nNumber ops to run per experiment: " +
            Integer.toString(num_ops_to_run_per_experiment));
        

        // how frequently each switch should independently fail
        double failure_prob =
            Double.parseDouble(args[FAILURE_PROB_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        /* Start up pronghorn */
        try {
            prong = new PronghornInstance(
                ralph_globals,"", new SingleSideConnection());
        } catch (Exception _ex) {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }


        ErrorShim shim = new ErrorShim(floodlight_port);
        ErrorProneFloodlightRoutingTableToHardware.ErrorProneFactory routing_table_to_hardware_factory
            = new ErrorProneFloodlightRoutingTableToHardware.ErrorProneFactory(failure_prob);
        
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,
                routing_table_to_hardware_factory);
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

        List<Boolean> run_results = new ArrayList<Boolean>();
            
        String a_b_ip = "18.18.";
        try {
            for (int i = 0; i < num_ops_to_run_per_experiment; ++i)
            {
                int c = (int) (i/256);
                int d = i % 256;
                String ip_addr =
                    a_b_ip + Integer.toString(c) + "." + Integer.toString(d);
                prong.insert_entry_on_all_switches_and_partners(ip_addr);

            }
        } catch (Exception _ex) {
            _ex.printStackTrace();
            System.out.println(
                "There was an unknown error.  Should deal with it.");
            assert(false);
        }


        if (num_ops_to_run_per_experiment != 0)
        {
            // ensure that each switch attached to local controller
            // has correct number of flow table entries.
            HashMap<String,Integer> rules_in_system = shim.num_rules_in_system();
            // first check for agreement betweeen number of flow table
            // entries for each switch.
            int num_rules_in_system = -1;
            boolean result = true;
            for (Entry<String,Integer> pairs : rules_in_system.entrySet())
            {
                int rules_on_switch = pairs.getValue().intValue();
                if (num_rules_in_system == -1)
                    num_rules_in_system = rules_on_switch;

                if (num_rules_in_system != rules_on_switch)
                    result = false;
            }

            // second, check that number of flow table entries on each
            // switch agrees with number we'd expect from having run
            // num_ops_to_run_per_experiment for each test.
            if (num_rules_in_system != num_ops_to_run_per_experiment)
                result = false;

            run_results.add(result);

            System.out.println("\nFinished experiment\n");
        }


        // actually output results
        Writer w;
        try {
            w = new PrintWriter(new FileWriter(output_filename));
            for (Boolean result : run_results)
            {
                if (result.booleanValue())
                    w.write("1");
                else
                    w.write("0");
                w.write(",");
            }
            w.flush();
            w.close();
        } catch (IOException e) {
            e.printStackTrace();
            assert(false);
        }


        while (true)
        {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException _ex) {
                _ex.printStackTrace();
                break;
            }
        }


        // disconnect the shim connection
        shim.stop();
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
