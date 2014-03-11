package pronghorn;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.ArrayList;

import ralph.Variables.AtomicNumberVariable;
import ralph.AtomicInternalList;
import ralph.Variables.AtomicListVariable;
import ralph.RalphGlobals;
import ralph.ActiveEvent;
import RalphDataWrappers.ListTypeDataWrapper;
import RalphServiceActions.LinkFutureBooleans;

import pronghorn.FlowTableToHardware.WrapApplyToHardware;
import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.FTable._InternalFlowTableEntry;
import pronghorn.SwitchJava._InternalSwitch;

public class InternalPronghornSwitchGuard extends AtomicNumberVariable
{
    public final String ralph_internal_switch_id;
    private final _InternalSwitchDelta switch_delta;
    private final _InternalSwitch internal_switch;
    private final FlowTableToHardware to_handle_pushing_changes;
    private final ExecutorService hardware_push_service;
    private final boolean should_speculate;
    
    private ListTypeDataWrapper<_InternalFlowTableDelta,_InternalFlowTableDelta>
        dirty_op_tuples_on_hardware = null;
    
    
    /**
       @param to_handle_pushing_changes --- Can be null, in which
       case, will simulate pushing changes to hardware.  (Ie.,
       will always return message that says it did push changes to
       hardware without doing any work.)
     */
    public InternalPronghornSwitchGuard(
        RalphGlobals ralph_globals, _InternalSwitchDelta _switch_delta,
        _InternalSwitch _internal_switch,String _ralph_internal_switch_id,
        FlowTableToHardware _to_handle_pushing_changes,
        ExecutorService _hardware_push_service,
        boolean _should_speculate)
    {
        super(false,new Double(0),ralph_globals);
        switch_delta = _switch_delta;
        internal_switch = _internal_switch;
        ralph_internal_switch_id = _ralph_internal_switch_id;
        to_handle_pushing_changes = _to_handle_pushing_changes;
        hardware_push_service = _hardware_push_service;
        should_speculate = _should_speculate;
    }

    /**
       Called from outside of lock.
     */
    @Override
    protected Future<Boolean> internal_first_phase_commit(
        ActiveEvent active_event)
    {
        // do not need to take locks here because know that this
        // method will only be called from AtomicActiveEvent
        // during first_phase_commit. Because AtomicActiveEvent is
        // in midst of commit, know that these values cannot
        // change.
        if ((write_lock_holder == null) ||
            (! active_event.uuid.equals(write_lock_holder.event.uuid)))
        {
            // never made a write to this variable: do not need to
            // ensure that hardware is up (for now).  May want to
            // add read checks as well.
            return ALWAYS_TRUE_FUTURE;
        }

        if (to_handle_pushing_changes == null)
            return super.internal_first_phase_commit(active_event);
        
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

        // grabbing ft_deltas to actually get changes made to hardware.
        AtomicListVariable<_InternalFlowTableDelta,_InternalFlowTableDelta>
            ft_deltas_list = switch_delta.ft_deltas;
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = null;
        
        if (ft_deltas_list.dirty_val != null)
            internal_ft_deltas_list = ft_deltas_list.dirty_val.val;
        else
            internal_ft_deltas_list = ft_deltas_list.val.val;

        dirty_op_tuples_on_hardware = 
            (ListTypeDataWrapper<_InternalFlowTableDelta,_InternalFlowTableDelta>)
            internal_ft_deltas_list.dirty_val;
        
        // reset dirty val for ft deltas: after each commit, pending
        // flow table deltas (in ft_deltas) should be empty.
        internal_ft_deltas_list.dirty_val = 
            (ListTypeDataWrapper<_InternalFlowTableDelta,_InternalFlowTableDelta>)
            internal_ft_deltas_list.data_wrapper_constructor.construct(
                internal_ft_deltas_list.val.val,false);

        WrapApplyToHardware to_apply_to_hardware =
            new WrapApplyToHardware(
                to_handle_pushing_changes, dirty_op_tuples_on_hardware,false);

        if (should_speculate)
        {
            // start speculating on this lock guard
            speculate(active_event,dirty_val.val);
            
            // start speculating ft_deltas
            internal_ft_deltas_list.speculate(
                active_event,internal_ft_deltas_list.dirty_val.val);

            // start speculating on ftable itself
            internal_ft_list.speculate(
                active_event,new ArrayList(internal_ft_list.dirty_val.val));
        }
        
        hardware_push_service.execute(to_apply_to_hardware);
        return to_apply_to_hardware.to_notify_when_complete;
    }

    @Override
    protected boolean internal_complete_commit(ActiveEvent active_event)
    {
        _lock();
        boolean write_lock_holder_completed =
            super.internal_complete_commit(active_event);

        if (write_lock_holder_completed)
            dirty_op_tuples_on_hardware = null;
        _unlock();
        return write_lock_holder_completed;
    }

    @Override
    protected boolean internal_backout (ActiveEvent active_event)
    {
        _lock();
        boolean write_lock_holder_preempted = 
            super.internal_backout(active_event);
        if (write_lock_holder_preempted)
        {
            // if there were dirty values that we had pushed to the
            // hardware, we need to undo them.
            if (dirty_op_tuples_on_hardware != null)
            {
                WrapApplyToHardware to_undo_wrapper =
                    new WrapApplyToHardware(
                        to_handle_pushing_changes,dirty_op_tuples_on_hardware,
                        true);
                hardware_push_service.execute(to_undo_wrapper);
                to_undo_wrapper.to_notify_when_complete.get();
            }
            dirty_op_tuples_on_hardware = null;
        }
        _unlock();
        return write_lock_holder_preempted;
    }


    @Override
    protected void internal_first_phase_commit_speculative(
        SpeculativeFuture sf)
    {
        // FIXME: check this logic
        ActiveEvent active_event = sf.event;
        Future<Boolean> bool = internal_first_phase_commit(active_event);
        ralph_globals.thread_pool.add_service_action(
            new LinkFutureBooleans(bool,sf));
    }

    // FIXME: must override acquire and release locks in case switch
    // fails.
}