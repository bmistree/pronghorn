package experiments;

import single_host.SingleHostRESTShim;
import single_host.SingleHostSwitchStatusHandler;
import single_host.JavaPronghornInstance.PronghornInstance;
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




public class SingleControllerVariableContentionAllSwitches {
	
    public static final int FLOODLIGHT_PORT_CSV_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 1;
    public static final int THREADS_ARG_INDEX = 2;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 3;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 4)
        {
            print_usage();
            return;
        }

        Set<Integer> floodlight_port_set = Util.parse_csv_ports(
            args[FLOODLIGHT_PORT_CSV_ARG_INDEX]);

        int num_ops_to_run = 
                Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        int num_threads =
            Integer.parseInt(args[THREADS_ARG_INDEX]);

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
        
        /* Spawn thread per switch to operate on it */
        ArrayList<Thread> threads = new ArrayList<Thread>();
        // each thread has a unique index into this results map
        ConcurrentHashMap<String,List<Long>> results =
            new ConcurrentHashMap<String,List<Long>>();

        long start = System.nanoTime();
        for (int j = 0; j < num_threads; ++j)
        {
            ThroughputThread t =
                new ThroughputThread(
                    prong, num_ops_to_run, results);
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
            ((double) (num_threads * num_ops_to_run)) /
            ((double)elapsedNano/1000000000);
        System.out.println("Switches: 1 Throughput(op/s): " + throughputPerS);

        // actually tell shims to stop.
        for (SingleHostRESTShim shim : shim_set)
            shim.stop();
    }
    
    public static void print_usage()
    {
        System.out.println(
            "\nSingleHost <int: floodlight port number> " + 
            "<int: num ops to run>\n");
    }
    
    public static class ThroughputThread extends Thread {

        private static final AtomicInteger atom_int = new AtomicInteger(0);
        
        int num_ops_to_run;
        PronghornInstance prong;
        ConcurrentHashMap<String,List<Long>> results;
        String result_id = null;
        
        public ThroughputThread(
            PronghornInstance prong, int num_ops_to_run,
            ConcurrentHashMap<String,List<Long>> results)
        {
            this.num_ops_to_run = num_ops_to_run;
            this.prong = prong;
            this.results = results;
            this.result_id = Integer.toString(atom_int.getAndIncrement());
    	}

    	public void run() {
            ArrayList<Long> completion_times = new ArrayList<Long>();
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                try {
                    if ((i %2) == 0)
                        prong.insert_entry_on_last_switch();
                    else
                        prong.remove_entry_on_last_switch();
                } catch (Exception _ex) {
                    _ex.printStackTrace();
                    assert(false);
                }
                completion_times.add(System.nanoTime());
            }
            results.put(result_id,completion_times);
    	}
    }
}
