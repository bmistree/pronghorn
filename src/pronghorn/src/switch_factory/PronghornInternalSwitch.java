package pronghorn.switch_factory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.List;

import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFPortStatisticsReply;

import ralph.Variables.NonAtomicTextVariable;
import ralph.Variables.AtomicNumberVariable;
import ralph.Variables.AtomicListVariable;
import ralph.RalphGlobals;

import pronghorn.PortStatsJava._InternalPortStats;
import pronghorn.SwitchJava._InternalSwitch;
import pronghorn.SwitchDeltaJava.SwitchDelta;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;

import pronghorn.IFloodlightShim;
import pronghorn.StatisticsUpdater;
import pronghorn.OFStatsMessageParser;


public class PronghornInternalSwitch
    extends _InternalSwitch implements Runnable
{
    public String ralph_internal_switch_id;
    private final IFloodlightShim shim;

    private final RalphGlobals ralph_globals;
    
    private final String floodlight_switch_id;

    /**
       @param {int} collect_statistics_period_ms.  If period < 0, then
       never collect statistics.
     */
    private final int collect_statistics_period_ms;
    /**
       Used to actually update switch statistics.
     */
    private final StatisticsUpdater stats_updater;

    public PronghornInternalSwitch(
        RalphGlobals _ralph_globals,String _ralph_internal_switch_id,
        double _available_capacity,
        _InternalSwitchDelta internal_switch_delta,
        int _collect_statistics_period_ms, IFloodlightShim _shim,
        String _floodlight_switch_id,StatisticsUpdater _stats_updater)
    {
        super(_ralph_globals);
        ralph_globals = _ralph_globals;
        ralph_internal_switch_id = _ralph_internal_switch_id;
        delta = new SwitchDelta (
            false,internal_switch_delta,_ralph_globals);
        switch_id = new NonAtomicTextVariable(
            false,_ralph_internal_switch_id,_ralph_globals);
        available_capacity =
            new AtomicNumberVariable(
                false,_available_capacity,_ralph_globals);

        shim = _shim;
        floodlight_switch_id = _floodlight_switch_id;
        collect_statistics_period_ms = _collect_statistics_period_ms;
        stats_updater = _stats_updater;

        // shim == null if simulating hardware
        if ((collect_statistics_period_ms > 0) && (shim != null)) 
        {
            // update thread to periodically poll for switch
            // statistics.
            Thread t = new Thread(this);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
       Periodically check the switch for statistics.
     */
    public void run()
    {
        while(true)
        {
            try
            {
                // FIXME: decide whether to collect any additional
                // statistics, eg., flow stats.

                // handle port stats
                Future<List<OFStatistics>> future_stats =
                    shim.get_port_stats(floodlight_switch_id);
                List<OFStatistics> port_stats_list = future_stats.get();
                for (OFStatistics of_stat : port_stats_list)
                {
                    OFPortStatisticsReply of_port_stat =
                        (OFPortStatisticsReply) of_stat;
                    _InternalPortStats port_stats =
                        OFStatsMessageParser.of_port_stats_to_ralph(
                            of_port_stat,ralph_globals);

                    Double port_num =
                        new Double(of_port_stat.getPortNumber());
                    stats_updater.update_port_stats(
                        ralph_internal_switch_id,port_num,port_stats);
                }
            }
            catch (IOException ex)
            {
                // switch failed.  stop this thread.  no longer
                // need to collect stats for switch.
                break;
            }
            catch (InterruptedException ex)
            {
                // retry on the flooldight controller
                continue;
            }
            catch (ExecutionException ex)
            {
                // retry on the flooldight controller
                continue;
            }

            // wait some time before polling again for more updates.
            try
            {
                Thread.sleep(collect_statistics_period_ms);
            }
            catch (InterruptedException ex)
            {
                ex.printStackTrace();
                System.out.println(
                    "\nShould never receive interrupted exception when " +
                    "polling for stats.\n");
                assert(false);
            }
        }
    }
}