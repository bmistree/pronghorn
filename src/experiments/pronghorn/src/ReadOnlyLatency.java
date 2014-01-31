package experiments;

import single_host.SingleHostRESTShim;
import single_host.SingleHostSwitchStatusHandler;
import single_host.JavaPronghornInstance.PronghornInstance;
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


public class ReadOnlyLatency
{
    public static final int FLOODLIGHT_PORT_CSV_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 1;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 2;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 3)
        {
            print_usage();
            return;
        }

        Set<Integer> floodlight_port_set = Util.parse_csv_ports(
            args[FLOODLIGHT_PORT_CSV_ARG_INDEX]);
        
        int num_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        int num_threads = 1;


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
        

        /* wait a while to ensure that all switches are connected */
        try {
            Thread.sleep(SETTLING_TIME_WAIT);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            assert(false);
        }
            
        try {
            prong.insert_entry_on_last_switch();
        } catch (Exception _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        List<LatencyThread> all_threads = new ArrayList<LatencyThread>();
        for (int i = 0; i < num_threads; ++i)
            all_threads.add(new LatencyThread(prong,num_ops_to_run));

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


    private static class LatencyThread extends Thread
    {
        public List <Long> all_times = new ArrayList<Long>();

        
        private PronghornInstance prong = null;
        private String switch_id = null;
        private int num_ops_to_run = -1;
        
        public LatencyThread(
            PronghornInstance prong, int num_ops_to_run)
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
                    prong.read_first_instance_routing_table();
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
