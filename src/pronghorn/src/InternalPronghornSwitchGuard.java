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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
   Interfaces to Ralph runtime:

     hardware_first_phase_commit_hook
       Returns a future.  This future can be cancelled by ralph
       runtime, while hardware is still pushing changes.  In this
       case, guaranteed to get a call to hardware_backout_hook.  That
       call should block until all changes have been removed from
       hardware.

       It should be impossible to get another
       hardware_first_phase_commit_hook while we are backing out.
       Adding an assert to ensure.


     hardware_complete_commit_hook
       Changes that have been pushed can be released.  Only called
       after hardware_first_phase_commit_hook.

     hardware_backout_hook
     
       Backout changes that may have been made to a hardware element.
       Only return after barrier response that changes have completely
       been removed from hardware.  Note: this may be called even when
       no changes have been pushed to hardware (eg., if event has been
       preempted on object).  In these cases, nothing to undo.

     hardware_first_phase_commit_speculative_hook

     See SwitchGuardState.java for more about state transitions.
 */
public class InternalPronghornSwitchGuard extends AtomicNumberVariable
{    
    public final String ralph_internal_switch_id;
    private final _InternalSwitchDelta switch_delta;
    private final _InternalSwitch internal_switch;
    private final FlowTableToHardware to_handle_pushing_changes;
    private final ExecutorService hardware_push_service;
    private final boolean should_speculate;
    private final SwitchGuardState switch_guard_state = new SwitchGuardState();
    
    protected static final Logger log =
        LoggerFactory.getLogger(InternalPronghornSwitchGuard.class);

    
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
       Called from within lock.  Only gets called on non-derivative
       objects.

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
        try
        {
            SwitchGuardState.State current_state = switch_guard_state.get_state_hold_lock();
            //// DEBUG
            if (current_state != SwitchGuardState.State.CLEAN)
            {
                // FIXME: Maybe should also be able to get here from
                // FAILED state, but should return false.
                log.error(
                    "Should only recieve first phase commit request when in clean state");
                assert(false);
            }
            //// END DEBUG

            
            // do not need to take locks here because know that this
            // method will only be called from AtomicActiveEvent
            // during first_phase_commit. Because AtomicActiveEvent is
            // in midst of commit, know that these values cannot
            // change.

            // FIXME: may need to grab latest speculative value, rather
            // than current pushing to hardware.  May be fixed by passing
            // null in to speculate.  Should check.

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
                if (is_write_lock_holder(active_event))
                {
                    to_push = internal_ft_deltas_list.dirty_val.val;
                }
            }


            // FIXME: What if this is on top of a speculated value.
            // Doesn't that mean that write_lock_holder might be
            // incorrect/pointing to incorrect value?
            if (is_write_lock_holder(active_event))
            {
                // check if we're just supposed to be simulating changes.
                if (to_handle_pushing_changes == null)
                    return null;

                switch_guard_state.move_state_pushing_changes(to_push);
                WrapApplyToHardware to_apply_to_hardware =
                    new WrapApplyToHardware(
                        to_handle_pushing_changes,
                        switch_guard_state.get_dirty_on_hardware(),false,
                        switch_guard_state);

                hardware_push_service.execute(to_apply_to_hardware);

                // note: do not need to move state transition here
                // ourselves.  to_apply_to_hardware does that for us.
                return to_apply_to_hardware.to_notify_when_complete;
             }

            // it's a read operation. never made a write to this variable:
            // do not need to ensure that hardware is up (for now).  May
            // want to add read checks as well.
            return ALWAYS_TRUE_FUTURE;
        }
        finally
        {
            switch_guard_state.release_lock();
        }
    }

    @Override
    protected void hardware_complete_commit_hook(ActiveEvent active_event)
    {
        boolean write_lock_holder_being_completed = is_write_lock_holder(active_event);
        if (write_lock_holder_being_completed)
        {
            try
            {
                SwitchGuardState.State current_state = switch_guard_state.get_state_hold_lock();
                //// DEBUG
                if ((current_state != SwitchGuardState.State.STAGED_CHANGES) &&
                    (current_state != SwitchGuardState.State.PUSHING_CHANGES))
                {
                    // FIXME: handle failed state.                    
                    log.error("Cannot complete from failed state or clean state.");
                    assert(false);
                }
                //// END DEBUG

                if (current_state == SwitchGuardState.State.PUSHING_CHANGES)
                {
                    current_state =
                        switch_guard_state.wait_staged_or_failed_state_while_holding_lock_returns_holding_lock();
                }
                
                if (current_state == SwitchGuardState.State.FAILED)
                {
                    // FIXME: Handle being in a failed state.
                    log.error("Handle failed state");
                    assert(false);
                }

                switch_guard_state.move_state_clean();
            }
            finally
            {
                switch_guard_state.release_lock();
            }
        }
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
            SwitchGuardState.State current_state = switch_guard_state.get_state_hold_lock();

            // Get backout requests even when runtime has not
            // requested changes to be staged on hardware (eg., if
            // event has been preempted on object).  In these cases,
            // nothing to undo.  Just stay in clean state.
            if (current_state == SwitchGuardState.State.CLEAN)
            {
                switch_guard_state.release_lock();
                return;
            }
            
            //// DEBUG
            if ((current_state != SwitchGuardState.State.PUSHING_CHANGES) &&
                (current_state != SwitchGuardState.State.STAGED_CHANGES))
            {
                // FIXME: Handle failed state.
                log.error(
                    "Unexpected state when requesting backout " +
                    current_state);
                assert(false);
            }
            //// END DEBUG

            if (current_state == SwitchGuardState.State.PUSHING_CHANGES)
            {
                current_state =
                    switch_guard_state.wait_staged_or_failed_state_while_holding_lock_returns_holding_lock();
            }

            if (current_state == SwitchGuardState.State.FAILED)
            {
                // FIXME: Must handle being in a failed state.
                log.error("Handle failed state");
                assert(false);
            }

            WrapApplyToHardware to_undo_wrapper =
                new WrapApplyToHardware(
                    to_handle_pushing_changes,
                    switch_guard_state.get_dirty_on_hardware(),
                    true,switch_guard_state);
            hardware_push_service.execute(to_undo_wrapper);
                
            // transitions synchronously from removing changes to clean.
            switch_guard_state.move_state_removing_changes();
            switch_guard_state.release_lock();
                
            to_undo_wrapper.to_notify_when_complete.get();
                
            // do not need to explicitly transition to clean here;
            // apply to hardware should for us.;


            // FIXME: should check what to do if failed though.
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