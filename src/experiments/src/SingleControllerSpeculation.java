package experiments;

import pronghorn.SingleInstanceFloodlightShim;
import pronghorn.SingleInstanceSwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.SingleHostSpeculationJava.SingleHostSpeculation;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import pronghorn.FloodlightFlowTableToHardware;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import experiments.Util;


public class SingleControllerSpeculation
{
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;
    public static final int SHOULD_SPECULATE_ARG_INDEX = 1;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 2;

    public static void main(String [] args)
    {
        /* Grab arguments */
        if (args.length != 3)
        {
            print_usage();
            return;
        }

        int num_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        boolean specualte =
            Boolean.parseBoolean(args[SHOULD_SPECULATE_ARG_INDEX]);

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
            
            prong.add_application(num_switches_app);
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
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();
        
        // wait for first switch to connect
        Util.wait_on_switches(num_switches_app);

        // All switches should have connected.  Install speculation
        // app, which requires all switches to already have been
        // registered.
        try
        {
            speculation_app = new SingleHostSpeculation(
                ralph_globals,new SingleSideConnection());
            prong.add_application(speculation_app);
        }
        catch (Exception ex)
        {
            System.out.println("\n\nERROR INSTALLING APP\n\n");
            return;
        }

        SpeculationThread spec_thread_1 =
            new SpeculationThread(true,num_ops_to_run,speculation_app);
        SpeculationThread spec_thread_2 =
            new SpeculationThread(false,num_ops_to_run,speculation_app);
        
        spec_thread_1.start();
        spec_thread_2.start();

        try
        {
            spec_thread_1.join();
            spec_thread_2.join();
        }
        catch (Exception _ex)
        {
            _ex.printStackTrace();
            assert(false);
            return;
        }
        

        StringBuffer results_buffer = new StringBuffer();
        spec_thread_1.write_times(results_buffer);
        spec_thread_2.write_times(results_buffer);
        Util.write_results_to_file(output_filename,results_buffer.toString());

        // stop and forst stop
        shim.stop();
        Util.force_shutdown();
    }

    private static class SpeculationThread extends Thread
    {
        private final boolean event_1;
        private final int num_ops_to_run;
        private final SingleHostSpeculation speculation_app;
        public final List <Long> all_times = new ArrayList<Long>();
        
        public SpeculationThread(
            boolean event_1,int num_ops_to_run,
            SingleHostSpeculation speculation_app)
        {
            this.event_1 = event_1;
            this.num_ops_to_run = num_ops_to_run;
            this.speculation_app = speculation_app;
        }
        public void run()
        {
            try
            {

                for (int i = 0; i < num_ops_to_run; ++i)
                {
                    long start_time = System.nanoTime();
                    if (event_1)
                        speculation_app.event_1();
                    else
                        speculation_app.event_2();
                    long total_time = System.nanoTime() - start_time;
                    all_times.add(total_time);
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                assert(false);
            }
        }
        
        public void write_times(StringBuffer buffer)
        {
            for (Long latency : all_times)
                buffer.append(latency.toString()).append(",");
            
            if (! all_times.isEmpty())
                buffer.append("\n");
        }
    }
    
    private static void print_usage()
    {
        String usage_string = "";

        // NUMBER_OPS_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // Should speculate
        usage_string +=
            "\n\t<boolean>: Should speculate.\n";
        
        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }    
}