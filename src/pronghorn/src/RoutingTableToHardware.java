package pronghorn;

import RalphDataWrappers.ListTypeDataWrapper;
import pronghorn.RTable._InternalRoutingTableEntry;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;

/**
   Subclass this object to override behavior of internal list when
   it is asked to push changes to hardware or undo pushed changes
   to hardware.
 */
public class RoutingTableToHardware
{
    public boolean apply_changes_to_hardware(
        ListTypeDataWrapper<
            _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
    {
        return true;
    }
    public void undo_dirty_changes_to_hardware(
        ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry>
        to_undo)
    { }


    public static class WrapApplyToHardware implements Runnable
    {
        public ApplyToHardwareFuture to_notify_when_complete =
            new ApplyToHardwareFuture();
        private RoutingTableToHardware rtable_to_hardware = null;
        private
            ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry>
            to_apply = null;
        
        public WrapApplyToHardware(
            RoutingTableToHardware _rtable_to_hardware,
            ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry> _to_apply)
        {
            rtable_to_hardware = _rtable_to_hardware;
            to_apply = _to_apply;
        }

        @Override
        public void run()
        {
            boolean application_successful =
                rtable_to_hardware.apply_changes_to_hardware(to_apply);
            to_notify_when_complete.set(application_successful);
        }
    }


    private static class ApplyToHardwareFuture implements Future<Boolean>
    {
        private final ReentrantLock rlock = new ReentrantLock();
        private final Condition cond = rlock.newCondition();
        
        private boolean result_is_set = false;
        private boolean result;

        /**
           Should only be called from WrapApply class.
         */
        public void set(boolean to_set_to)
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


