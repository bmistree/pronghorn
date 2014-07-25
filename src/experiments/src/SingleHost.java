package experiments;

import java.lang.Thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;

import pronghorn.InstanceJava.Instance;
import pronghorn.FloodlightShim;
import pronghorn.SwitchStatusHandler;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import experiments.OffOnApplicationJava.OffOnApplication;



public class SingleHost
{
    // collect statistics once every 10 seconds.
    private static final int COLLECT_STATISTICS_PERIOD_MS = 10*1000;
    
    protected static final Logger log =
        LoggerFactory.getLogger(SingleHost.class);

    
    public static void main (String[] args)
    {
        Instance prong = null;
        OffOnApplication app = null;
        try
        {
            RalphGlobals ralph_globals = new RalphGlobals();
            prong = new Instance(
                ralph_globals,new SingleSideConnection());

            app = new OffOnApplication(
                ralph_globals,new SingleSideConnection());
        }
        catch (Exception _ex)
        {
            log.error(
                "\nUnexpected exception when connecting \n",
                _ex.toString());
            assert(false);
        }

        FloodlightShim shim = new FloodlightShim();
        SwitchStatusHandler switch_status_handler =
            new SwitchStatusHandler(
                shim,prong,
                FloodlightFlowTableToHardware.FLOODLIGHT_FLOW_TABLE_TO_HARDWARE_FACTORY,
                true,COLLECT_STATISTICS_PERIOD_MS);

        shim.subscribe_switch_status_handler(switch_status_handler);
        boolean block_traffic = true;

        try
        {
            prong.add_application(app,Util.ROOT_APP_ID);
        }
        catch (Exception ex)
        {
            log.error(
                "\nUnexpected exception when adding application\n",
                ex.toString());
            assert(false);
        }

        
        while (true)
        {            
            try
            {
                Thread.sleep(3000);
            }
            catch(InterruptedException ex)
            {
                break;
            }
            try
            {
                // cycle between allowing traffic between 10.0.0.1 and 10.0.0.2
                // for all switches in system and blocking it
                if (block_traffic)
                    app.block_traffic_all_switches();
                else
                    app.remove_first_entry_all_switches();

                System.out.println("Blocking traffic: " + block_traffic + "\n");
                block_traffic = ! block_traffic;
                
            } catch (Exception _ex)
            {
                _ex.printStackTrace();
                break;
            }
        }
    }
}