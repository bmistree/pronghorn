package experiments;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;

import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import experiments.Util;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.OffOnApplicationJava.OffOnApplication;


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

    public static final AtomicBoolean had_exception = new AtomicBoolean(false);
    
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
            had_exception.set(true);
            return;
        }

        FloodlightShim shim = new FloodlightShim();
        
        SwitchStatusHandler switch_status_handler =
            new SwitchStatusHandler(
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
            had_exception.set(true);
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
            off_on_app.add_entry_all_switches("00:00:00:00:00:1");
            off_on_app.add_entry_all_switches("00:00:00:00:00:2");
            off_on_app.add_entry_all_switches("00:00:00:00:00:3");
        }
        catch (Exception ex)
        {
            System.out.println("Unknown exception error.");
            ex.printStackTrace();
            System.out.println("\n\n");
            had_exception.set(true);
            assert(false);
        }
        
        /* Spawn thread per switch to operate on it */
        ArrayList<Thread> threads = new ArrayList<Thread>();
        ArrayList<Thread> warmup_threads = new ArrayList<Thread>();
        // each thread has a unique index into this results map
        ConcurrentHashMap<String,List<Long>> results =
            new ConcurrentHashMap<String,List<Long>>();
        ConcurrentHashMap<String,List<Long>> warmup_results =
            new ConcurrentHashMap<String,List<Long>>();

        // start from 1 to prevent collisions with entries added above with 00.
        int highest_dl_src_byte = 1;
        for (int i =0; i < switch_ids.size(); ++i)
        {
            String switch_id = switch_ids.get(i);
            OffOnApplication off_on_app = off_on_app_list.get(i);
            for (int j = 0; j < threads_per_switch; ++j)
            {
                ++highest_dl_src_byte;
                ThroughputThread t =
                    new ThroughputThread(
                        switch_id, off_on_app, num_ops_to_run, results,coarse_locking,
                        highest_dl_src_byte);
                threads.add(t);
                ThroughputThread wt =
                    new ThroughputThread(
                        switch_id, off_on_app, num_warmup_ops_to_run,
                        warmup_results,coarse_locking,
                        highest_dl_src_byte);
                warmup_threads.add(wt);
            }
        }

        if (highest_dl_src_byte > 256)
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
            had_exception.set(true);
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
                had_exception.set(true);
                assert(false);
            }
        }
        long end = System.nanoTime();
        long elapsedNano = end-start;


        // output results
        StringBuffer string_buffer = Util.produce_result_string(results);
        String results_to_write = string_buffer.toString();
        if (had_exception.get())
            results_to_write = "HAD AN EXCEPTION";
        
        Util.write_results_to_file(output_filename,results_to_write);
        Util.print_throughput_results(
            num_switches,threads_per_switch,num_ops_to_run,elapsedNano);
        
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

        // Each thread adds entries from a unique range of high level dl_src-s.
        // Eg., one thread would add entries for 01:*:*:*:*:* and another for
        // 02:*:*:*:*:*, etc.
        int highest_dl_src_byte;
        
        public ThroughputThread(
            String switch_id, OffOnApplication off_on_app, int num_ops_to_run,
            ConcurrentHashMap<String,List<Long>> results, boolean coarse_locking,
            int highest_dl_src_byte)
        {
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.off_on_app = off_on_app;
            this.results = results;
            this.coarse_locking = coarse_locking;
            this.result_id = switch_id + atom_int.getAndIncrement();
            this.highest_dl_src_byte = highest_dl_src_byte;
    	}

    	public void run() {
            ArrayList<Long> completion_times = new ArrayList<Long>();
            for (long i = 0; i < num_ops_to_run; ++i)
            {
                int b_byte = (int) ((long)(i & 0xff00000000L) >> 32);
                int c_byte = (int) ((long)(i & 0xff000000L) >> 24);
                int d_byte = (int) ((long)(i & 0xff0000L) >> 16);
                int e_byte = (int) ((long)(i & 0xff00L) >> 8);
                int f_byte = (int) ((long)(i & 0xffL));
                
                String dl_src_to_add =
                    Integer.toHexString(highest_dl_src_byte) + ":" +
                    Integer.toHexString(b_byte) + ":" +
                    Integer.toHexString(c_byte) + ":" +
                    Integer.toHexString(d_byte) + ":" +
                    Integer.toHexString(e_byte) + ":" +
                    Integer.toHexString(f_byte);

                try
                {
                    if (coarse_locking)
                        off_on_app.single_op_coarse(switch_id);
                    else
                    {
                        if ((i%2) == 0)
                        {
                            off_on_app.add_specific_entry_switch(
                                switch_id,dl_src_to_add);
                        }
                        else
                            off_on_app.remove_entry_switch(switch_id);
                    }

                }
                catch (Exception _ex)
                {
                    _ex.printStackTrace();
                    had_exception.set(true);
                    assert(false);
                }
                completion_times.add(System.nanoTime());
            }
            results.put(result_id,completion_times);
    	}
    }
}
