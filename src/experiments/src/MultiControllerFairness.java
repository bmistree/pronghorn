package experiments;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import ralph.RalphGlobals;
import ralph.EndpointConstructorObj;
import ralph.Endpoint;
import ralph.Ralph;
import ralph.InternalServiceFactory;
import ralph.BoostedManager.DeadlockAvoidanceAlgorithm;
import ralph.RalphObject;
import ralph.NonAtomicInternalList;
import ralph.InternalServiceReference;

import RalphDurability.IDurabilityContext;
import RalphDurability.DurabilityReplayContext;

import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.MultiControllerFairnessJava.MultiControllerFairnessApp;
import experiments.Util.HostPortPair;
import experiments.Util;
import experiments.Util.LatencyThread;


/**
   Want to test that get fair access to resources, even when resource
   contention occurs on a foreign host.  To do so, create a topology
   of controllers with a single head node.  Eg.,

       A -> B -> C

   or

       A ---> B
         \--> C

   etc., where A is the head node, and arrows indicate the direction
   that requests to run single_ops go.  (Ie, in the first topology, A
   can ask B to run a single op and B can ask C, but B cannot ask A, C
   cannot ask B.)

   The head node generates two instances of fairness_app, one for each
   principal.  The other nodes generate only a single fairness app.
   The head node then dumps many events into the system at the behest
   of Principal a, and a short time later, dumps many events into the
   system at the behest of Principal b.

   When the event completes, tags the time that it completed on head
   and eventually writes to file.

 */
public class MultiControllerFairness
{
    public static final int CHILDREN_TO_CONTACT_HOST_PORT_CSV_ARG_INDEX = 0;
    public static final int PORT_TO_LISTEN_FOR_CONNECTIONS_ON_ARG_INDEX = 1;

    // if number_ops_to_run is not 0, then that means that this is head node.
    public static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 2;
    // if the local controller should perform a read or a write when
    // it receives a request to perform a single operation.
    public static final int SHOULD_WRITE_ARG_INDEX = 3;
    public static final int USE_WOUND_WAIT_ARG_INDEX = 4;
    public static final int COLLECT_STATISTICS_ARG_INDEX = 5;
    public static final int OUTPUT_FILENAME_ARG_INDEX = 6;

    public static Instance prong = null;
    /**
       Both will be non-null on head.  Only _a will be non-null on all
       other nodes.
     */
    public static MultiControllerFairnessApp mc_fairness_app_principal_a = null;
    public static MultiControllerFairnessApp mc_fairness_app_principal_b = null;
    public static GetNumberSwitches num_switches_app = null;

    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;

    public static RalphGlobals ralph_globals = null;

    public static void main (String[] args)
    {
        /* Grab arguments */
        if (args.length != 7)
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

        int num_ops_to_run =
            Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);
        // running as head controller if told to run operations.
        // otherwise, running as body node.
        boolean is_head = num_ops_to_run != 0;

        boolean should_write =
            Boolean.parseBoolean(args[SHOULD_WRITE_ARG_INDEX]);

        boolean use_wound_wait =
            Boolean.parseBoolean(args[USE_WOUND_WAIT_ARG_INDEX]);

        int collect_statistics_period_ms =
            Integer.parseInt(args[COLLECT_STATISTICS_ARG_INDEX]);

        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        RalphGlobals.Parameters rg_params = new RalphGlobals.Parameters();
        rg_params.tcp_port_to_listen_for_connections_on = port_to_listen_on;
        if (use_wound_wait) {
            rg_params.deadlock_avoidance_algorithm =
                DeadlockAvoidanceAlgorithm.WOUND_WAIT;
        }
        else {
            System.out.println("\nRalph-scheduling\n");
        }
        ralph_globals = new RalphGlobals(rg_params);

        /* Start up pronghorn */
        try
        {
            prong = Instance.create_single_sided(ralph_globals);
            prong.start();
            mc_fairness_app_principal_a =
                MultiControllerFairnessApp.create_single_sided(
                    ralph_globals);
            mc_fairness_app_principal_a.set_should_write(should_write);
            prong.add_application(mc_fairness_app_principal_a);

            if (is_head)
            {
                // only head node has two principals.  body nodes just
                // have one.
                mc_fairness_app_principal_b =
                    MultiControllerFairnessApp.create_single_sided(
                        ralph_globals);
                prong.add_application(mc_fairness_app_principal_b);
                mc_fairness_app_principal_b.set_should_write(should_write);
            }
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
                true,collect_statistics_period_ms);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();

        // FIXME: Must change arguments so that parent connects to host

        // now host actually connects to children
        for (HostPortPair hpp : children_to_contact_hpp)
        {
            try
            {
                System.out.println(
                    "\nConnecting to " + hpp.host + "  " + hpp.port);
                Ralph.tcp_connect(hpp.host, hpp.port, ralph_globals);

                if (is_head)
                {
                    InternalServiceFactory factory =
                        new InternalServiceFactory(MultiControllerFairnessApp.factory,
                                                   ralph_globals);
                    mc_fairness_app_principal_a.install_remotes(factory);
                    mc_fairness_app_principal_b.install_remotes(factory);
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
                assert(false);
            }
        }

        // wait for first switch to connect
        Util.wait_on_switches(num_switches_app);
        // what's the first switch's id.
        String switch_id = Util.first_connected_switch_id(num_switches_app);

        if (is_head)
        {
            ConcurrentLinkedQueue<String> tsafe_queue =
                new ConcurrentLinkedQueue<String>();

            FairnessUtil.run_operations(
                mc_fairness_app_principal_a, mc_fairness_app_principal_b,
                // using dummy switch id: in practice single_op
                // happens on all switches connected to controller.
                "",
                num_ops_to_run,tsafe_queue);

            // write results out.
            FairnessUtil.write_results(output_filename,tsafe_queue);
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

        // SHOULD_WRITE_ARG_INDEX
        usage_string +=
            "\n\t<boolean> : true if controller should perform " +
            "writes to switch; false if controller should perform reads\n";

        // USE_WOUND_WAIT_ARG_INDEX
        usage_string +=
            "\n\t<boolean>: true if should run using wound-wait; " +
            "false if should use ralph scheduler.\n";

        // COLLECT_STATISTICS_ARG_INDEX
        usage_string +=
            "\n\t<int> : period for collecting individual switch stastics " +
            "in ms.  < 0 if should not collect any statistics\n";

        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }
}
