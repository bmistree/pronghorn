package experiments;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import ralph.RalphGlobals;
import ralph.Endpoint;
import ralph.Ralph;
import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import ralph.RalphObject;
import ralph.InternalServiceFactory;
import RalphDurability.IDurabilityContext;
import RalphDurability.DurabilityReplayContext;

import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;

import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import experiments.Util.HostPortPair;
import experiments.Util;
import experiments.Util.LatencyThread;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.MultiControllerErrorJava.MultiControllerErrorApp;


public class MultiControllerError
{
    public static final int CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX = 0;
    public static final int PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX = 1;

    // if number_ops_to_run is not 0, then that means that this is head node.
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 2;
    public static final int FAILURE_PROBABILITY_ARG_INDEX = 3;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 4;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 5;

    public static Instance prong = null;
    private static final int MAX_NUM_OPS_BEFORE_CHECK = 20;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;

    public static RalphGlobals ralph_globals;

    public static MultiControllerErrorApp mc_error_app = null;

    private static final boolean SHOULD_SPECULATE = true;

    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 6)
        {
            print_usage();
            return;
        }

        Set<HostPortPair> children_to_contact_hpp = null;
        if (! args[CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX].equals("-1"))
            children_to_contact_hpp = Util.parse_csv_host_port_pairs(
                args[CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX]);
        else
            children_to_contact_hpp = new HashSet<HostPortPair>();

        int port_to_listen_on =
            Integer.parseInt(args[PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX]);

        RalphGlobals.Parameters params = new RalphGlobals.Parameters();
        params.tcp_port_to_listen_for_connections_on = port_to_listen_on;
        ralph_globals = new RalphGlobals(params);


        int num_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);
        // running as head controller if told to run operations.
        // otherwise, running as body node.
        boolean is_head = num_ops_to_run != 0;

        // will add a single dummy switch that injects errors.
        float failure_probability =
            Float.parseFloat(args[FAILURE_PROBABILITY_ARG_INDEX]);

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);

        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        /* Start up pronghorn */
        GetNumberSwitches num_switches_app = null;
        try
        {
            prong = Instance.create_single_sided(ralph_globals);
            prong.start();
            mc_error_app = MultiControllerErrorApp.create_single_sided(ralph_globals);
            prong.add_application(mc_error_app);
            num_switches_app =
                GetNumberSwitches.create_single_sided(ralph_globals);
            prong.add_application(num_switches_app);
        }
        catch (Exception _ex)
        {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }


        FloodlightShim shim = new FloodlightShim();
        SwitchStatusHandler switch_status_handler =
            new SwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                false,collect_statistics_period_ms);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();

        // now actually try to conect to parent
        for (HostPortPair hpp : children_to_contact_hpp)
        {
            try
            {
                System.out.println(
                    "\nConnecting to " + hpp.host + "  " + hpp.port);
                InternalServiceFactory factory =
                        new InternalServiceFactory(MultiControllerErrorApp.factory,
                                                   ralph_globals);
                mc_error_app.install_remotes(factory);
            }
            catch(Exception e)
            {
                e.printStackTrace();
                assert(false);
            }
        }

        // wait for first switch to connect
        Util.wait_on_switches(num_switches_app);
        String faulty_switch_id = "some_switch";
        ErrorUtil.add_faulty_switch(
            ralph_globals,faulty_switch_id, SHOULD_SPECULATE,
            failure_probability,prong);

        List<String> switch_id_list =
            Util.get_switch_id_list (num_switches_app);


        if (is_head)
        {
            // Contains trues if test succeeded, false if test failed.
            List<Boolean> results_list =
                ErrorUtil.run_operations(
                    num_ops_to_run,mc_error_app,faulty_switch_id,switch_id_list);
            // write results
            ErrorUtil.write_results(output_filename,results_list);
        }
        else
        {
            // run indefinitely
            while (true)
            {
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException _ex)
                {
                    _ex.printStackTrace();
                    break;
                }
            }
        }

        // actually tell shims to stop.
        shim.stop();
        Util.force_shutdown();
    }

    private static void print_usage()
    {
        String usage_string = "";

        // CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX
        usage_string +=
            "\n\t<csv>: Children to contact host port csv.  Pronghorn ";
        usage_string += "controllers to connect to.  ";
        usage_string += "Format host:port,host:port\n";

        // PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX
        usage_string +=
            "\n\t<int>: Port to listen for connections on.\n";

        // NUMBER_OPS_TO_RUN_PER_EXPERIMENT_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // FAILURE_PROBABILITY_ARG_INDEX
        usage_string +=
            "\n\t<float>: failure probability.\n";

        // COLLECT_STATISTICS_ARG_INDEX
        usage_string +=
            "\n\t<int> : period for collecting individual switch stastics " +
            "in ms.  < 0 if should not collect any statistics\n";

        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
}
