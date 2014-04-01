package pronghorn;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import RalphExtended.ISpeculateListener;
import ralph.AtomicInternalList;
import ralph.Variables.AtomicListVariable;
import ralph.ActiveEvent;
import ralph.RalphObject;

import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;
import pronghorn.FTable._InternalFlowTableEntry;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.SwitchJava._InternalSwitch;

public class SwitchSpeculateListener implements ISpeculateListener
{
    protected static final Logger log =
        LoggerFactory.getLogger(SwitchSpeculateListener.class);

    private _InternalSwitchDelta switch_delta;
    private _InternalSwitch internal_switch;
    private RalphInternalPronghornSwitchGuard switch_guard;
    
    public SwitchSpeculateListener()
    {}

    public void init(
        _InternalSwitchDelta _switch_delta, _InternalSwitch _internal_switch,
        RalphInternalPronghornSwitchGuard _switch_guard)
    {
        switch_delta = _switch_delta;
        internal_switch = _internal_switch;
        switch_guard = _switch_guard;
    }


    @Override
    public void speculate(ActiveEvent active_event)
    {
        AtomicInternalList<_InternalFlowTableEntry,_InternalFlowTableEntry>
            internal_ft_list = get_internal_ft_list();
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = get_internal_ft_deltas_list();

        internal_ft_list.speculate(active_event,null);
        internal_ft_deltas_list.speculate(
            active_event,
            // so that resets delta list
            new ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>());

        switch_guard.speculate(active_event,null);
    }


    private AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
        get_internal_ft_deltas_list()
    {
        // these accesses are safe, because we assume the invariant
        // that will only receive changes on PronghornSwitchGuard
        // if no other event is writing to them.

        // grabbing ft_deltas to actually get changes made to hardware.
        AtomicListVariable<_InternalFlowTableDelta,_InternalFlowTableDelta>
            ft_deltas_list = switch_delta.ft_deltas;
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = null;

        if (ft_deltas_list.dirty_val != null)
            internal_ft_deltas_list = ft_deltas_list.dirty_val.val;
        else
            internal_ft_deltas_list = ft_deltas_list.val.val;

        return internal_ft_deltas_list;
    }

    private AtomicInternalList<_InternalFlowTableEntry,_InternalFlowTableEntry>
        get_internal_ft_list()
    {
        // these accesses are safe, because we assume the invariant
        // that will only receive changes on PronghornSwitchGuard
        // if no other event is writing to them.

        // grabbing internal_ft_list in case we need to speculate on it.
        AtomicListVariable<_InternalFlowTableEntry,_InternalFlowTableEntry>
            ft_list = internal_switch.ftable;
        AtomicInternalList<_InternalFlowTableEntry,_InternalFlowTableEntry>
            internal_ft_list = null;

        if (ft_list.dirty_val != null)
            internal_ft_list = ft_list.dirty_val.val;
        else
            internal_ft_list = ft_list.val.val;

        return internal_ft_list;
    }

    
}