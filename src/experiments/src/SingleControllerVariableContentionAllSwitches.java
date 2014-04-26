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
import java.util.HashSet;
import java.util.Set;
import experiments.Util.HostPortPair;
import experiments.Util;


public class SingleControllerVariableContentionAllSwitches
{
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int THREADS_ARG_INDEX = 1;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 2;
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

        int num_ops_to_run = 
                Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        int num_threads =
            Integer.parseInt(args[THREADS_ARG_INDEX]);

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        OffOnApplication off_on_app = null;
        try
        {
            RalphGlobals ralph_globals = new RalphGlobals();
            prong = new Instance(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            off_on_app = new OffOnApplication(
                ralph_globals,new SingleSideConnection());
            
            prong.add_application(num_switches_app,Util.ROOT_APP_ID);
            prong.add_application(off_on_app,Util.ROOT_APP_ID);
        }
        catch (Exception _ex)
        {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }
        String first_switch_id = Util.first_connected_switch_id (num_switches_app);
        
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
                    off_on_app, first_switch_id,num_ops_to_run, results);
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

        StringBuffer string_buffer = new StringBuffer();
        for (String switch_id : results.keySet())
        {
            List<Long> times = results.get(switch_id);
            String line = "";
            for (Long time : times)
                line += time.toString() + ",";
            if (line != "") {
                // trim off trailing comma
                line = line.substring(0, line.length() - 1);
            }

            string_buffer.append(line);
            string_buffer.append("\n");
        }
        Util.write_results_to_file(output_filename,string_buffer.toString());
        
        
        double throughputPerS =
            ((double) (num_threads * num_ops_to_run)) /
            ((double)elapsedNano/1000000000);
        System.out.println("Switches: 1 Throughput(op/s): " + throughputPerS);

        // actually tell shims to stop.
        shim.stop();
        
        Util.force_shutdown();
    }

    public static void print_usage()
    {
        String usage_string = "";

        // NUMBER_OPS_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

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
        
        private final int num_ops_to_run;
        private final OffOnApplication off_on_app;
        private final String switch_id;
        private final ConcurrentHashMap<String,List<Long>> results;
        private final String result_id;
        
        public ThroughputThread(
            OffOnApplication off_on_app, String switch_id,int num_ops_to_run,
            ConcurrentHashMap<String,List<Long>> results)
        {
            this.off_on_app = off_on_app;
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.results = results;
            this.result_id = Integer.toString(atom_int.getAndIncrement());
    	}

    	public void run() {
            ArrayList<Long> completion_times = new ArrayList<Long>();
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                try {
                    if ((i %2) == 0)
                        off_on_app.remove_entry_switch(switch_id);
                    else
                        off_on_app.add_entry_switch(switch_id);
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
