package experiments;

import ralph.RalphGlobals;

import pronghorn.FloodlightShim;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;
import pronghorn.InstanceJava.Instance;
import pronghorn.SwitchStatusHandler;

import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.OffOnApplicationJava.OffOnApplication;
import experiments.Util;


public class SingleControllerMeanSwitch {

    private static final int NUMBER_OPS_TO_RUN_ARG_INDEX = 0;

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Require num ops arg");
        }

        int numOpsToRun = Integer.parseInt(args[NUMBER_OPS_TO_RUN_ARG_INDEX]);

        RalphGlobals ralphGlobals = new RalphGlobals();

        try {
            Instance prong = Instance.create_single_sided(ralphGlobals);
            prong.start();
            GetNumberSwitches numSwitchesApp =
                GetNumberSwitches.create_single_sided(ralphGlobals);

            FloodlightShim shim = new FloodlightShim();

            SwitchStatusHandler switchStatusHandler =
                new SwitchStatusHandler(
                    shim, prong,
                    FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                    false, -1);

            shim.subscribe_switch_status_handler(switchStatusHandler);
            shim.start();
            prong.add_application(numSwitchesApp);
            Util.wait_on_switches(numSwitchesApp);

            // Add on-off-application
            OffOnApplication offOnApp =
                OffOnApplication.create_single_sided(ralphGlobals);
            prong.add_application(offOnApp);

            // Run flow mods
            for (int i = 0; i < numOpsToRun; ++i) {
                String dlSrc = "00:00:00:00:00:" + Integer.toHexString(i);
                offOnApp.add_entry_all_switches(dlSrc);
                offOnApp.remove_first_entry_all_switches();
            }

            Double numEntries = offOnApp.all_flow_table_entries();
            int intNumEntries = numEntries.intValue();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}