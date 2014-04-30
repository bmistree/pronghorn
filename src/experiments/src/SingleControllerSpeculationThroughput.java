package experiments;

import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.WrappedSwitchJava._InternalStructWrappedSwitch;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.SingleHostSpeculationJava.SingleHostSpeculation;
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


public class SingleControllerSpeculationThroughput
{
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int SPECULATION_ARG_INDEX = 1;
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

        // per thread
        int num_ops_to_run = 
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        boolean speculation_on =
            Boolean.parseBoolean(args[SPECULATION_ARG_INDEX]);

        System.out.println(
            "\n\nRunning with speculation " + speculation_on + "\n\n");
        
        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        SingleHostSpeculation speculation_app = null;
        RalphGlobals ralph_globals = new RalphGlobals();
        try
        {
            prong = new Instance(
                ralph_globals,new SingleSideConnection());
            num_switches_app = new GetNumberSwitches(
                ralph_globals,new SingleSideConnection());
            prong.add_application(num_switches_app,Util.ROOT_APP_ID);
        }
        catch (Exception _ex)
        {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        SingleInstanceFloodlightShim shim = new SingleInstanceFloodlightShim();
        
        SingleInstanceSwitchStatusHandler switch_status_handler =
            new SingleInstanceSwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                speculation_on,collect_statistics_period_ms);

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
        if ((num_switches%2) != 0)
        {
            System.out.println(
                "Expecting even number of switches attached to pronghorn: error");
            assert(false);
        }        
        if (num_switches == 0)
        {
            System.out.println(
                "No switches attached to pronghorn: error");
            assert(false);
        }        

        try
        {
            speculation_app = new SingleHostSpeculation(
                ralph_globals,new SingleSideConnection());
            prong.add_application(speculation_app,Util.ROOT_APP_ID);
            speculation_app.load_entry_all_switches("39.2.3.1");
            speculation_app.load_entry_all_switches("39.2.3.2");
            speculation_app.load_entry_all_switches("39.2.3.3");
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            System.out.println("\n\nError connecting application\n\n");
            assert(false);
        }
            

        
        /* Spawn thread per switch to operate on it */
        ArrayList<Thread> threads = new ArrayList<Thread>();
        // each thread has a unique index into this results map
        ConcurrentHashMap<String,List<Long>> results =
            new ConcurrentHashMap<String,List<Long>>();

        try
        {
            // spawn 2 threads per switch pair
            for (int i=0; i < (num_switches/2); ++i)
            {
                String one_switch_id = switch_ids.get(2*i);
                String other_switch_id = switch_ids.get(2*i +1);

                _InternalStructWrappedSwitch one_switch =
                    speculation_app.get_struct_wrapped_switch(one_switch_id);
                _InternalStructWrappedSwitch other_switch =
                    speculation_app.get_struct_wrapped_switch(other_switch_id);

                SpeculationThroughputThread stt_1 =
                    new SpeculationThroughputThread(
                        one_switch,other_switch,num_ops_to_run,speculation_app,
                        results);
                SpeculationThroughputThread stt_2 =
                    new SpeculationThroughputThread(
                        other_switch,one_switch,num_ops_to_run,speculation_app,
                        results);

                threads.add(stt_1);
                threads.add(stt_2);
            }
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            assert(false);
        }
        
        
        long start = System.nanoTime();
        for (Thread t : threads)
        {
            try
            {
                t.start();
            }
            catch (Exception _ex)
            {
                _ex.printStackTrace();
                assert(false);
            }
        }
        for (Thread t : threads)
        {
            try
            {
                t.join();
            }
            catch (Exception _ex)
            {
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
            string_buffer.append(line).append("\n");
        }
        Util.write_results_to_file(output_filename,string_buffer.toString());


        double throughputPerS =
            ((double) (num_switches * 2 * num_ops_to_run)) /
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

        // COARSE_LOCKING_ARG_INDEX_ARG_INDEX
        usage_string +=
            "\n\t<boolean>: true if should turn speculation on; ";
        usage_string += "false otherwise\n";

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
        
        _InternalStructWrappedSwitch one_switch;
        _InternalStructWrappedSwitch two_switch;
        int num_ops_to_run;
        SingleHostSpeculation speculation_app;
        ConcurrentHashMap<String,List<Long>> results;

        String result_id = null;
        
        public SpeculationThroughputThread(
            _InternalStructWrappedSwitch one_switch,
            _InternalStructWrappedSwitch two_switch,int num_ops_to_run,
            SingleHostSpeculation speculation_app,
            ConcurrentHashMap<String,List<Long>> results)
        {
            this.one_switch = one_switch;
            this.two_switch = two_switch;
            this.num_ops_to_run = num_ops_to_run;
            this.speculation_app = speculation_app;
            this.results = results;
            this.result_id = Integer.toString(atom_int.getAndIncrement());
    	}

    	public void run() {
            ArrayList<Long> completion_times = new ArrayList<Long>();
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                try
                {
                    boolean add_entry = (i % 2) == 0;
                    speculation_app.some_event_switches(
                        one_switch,two_switch,add_entry);
                }
                catch (Exception _ex)
                {
                    _ex.printStackTrace();
                    assert(false);
                }
                long time_completed = System.nanoTime();
                completion_times.add(time_completed);
            }
            results.put(result_id,completion_times);
    	}
    }
}
