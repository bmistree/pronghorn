package experiments;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;

import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import pronghorn.switch_factory.NoLogVersionFactory;
import pronghorn.switch_factory.IVersionListenerFactory;

import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.ReadOnlyThroughputJava.ReadOnly;
import experiments.Util;


public class ReadOnlyThroughput
{
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int NUMBER_OPS_TO_WARMUP_ARG_INDEX = 1;
    public static final int THREADS_PER_SWITCH_ARG_INDEX = 2;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 3;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 4;
    public static final int VERSION_LISTENER_ARG_INDEX = 5;
    
    // if any thread errored out, report error to had_exception.
    // Before returning, will make note of thread issues.
    public static final AtomicBoolean had_exception =
        new AtomicBoolean(false);
    
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
        
        int threads_per_switch =
            Integer.parseInt(args[THREADS_PER_SWITCH_ARG_INDEX]);

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);
        
        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        RalphGlobals ralph_globals = new RalphGlobals();
        
        IVersionListenerFactory ft_version_listener_factory =
            VersionListenerFactoryArgs.produce_flow_table_factory(
                args[VERSION_LISTENER_ARG_INDEX],ralph_globals);
        IVersionListenerFactory port_version_listener_factory =
            VersionListenerFactoryArgs.produce_ports_factory(
                args[VERSION_LISTENER_ARG_INDEX],ralph_globals);        

        
        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
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
        
        FloodlightShim shim = new FloodlightShim();
        
        SwitchStatusHandler switch_status_handler =
            new SwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                true,collect_statistics_period_ms,
                ft_version_listener_factory,port_version_listener_factory);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();

        // wait until a few switches connect
        Util.wait_on_switches(num_switches_app);

        List<String> switch_ids = Util.get_switch_id_list(num_switches_app);
        int num_switches = switch_ids.size();

        List<Thread> warmup_threads_to_run = new ArrayList<Thread>();
        List<Thread> threads_to_run = new ArrayList<Thread>();
        try
        {
            for (String switch_id : switch_ids)
            {
                for (int i = 0; i < threads_per_switch; ++i)
                {
                    ReadOnly read_only = new ReadOnly(
                        ralph_globals,new SingleSideConnection());
                    prong.add_application(read_only,Util.ROOT_APP_ID);
                    if (! read_only.set_switch(switch_id).booleanValue())
                    {
                        System.out.println("Trying to set switch that does not exist");
                        throw new Exception();
                    }
                    
                    threads_to_run.add(
                        new ReadOnlyThroughputThread(read_only, num_ops_to_run));
                    warmup_threads_to_run.add(
                        new ReadOnlyThroughputThread(
                            read_only, num_warmup_ops_to_run));
                }
            }

            // run warmup
            for (Thread t : warmup_threads_to_run)
                t.start();
            for (Thread t : warmup_threads_to_run)
                t.join();
            
            // run timed throughput
            long start = System.nanoTime();
            for (Thread t : threads_to_run)
                t.start();
            for (Thread t : threads_to_run)
                t.join();
            long end = System.nanoTime();

            
            double total_ops_performed_d =
                num_switches*threads_per_switch*num_ops_to_run;
            double elapsed_time_ns_d = ((double)(end - start));
            
            double throughput_per_ns = (total_ops_performed_d/elapsed_time_ns_d);
            double throughput_per_s = throughput_per_ns * 1000000000;
            String output_string = "Total throughput: " + throughput_per_s;

            if (had_exception.get())
                output_string = "Had exception while running";
            
            System.out.println("\n\n");
            System.out.println(output_string);
            System.out.println("\n\n");
            Util.write_results_to_file(output_filename,output_string);
        }
        catch (Exception ex)
        {
            had_exception.set(true);
            ex.printStackTrace();
            String output_string = "Had exception while running";
            Util.write_results_to_file(output_filename,output_string);
            assert(false);
        }
        
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
        
        // NUMBER_THREADS_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number threads.\n";

        // COLLECT_STATISTICS_ARG_INDEX
        usage_string +=
            "\n\t<int> : period for collecting individual switch stastics " +
            "in ms.  < 0 if should not collect any statistics\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";
        
        // VERSION_LISTENER_ARG_INDEX
        usage_string += VersionListenerFactoryArgs.usage_string();

        System.out.println(usage_string);
        
    }

    public static class ReadOnlyThroughputThread extends Thread
    {
        private final ReadOnly read_only;
        private final int num_ops_to_run;
        
        public ReadOnlyThroughputThread(ReadOnly read_only, int num_ops_to_run)
        {
            this.read_only = read_only;
            this.num_ops_to_run = num_ops_to_run;
    	}

        @Override
    	public void run() {
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                try
                {
                    read_only.read_switch();
                }
                catch (Exception _ex)
                {
                    _ex.printStackTrace();
                    had_exception.set(true);
                    assert(false);
                }
            }
    	}
    }
}
