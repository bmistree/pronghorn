package pronghorn;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;

import ralph.Variables.AtomicNumberVariable;
import ralph.RalphGlobals;
import ralph.ActiveEvent;
import RalphDataWrappers.ListTypeDataWrapper;

import pronghorn.FlowTableToHardware.WrapApplyToHardware;
import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;

public class InternalPronghornSwitchGuard extends AtomicNumberVariable
{

    public final String ralph_internal_switch_id;
    private final _InternalSwitchDelta switch_delta;
    private final FlowTableToHardware to_handle_pushing_changes;
    private final ExecutorService hardware_push_service;

    
    /**
       @param to_handle_pushing_changes --- Can be null, in which
       case, will simulate pushing changes to hardware.  (Ie.,
       will always return message that says it did push changes to
       hardware without doing any work.)
     */
    public InternalPronghornSwitchGuard(
        RalphGlobals ralph_globals, _InternalSwitchDelta _switch_delta,
        String _ralph_internal_switch_id,
        FlowTableToHardware _to_handle_pushing_changes,
        ExecutorService _hardware_push_service)
    {
        super(false,new Double(0),ralph_globals);
        switch_delta = _switch_delta;
        ralph_internal_switch_id = _ralph_internal_switch_id;
        to_handle_pushing_changes = _to_handle_pushing_changes;
        hardware_push_service = _hardware_push_service;
    }

    @Override
    protected Future<Boolean> internal_first_phase_commit(
        ActiveEvent active_event)
    {
        if (to_handle_pushing_changes == null)
            return super.internal_first_phase_commit(active_event);

        // FIXME: How to reset changes on SwitchDelta to empty
        // list.

        // this access is safe, because we assume the invariant
        // that will only receive changes on PronghornSwitchGuard
        // if no other event is writing to
        ListTypeDataWrapper<_InternalFlowTableDelta,_InternalFlowTableDelta>
            ltdw =
            (ListTypeDataWrapper<_InternalFlowTableDelta,_InternalFlowTableDelta>)
            switch_delta.ft_deltas.dirty_val.val.dirty_val;


        WrapApplyToHardware to_apply_to_hardware =
            new WrapApplyToHardware(to_handle_pushing_changes, ltdw);

        hardware_push_service.execute(to_apply_to_hardware);
        return to_apply_to_hardware.to_notify_when_complete;
    }

    // FIXME: must override acquire and release locks in case switch
    // fails.
}

