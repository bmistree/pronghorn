package pronghorn;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ralph.RalphObject;

import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;


/**
   See top of InternalPronghornSwitchGuard for more.
   
   Corresponding states:

     CLEAN --- No outstanding changes on hardware.

     PUSHING_CHANGES --- Transition into this state from a
     hardwrae_first_phase_commit_hook. State means that we are in the
     midst of pushing first phase commits to hardware and/or waiting
     on barrier that they have completed.  If barrier times out or get
     an error, transition into failed state.  Note: 1) If just got
     errors on operations, could retry those operations with barriers.
     Currently, do not and just go to failed.  2) Need to deal with
     failed state, creating super priority that removes state.

     STAGED_CHANGES --- Dirty state is on hardware and hardware has
     acknowledged this with a barrier response and no intervening
     error messages.  If commit succeeds (get
     hardware_complete_commit_hook), go ahead and go back to clean.

     REMOVING_CHANGES --- Enters when in STAGED_CHANGES state and
     receive an event from hardware_backout_hook.  Can transition to
     Failed if undos it issues error out or barrier times out.  (Same
     FIXME-s as PUSHING_CHANGES state.)  Transitions to CLEAN if all
     changes are removed.  Does not return until they are all removed.

     FAILED --- Switch must be reset by an admin.



   +--------------+        +---------------+
   |              |        |               |
   |              |        | PUSHING       |
   |    CLEAN     | -----> |   CHANGES     |---------
   |              |        |               |         |
   +--------------+        +---------------+         |
        ^     ^                        |             |
        |     |                        |             |
        |      ----------------        |             |
        |                      |       v             |
   +--------------+        +---------------+         |
   |              |        |               |         |
   |  REMOVING    | <----- | STAGED        |         |
   |    CHANGES   |        |   CHANGES     |         |
   |              |        |               |         |
   +--------------+        +---------------+         |
             |                                       |
             |                                       |
             v                                       |
      +------------+                                 |
      |            |                                 |
      |  FAILED    | <--------------------------------
      |            |
      +------------+
 */
public class SwitchGuardState
{
    protected static final Logger log =
        LoggerFactory.getLogger(SwitchGuardState.class);

    
    /**
       See comments and state diagram above.
     */
    public enum State
    {
        CLEAN,
        PUSHING_CHANGES,
        STAGED_CHANGES,
        REMOVING_CHANGES,
        FAILED
    }
    private State switch_guard_state = State.CLEAN;
    private final ReentrantLock rlock = new ReentrantLock();
    private final Condition cond = rlock.newCondition();
    private List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
        dirty_on_hardware = null;
    
    public State get_state()
    {
        State to_return = null;
        rlock.lock();
        to_return = switch_guard_state;
        rlock.unlock();
        return to_return;
    }

    /**
       All moves are called while holding lock.
     */
    public void move_state_clean()
    {
        switch_guard_state = State.CLEAN;
        dirty_on_hardware = null;
        cond.signalAll();
    }


    /**
       Called from within lock.
     */
    public List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>
        get_dirty_on_hardware()
    {
        return dirty_on_hardware;
    }
    

    public void move_state_pushing_changes(
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>> new_dirty_on_hardware)
    {
        //// DEBUG
        if (dirty_on_hardware != null)
        {
            log.error(
                "Should never move to state pushing changes when already " +
                "have outstanding changes on hardware.");
            assert(false);
        }
        //// END DEBUG
        
        dirty_on_hardware = new_dirty_on_hardware;
        
        // not providing a wait_pushing method, therefore, do not have
        // to signal.
        switch_guard_state = State.PUSHING_CHANGES;
    }
    public void move_state_staged_changes()
    {
        switch_guard_state = State.STAGED_CHANGES;
        cond.signalAll();
    }

    public void move_state_removing_changes()
    {
        // not providing a wait_removing_changes method, therefore, do
        // not have to signal.
        switch_guard_state = State.REMOVING_CHANGES;
    }

    public void move_state_failed()
    {
        switch_guard_state = State.FAILED;
        cond.signalAll();
    }
    
    
    /**
       @see wait_on_states_while_holding_lock_returns_holding_lock for
       state STAGED_CHANGES and state FAILED.
     */
    public State wait_staged_or_failed_state_while_holding_lock_returns_holding_lock()
    {
        return wait_on_states_while_holding_lock_returns_holding_lock(
            State.STAGED_CHANGES,State.FAILED);
    }
    
    /**
       @see wait_on_states_while_holding_lock_returns_holding_lock for
       state CLEAN and state FAILED.
     */
    public State wait_clean_or_failed_state_while_holding_lock_returns_holding_lock()
    {
        return wait_on_states_while_holding_lock_returns_holding_lock(
            State.CLEAN,State.FAILED);
    }

    /**
       Called while holding lock on state.
       
       Returns after switch_guard_state transitions to one of two
       states.  When returns, still holding lock on state, and caller
       must unlock.
     */
    private State wait_on_states_while_holding_lock_returns_holding_lock(
        State state_1, State state_2)
    {
        while ((switch_guard_state != state_1) &&
               (switch_guard_state != state_2))
        {
            try
            {
                cond.await();
            }
            catch (InterruptedException ex)
            {
                log.error(
                    "Interrupts not allowed in wait_on_states",ex);
                assert(false);
            }
        }
        return switch_guard_state;        
    }
        
    public State get_state_hold_lock()
    {
        rlock.lock();
        return switch_guard_state;
    }

    public void release_lock()
    {
        rlock.unlock();
    }
}