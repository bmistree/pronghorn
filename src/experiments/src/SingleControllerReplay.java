package experiments;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import ralph.RalphGlobals;
import ralph.NonAtomicInternalList;
import ralph.DurabilityInfo;

import RalphDurability.DurabilityReplayer;

import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import experiments.Util.HostPortPair;
import experiments.Util;
import experiments.Util.LatencyThread;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.PathApplicationJava.PathApplication;

public class SingleControllerReplay
{
    // wait this long for pronghorn to add all switches
    public static final int SETTLING_TIME_WAIT = 5000;
    private static final int NUM_ENTRIES_PER_SWITCH = 10;

    public static void main (String[] args)
    {
        RalphGlobals ralph_globals = new RalphGlobals();

        /* Start up pronghorn */
        Instance prong = null;
        GetNumberSwitches num_switches_app = null;
        PathApplication path_app = null;
        try
        {
            prong = Instance.create_single_sided(ralph_globals);
            prong.start();
            num_switches_app =
                GetNumberSwitches.create_single_sided(ralph_globals);
            path_app =
                PathApplication.create_single_sided(ralph_globals);

            prong.add_application(num_switches_app);
            prong.add_application(path_app);
        } catch (Exception _ex) {
            System.out.println("\n\nERROR CONNECTING\n\n");
            return;
        }

        FloodlightShim shim = new FloodlightShim();

        SwitchStatusHandler switch_status_handler =
            new SwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                true, -1);

        shim.subscribe_switch_status_handler(switch_status_handler);
        shim.start();

        System.out.println("\nListening for num switches\n");
        // wait for first switch to connect
        Util.wait_on_switches(num_switches_app);
        // what's the first switch's id.
        String switch_id = Util.first_connected_switch_id(num_switches_app);
        List<String> switch_ids = Util.get_switch_id_list(num_switches_app);

        try
        {
            // Add a path for a packet with a given dl src.
            for (int i = 0; i < NUM_ENTRIES_PER_SWITCH; ++i) {
                for (String sw_id : switch_ids) {
                    String dl = "00:11:22:33:44:" + String.format("%02d", i);
                    path_app.add_output_action(sw_id, 100.0, dl, 1.0);
                }
            }
        }
        catch (Exception _ex)
        {
            _ex.printStackTrace();
            assert(false);
            return;
        }

        // stop shim
        shim.stop();

        // Now, try to replay from durability file
        DurabilityReplayer replayer =
            (DurabilityReplayer)DurabilityInfo.instance.durability_replayer;

        while(replayer.step(ralph_globals)) { }

         PathApplication replayed_setter_getter =
            (PathApplication)replayer.get_endpoint_if_exists(path_app.uuid());

         try {
             boolean passed = true;
             for (String sw_id : switch_ids) {
                 Double replayed_num_entries =
                     replayed_setter_getter.get_num_entries(sw_id);

                 if (replayed_num_entries.intValue() != NUM_ENTRIES_PER_SWITCH) {
                     System.out.println("\n\nREPLAY FAILED " + replayed_num_entries +
                                        "\n\n");
                     passed = false;
                 }
             }
             if (passed) {
                 System.out.println("\n\nREPLAY SUCCEEDED\n\n");
             }
         } catch (Exception ex) {
             System.out.println("\n\nTest failed\n\n");
             ex.printStackTrace();
             System.out.println("\n\nTest failed\n\n");
         }
        Util.force_shutdown();
    }
}
