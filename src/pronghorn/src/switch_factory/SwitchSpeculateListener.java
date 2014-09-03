package pronghorn.switch_factory;

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
    private InternalPronghornSwitchGuard switch_guard;
    
    public SwitchSpeculateListener()
    {}

    public void init(
        _InternalSwitchDelta _switch_delta, _InternalSwitch _internal_switch,
        InternalPronghornSwitchGuard _switch_guard)
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
        internal_ft_list.speculate(active_event);

        // NOTE: speculate for internal value of internal_ft_list is
        // called in DeltaListStateSupplier.  This is because when we
        // speculate we overwrite dirty_val, which
        // DeltaListStateSupplier actually needs.  See Issue #10.
        switch_guard.speculate(active_event);
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

        ft_list._lock();
        if (ft_list.dirty_val != null)
            internal_ft_list = ft_list.dirty_val.val;
        else
            internal_ft_list = ft_list.val.val;
        ft_list._unlock();
        
        return internal_ft_list;
    }    
}