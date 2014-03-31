package pronghorn;

import RalphDataWrappers.ListTypeDataWrapper;
import pronghorn.FTable._InternalFlowTableEntry;
import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;

import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;
import java.util.List;

import ralph.RalphObject;
import ralph.ICancellableFuture;
import RalphExtended.IHardwareChangeApplier;

/**
   Subclass this object to override behavior of internal list when
   it is asked to push changes to hardware or undo pushed changes
   to hardware.
 */
public abstract class FlowTableToHardware
    implements IHardwareChangeApplier<
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>>
{
    @Override
    public abstract boolean apply(
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
        dirty);
    @Override    
    public abstract boolean undo(
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
        to_undo);

    public static class WrapApplyToHardware implements Runnable
    {
        public final ApplyToHardwareFuture to_notify_when_complete =
            new ApplyToHardwareFuture();
        private FlowTableToHardware ftable_to_hardware = null;
        private List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
            to_apply = null;
        private final boolean undo_changes;
        private final SwitchGuardState switch_guard_state;

        /**
           Transition in switch_guard_state either to FAILED or
           STAGED_CHANGES (if not undoing changes) or CLEAN (if
           undoing changes)
         */
        public WrapApplyToHardware(
            FlowTableToHardware _ftable_to_hardware,
            List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>> _to_apply,
            boolean _undo_changes, SwitchGuardState _switch_guard_state)
        {
            ftable_to_hardware = _ftable_to_hardware;
            to_apply = _to_apply;
            undo_changes = _undo_changes;
            switch_guard_state = _switch_guard_state;
        }

        @Override
        public void run()
        {
            boolean application_successful = true;
            if (undo_changes)
                ftable_to_hardware.undo(to_apply);
            else
            {
                application_successful =
                    ftable_to_hardware.apply(to_apply);
            }

            switch_guard_state.get_state_hold_lock();
            if (! application_successful)
                switch_guard_state.move_state_failed();
            else if (undo_changes) // undo succeeded
                switch_guard_state.move_state_clean();
            else // apply succeeded
                switch_guard_state.move_state_staged_changes();
            switch_guard_state.release_lock();

            if (application_successful)
                to_notify_when_complete.succeeded();
            else
                to_notify_when_complete.failed();
        }
    }

    
    public static class ApplyToHardwareFuture implements ICancellableFuture
    {
        private final ReentrantLock rlock = new ReentrantLock();
        private final Condition cond = rlock.newCondition();
        
        private boolean result_is_set = false;
        private boolean result;

        @Override
        public void succeeded()
        {
            set(true);
        }
        @Override
        public void failed()
        {
            set(false);
        }
        
        private void set(boolean to_set_to)
        {
            rlock.lock();
            result = to_set_to;
            result_is_set = true;
            cond.signalAll();
            rlock.unlock();
        }

        @Override
        public Boolean get()
        {
            rlock.lock();
            while (! result_is_set)
            {
                try {
                    cond.await();
                } catch (InterruptedException _ex) {
                    /// FIXME: deal with interrupted exception
                    _ex.printStackTrace();
                    assert(false);
                }
            }
            rlock.unlock();
            return result;
        }
        
        @Override
        public Boolean get(long timeout, TimeUnit unit)
        {
            /// FIXME: fill in this stub method
            assert(false);
            return null;
        }

        @Override
        public boolean isCancelled()
        {
            /// FIXME: fill in this stub method
            assert(false);
            return false;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            /// FIXME: fill in this stub method
            assert(false);
            return false;
        }
        
        @Override
        public boolean isDone()
        {
            boolean to_return;
            rlock.lock();
            to_return = result_is_set;
            rlock.unlock();
            return to_return;
        }
    }
}


