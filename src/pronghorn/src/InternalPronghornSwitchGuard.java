package pronghorn;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.ArrayList;
import java.util.List;

import ralph.Variables.AtomicNumberVariable;
import ralph.AtomicInternalList;
import ralph.Variables.AtomicListVariable;
import ralph.RalphGlobals;
import ralph.ActiveEvent;
import ralph.RalphObject;
import ralph.ICancellableFuture;
import ralph.SpeculativeFuture;
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
    
    private List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
        dirty_on_hardware = null;
    
    
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


    /**
       Called from within lock.

       @returns --- Can be null, eg., if the object is not backed by
       hardware.  Otherwise, call to get on future returns true if if
       can commit in first phase, false otherwise.
     */
    @Override
    protected ICancellableFuture hardware_first_phase_commit_hook(
        ActiveEvent active_event)
    {
        // FIXME: Check next paragraphs now that updated method to be
        // called from within lock.
        
        
        // do not need to take locks here because know that this
        // method will only be called from AtomicActiveEvent
        // during first_phase_commit. Because AtomicActiveEvent is
        // in midst of commit, know that these values cannot
        // change.

        // regardless of whether we are a reader or a writer, we need
        // these values so that we can speculate on them.
        AtomicInternalList<_InternalFlowTableEntry,_InternalFlowTableEntry>
            internal_ft_list = get_internal_ft_list();
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = get_internal_ft_deltas_list(); 

        ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
            to_push = null;
        
        if (should_speculate)
        {
            internal_ft_list.speculate(active_event,null);
            to_push = internal_ft_deltas_list.speculate(
                active_event,
                // so that resets delta list
                new ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>());
            
            speculate(active_event,null);
        }
        else
        {
            if ((write_lock_holder != null) && 
                (active_event.uuid.equals(write_lock_holder.event.uuid)))
            {
                to_push = internal_ft_deltas_list.dirty_val.val;
            }
        }


        // FIXME: What if this is on top of a speculated value.
        // Doesn't that mean that write_lock_holder might be
        // incorrect/pointing to incorrect value?
        if ((write_lock_holder != null) && 
            (active_event.uuid.equals(write_lock_holder.event.uuid)))
        {
            // check if we're just supposed to be simulating changes.
            if (to_handle_pushing_changes == null)
                return null;
            
            dirty_on_hardware = to_push;

            WrapApplyToHardware to_apply_to_hardware =
                new WrapApplyToHardware(
                    to_handle_pushing_changes, dirty_on_hardware,false);

            hardware_push_service.execute(to_apply_to_hardware);

            // FIXME: Previous version supposed that future returned
            // would never be cancelled, but only set in switch
            // guard/flowtabletohardware.  Need to update to handle
            // case where can get backed out before all changes have
            // been pushed to hardware.
            
            // return to_apply_to_hardware.to_notify_when_complete;
            assert(false);
         }


        // it's a read operation. never made a write to this variable:
        // do not need to ensure that hardware is up (for now).  May
        // want to add read checks as well.
        return ALWAYS_TRUE_FUTURE;
    }                

    @Override
    protected void hardware_complete_commit_hook(ActiveEvent active_event)
    {
        boolean write_lock_holder_being_completed = is_write_lock_holder(active_event);
        if (write_lock_holder_being_completed)
            dirty_on_hardware = null;
    }

    /**
       Called from within lock.
     */
    @Override
    protected void hardware_backout_hook(ActiveEvent active_event)
    {
        boolean write_lock_holder_being_preempted = is_write_lock_holder(active_event);
        if (write_lock_holder_being_preempted)
        {
            // if there were dirty values that we had pushed to the
            // hardware, we need to undo them.
            if (dirty_on_hardware != null)
            {
                WrapApplyToHardware to_undo_wrapper =
                    new WrapApplyToHardware(
                        to_handle_pushing_changes,dirty_on_hardware,
                        true);
                hardware_push_service.execute(to_undo_wrapper);
                to_undo_wrapper.to_notify_when_complete.get();
            }
            dirty_on_hardware = null;
        }
    }

    /**
       Called from within lock.

       When a derived object gets promoted to root object, we need
       to deal with any events that began committing to the object
       when it was a derived object.  In our case, we take the
       changes associated with the speculative future and apply
       them to hardware.  We link this speculative future with a
       new future that pushes to hardware.
     */
    @Override
    protected boolean hardware_first_phase_commit_speculative_hook(
        SpeculativeFuture sf)
    {
        ActiveEvent active_event = sf.event;
        Future<Boolean> bool = hardware_first_phase_commit_hook(active_event);
        ralph_globals.thread_pool.add_service_action(
            new LinkFutureBooleans(bool,sf));

        return true;
    }
    
    // FIXME: must override acquire and release locks in case switch
    // fails.
}