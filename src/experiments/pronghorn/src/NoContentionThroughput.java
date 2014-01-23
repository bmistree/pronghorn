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



public class NoContentionThroughput {
	
    public static final int FLOODLIGHT_PORT_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 1;
    
    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 2)
        {
            print_usage();
            return;
        }
        
        int floodlight_port =
            Integer.parseInt(args[FLOODLIGHT_PORT_ARG_INDEX]);
        int num_ops_to_run = 
                Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

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
        SingleHostRESTShim shim = new  SingleHostRESTShim(floodlight_port);
        SingleHostSwitchStatusHandler switch_status_handler =
            new SingleHostSwitchStatusHandler(
                prong,shim,
                FloodlightRoutingTableToHardware.FLOODLIGHT_ROUTING_TABLE_TO_HARDWARE_FACTORY);
        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();


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
        /* Spawn thread per switch to operate on it */

        ArrayList<Thread> threads = new ArrayList<Thread>();
        ConcurrentHashMap<String,List<Long>> results = new ConcurrentHashMap<String,List<Long>>();

        // Don't warmup now that we can factor it out.
        /*if (true){
            String switch_id = null;
            try { 
                switch_id = switch_list.get_val_on_key(null, new Double(0));
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }

          ThroughputThread t = new ThroughputThread(switch_id, prong, num_ops_to_run, results);
        	t.start();
            try {
          t.join();
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }
            }*/


        long start = System.nanoTime();
        for (int i = 0; i < num_switches; i++) {
            String switch_id = null;
            try { 
                switch_id = switch_list.get_val_on_key(null, new Double((double)i));
            } catch (Exception _ex) {
                _ex.printStackTrace();
                assert(false);
            }

          ThroughputThread t = new ThroughputThread(switch_id, prong, num_ops_to_run, results);
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
            w = new PrintWriter(new FileWriter("times.csv"));

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

        double throughputPerS = ((double) (num_switches * num_ops_to_run)) / ((double)elapsedNano/1000000000);
        System.out.println("Switches: " + num_switches + " Throughput(op/s): " + throughputPerS);
        
        shim.stop();
    }
    
    public static void print_usage()
    {
        System.out.println(
            "\nSingleHost <int: floodlight port number> " + 
            "<int: num ops to run>\n");
    }
    
    public static class ThroughputThread extends Thread {
        String switch_id;
        int num_ops_to_run;
        PronghornInstance prong;
        ConcurrentHashMap<String,List<Long>> results;
        public ThroughputThread(String switch_id, PronghornInstance prong, int num_ops_to_run,
                                ConcurrentHashMap<String,List<Long>> results) {
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.prong = prong;
            this.results = results;
    	}
    	
    	public void run() {
          ArrayList<Long> completion_times = new ArrayList<Long>();
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                try {
                    prong.single_op(switch_id);
                } catch (Exception _ex) {
                    _ex.printStackTrace();
                    assert(false);
                }
                completion_times.add(System.nanoTime());
            }
            results.put(switch_id, completion_times);
    	}
    }
}
