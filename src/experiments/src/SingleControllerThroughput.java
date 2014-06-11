package experiments;

import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.OffOnApplicationJava.OffOnApplication;
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
import experiments.Util;


public class SingleControllerThroughput
{
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_WARMUP_ARG_INDEX = 1;
    public static final int COARSE_LOCKING_ARG_INDEX = 2;
    public static final int THREADS_PER_SWITCH_ARG_INDEX = 3;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 4;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 5;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    
    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 6)
        {
            print_usage();
            return;
        }

        int num_ops_to_run = 
                Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        int num_warmup_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_WARMUP_ARG_INDEX]);

        boolean coarse_locking =
            Boolean.parseBoolean(args[COARSE_LOCKING_ARG_INDEX]);

        int threads_per_switch =
            Integer.parseInt(args[THREADS_PER_SWITCH_ARG_INDEX]);

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        RalphGlobals ralph_globals = new RalphGlobals();
        try
        {
            prong = new Instance(
                ralph_globals,new SingleSideConnection());

            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());

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

        /* wait a while to ensure that all switches are connected */
        try {
            Thread.sleep(SETTLING_TIME_WAIT);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            assert(false);
        }

        // wait until a few switches connect
        Util.wait_on_switches(num_switches_app);

        List<String> switch_ids = Util.get_switch_id_list(num_switches_app);
        int num_switches = switch_ids.size();
        if (num_switches == 0)
        {
            System.out.println(
                "No switches attached to pronghorn: error");
            assert(false);
        }        

        List<OffOnApplication> off_on_app_list =
            new ArrayList<OffOnApplication>();        
        try
        {
            OffOnApplication off_on_app = null;
            
            for (int i = 0; i < num_switches; ++i)
            {
                off_on_app = new OffOnApplication(
                    ralph_globals,new SingleSideConnection());
                prong.add_application(off_on_app,Util.ROOT_APP_ID);
                off_on_app_list.add(off_on_app);
            }
            // add some existing entries to all switches
            off_on_app.add_entry_all_switches("43.31.1.3");
            off_on_app.add_entry_all_switches("43.31.1.2");
            off_on_app.add_entry_all_switches("43.31.1.1");
        }
        catch (Exception ex)
        {
            System.out.println("Unknown exception error.");
            assert(false);
        }
        
        
        /* Spawn thread per switch to operate on it */
        ArrayList<Thread> threads = new ArrayList<Thread>();
        ArrayList<Thread> warmup_threads = new ArrayList<Thread>();
        // each thread has a unique index into this results map
        ConcurrentHashMap<String,List<Long>> results =
            new ConcurrentHashMap<String,List<Long>>();


        int highest_subnet_byte = 0;
        for (int i =0; i < switch_ids.size(); ++i)
        {
            String switch_id = switch_ids.get(i);
            OffOnApplication off_on_app = off_on_app_list.get(i);
            for (int j = 0; j < threads_per_switch; ++j)
            {
                ++highest_subnet_byte;
                ThroughputThread t =
                    new ThroughputThread(
                        switch_id, off_on_app, num_ops_to_run, results,coarse_locking,
                        highest_subnet_byte);
                threads.add(t);
                ThroughputThread wt =
                    new ThroughputThread(
                        switch_id, off_on_app, num_warmup_ops_to_run, results,coarse_locking,
                        highest_subnet_byte);
                warmup_threads.add(wt);
            }
        }

        if (highest_subnet_byte > 256)
        {
            System.out.println("Error: more threads than subnet bytes");
            System.exit(-1);
        }

        // do warmup
        try
        {
            for (Thread t : warmup_threads)
                t.start();
            for (Thread t : warmup_threads)
                t.join();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            assert(false);
            return;
        }
        
        // do actual ops
        long start = System.nanoTime();
        for (Thread t: threads)
            t.start();
        
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

        
        StringBuffer string_buffer = new StringBuffer();
        for (String results_index : results.keySet())
        {
            List<Long> times = results.get(results_index);
            String line = "";
            for (Long time : times)
                line += time.toString() + ",";
            if (line != "") {
                // trim off trailing comma
                line = line.substring(0, line.length() - 1);
            }
            string_buffer.append(line).append("\n");
        }
        Util.write_results_to_file(output_filename,string_buffer.toString());


        double throughputPerS =
            ((double) (num_switches * threads_per_switch * num_ops_to_run)) /
            ((double)elapsedNano/1000000000);
        System.out.println("Switches: " + num_switches + " Throughput(op/s): " + throughputPerS);

        // actually tell shims to stop.
        shim.stop();

        Util.force_shutdown();
    }

    private static void print_usage()
    {
        String usage_string = "";

        // NUMBER_TIMES_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // NUMBER_TIMES_TO_WARMUP_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to use for warmup\n";
        
        // COARSE_LOCKING_ARG_INDEX_ARG_INDEX
        usage_string +=
            "\n\t<boolean>: true if should use coarse locking; ";
        usage_string += "false otherwise\n";

        // NUMBER_THREADS_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number threads.\n";

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

        private final String switch_id;
        int num_ops_to_run;
        OffOnApplication off_on_app;
        ConcurrentHashMap<String,List<Long>> results;
        boolean coarse_locking;
        String result_id = null;
        
        // Each thread adds entries from a unique range of high level
        // ip addrs.  Eg., one thread would add entries for 18.*.*.*,
        // another for 19.*.*.*, etc.
        int highest_subnet_byte;
        
        public ThroughputThread(
            String switch_id, OffOnApplication off_on_app, int num_ops_to_run,
            ConcurrentHashMap<String,List<Long>> results, boolean coarse_locking,
            int highest_subnet_byte)
        {
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.off_on_app = off_on_app;
            this.results = results;
            this.coarse_locking = coarse_locking;
            this.result_id = switch_id + atom_int.getAndIncrement();
            this.highest_subnet_byte = highest_subnet_byte;
    	}

    	public void run() {
            ArrayList<Long> completion_times = new ArrayList<Long>();
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                int b_subnet_byte = i >> 16;
                int c_subnet_byte = (i << 8) >> 16;
                int d_subnet_byte = (i << 16) >> 16;
                
                String ip_src_to_add = highest_subnet_byte + "." +
                    b_subnet_byte + "." + c_subnet_byte + "." +
                    d_subnet_byte;
                
                try
                {
                    if (coarse_locking)
                        off_on_app.single_op_coarse(switch_id);
                    else
                    {
                        if ((i%2) == 0)
                        {
                            off_on_app.add_specific_entry_switch(
                                switch_id,ip_src_to_add);
                        }
                        else
                            off_on_app.remove_entry_switch(switch_id);
                    }

                }
                catch (Exception _ex)
                {
                    _ex.printStackTrace();
                    assert(false);
                }
                completion_times.add(System.nanoTime());
            }
            results.put(result_id,completion_times);
    	}
    }
}
