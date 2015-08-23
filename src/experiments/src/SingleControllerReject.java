package experiments;

import java.util.List;

import ralph.RalphGlobals;
import ralph.DurabilityInfo;

import RalphDurability.DurabilityReplayer;

import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import experiments.Util;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.TestErrorJava.TestError;


public class SingleControllerReject
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
        TestError test_error_app = null;
        try
        {
            prong = Instance.create_single_sided(ralph_globals);
            prong.start();
            num_switches_app =
                GetNumberSwitches.create_single_sided(ralph_globals);
            test_error_app = TestError.create_single_sided(ralph_globals);

            prong.add_application(num_switches_app);
            prong.add_application(test_error_app);
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

        boolean success = true;
        try
        {
            for (String sw_id : switch_ids) {
                test_error_app.add_good_bad_entry(sw_id);
                Double num_entries = test_error_app.get_num_entries(sw_id);
                if (num_entries.intValue() != 0) {
                    success = false;
                }
            }
        }
        catch (Exception _ex)
        {
            _ex.printStackTrace();
            assert(false);
            return;
        }

        if (success) {
            System.out.println("\n\nSUCCESS\n\n");
        } else {
            System.out.println("\n\nFAILURE\n\n");
        }

        // stop shim
        shim.stop();
        Util.force_shutdown();
    }
}
