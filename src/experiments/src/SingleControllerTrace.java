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
import experiments.PathApplicationJava.PathApplication;
import experiments.PathApplicationJava._InternalIpV4Packet;

public class SingleControllerTrace
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

        Double priority = 1.0;
        String tp_src = "22";
        String tp_dst = "80";
        Double output_port_number = 1.0;
        try
        {
            for (String sw_id : switch_ids) {
                path_app.add_output_action_tp(sw_id, priority, tp_src,
                                              tp_dst, output_port_number);

            }
        }
        catch (Exception _ex)
        {
            _ex.printStackTrace();
            assert(false);
            return;
        }

        // Check trace
        try {
            _InternalIpV4Packet fwd_pkt =
                path_app.make_ipv4_packet(0.0, tp_src, tp_dst);
            _InternalIpV4Packet drop_pkt =
                path_app.make_ipv4_packet(0.0, "10000", tp_dst);

            boolean succeeded = true;
            for (String sw_id : switch_ids) {
                _InternalIpV4Packet output_pkt =
                    path_app.trace_ipv4_packet(sw_id, fwd_pkt);
                Double output = path_app.get_output_port(output_pkt);
                if (!output.equals(output_port_number)) {
                    System.out.println("\n\nTRACE FAILURE: " + output + "\n\n");
                    assert(false);
                    succeeded = false;
                }

                output_pkt = path_app.trace_ipv4_packet(sw_id, drop_pkt);
                if (path_app.get_output_port(output_pkt) != null) {
                    System.out.println("\n\nTRACE FAILURE: " + output + "\n\n");
                    assert(false);
                    succeeded = false;
                }
            }

            if (succeeded) {
                System.out.println("\n\nTrace passed\n\n");
            }

        } catch(Exception _ex) {
            _ex.printStackTrace();
            assert(false);
            return;
        }

        // stop shim
        shim.stop();
        Util.force_shutdown();
    }
}
