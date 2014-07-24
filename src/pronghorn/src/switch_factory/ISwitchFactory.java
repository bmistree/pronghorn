package pronghorn.switch_factory;

import pronghorn.FTable;
import pronghorn.FTable._InternalFlowTableEntry;
import pronghorn.SwitchJava._InternalSwitch;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.SwitchDeltaJava.SwitchDelta;
import pronghorn.PortStatsJava._InternalPortStats;

import ralph.Variables.NonAtomicTextVariable;
import ralph.Variables.AtomicNumberVariable;
import ralph.Variables.AtomicListVariable;
import ralph.RalphGlobals;

import pronghorn.IFloodlightShim;
import pronghorn.FlowTableToHardware;
import pronghorn.StatisticsUpdater;


public interface ISwitchFactory
{
    public void init(String _factory_switch_prefix);
    
    public PronghornInternalSwitch construct(
        double available_capacity,StatisticsUpdater stats_updater);

    /**
       @param to_handle_pushing_changes --- Can be null, in which
       case, will simulate pushing changes to hardware.  (Ie., will
       always return message that says it did push changes to hardware
       without doing any work.)  
     */
    public PronghornInternalSwitch construct(
        double available_capacity,
        FlowTableToHardware to_handle_pushing_changes,
        IFloodlightShim shim, String floodlight_switch_id,
        StatisticsUpdater stats_updater);
}

