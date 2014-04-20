package experiments;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import ralph.RalphGlobals;
import ralph.Variables.NonAtomicTextVariable;

import RalphExtended.ExtendedHardwareOverrides;
import RalphExtended.IHardwareChangeApplier;

import pronghorn.FTableUpdate;
import pronghorn.FlowTableToHardware;
import pronghorn.DeltaListStateSupplier;
import pronghorn.InternalPronghornSwitchGuard;
import pronghorn.SwitchSpeculateListener;
import pronghorn.SwitchJava._InternalSwitch;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.SwitchDeltaJava.SwitchDelta;


public class ErrorUtil
{
    /**
       extra debugging flag: something for us to watch out for in case
       we had an exception.
    */
    final public static AtomicBoolean had_exception =
        new AtomicBoolean(false);


    static _InternalSwitch create_faulty_switch(
        RalphGlobals ralph_globals,String new_switch_id, boolean speculate,
        float error_probability)
    {
        _InternalSwitch internal_switch = new _InternalSwitch(ralph_globals);
        FaultyHardwareChangeApplier to_handle_pushing_changes =
            new FaultyHardwareChangeApplier(error_probability);
        SwitchSpeculateListener switch_speculate_listener =
            new SwitchSpeculateListener();
        
        _InternalSwitchDelta internal_switch_delta =
            new _InternalSwitchDelta(ralph_globals);
        SwitchDelta switch_delta =
            new SwitchDelta (
                false,internal_switch_delta,ralph_globals);
        
        // override switch_lock variable: switch_lock variable serves
        // as a guard for both port_deltas and ft_deltas.
        InternalPronghornSwitchGuard faulty_switch_guard_num_var =
            new InternalPronghornSwitchGuard(
                ralph_globals,new_switch_id,speculate,
                to_handle_pushing_changes,
                // gets internal switch delta.  note this is safe, but
                // ugly.
                new DeltaListStateSupplier(internal_switch_delta),
                switch_speculate_listener);
        to_handle_pushing_changes.set_hardware_overrides(
            faulty_switch_guard_num_var.extended_hardware_overrides);

        internal_switch_delta.switch_lock = faulty_switch_guard_num_var;
        internal_switch.delta = switch_delta;

        try
        {
            internal_switch.switch_id = 
                new NonAtomicTextVariable(false,new_switch_id,ralph_globals);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            assert(false);
        }
                

        
        switch_speculate_listener.init(
            internal_switch_delta,internal_switch,faulty_switch_guard_num_var);
        return internal_switch;
    }
    

    private static class FaultyHardwareChangeApplier
        extends FlowTableToHardware implements Runnable
    {
        private ExtendedHardwareOverrides<List<FTableUpdate>> hardware_overrides = null;
        private final float error_probability;

        private final static Random rand = new Random();
        
        /**
           When switch fails, wait this much time before restoring it.
         */
        private final static int RESET_SLEEP_WAIT_MS = 50;
        
        public FaultyHardwareChangeApplier(float _error_probability)
        {
            error_probability = _error_probability;
        }

        public void set_hardware_overrides(
            ExtendedHardwareOverrides<List<FTableUpdate>> _hardware_overrides)
        {
            hardware_overrides = _hardware_overrides;
        }

        /** Runnable interface overrides*/
        
        /**
           When switch fails applying state, we start a thread that
           waits briefly and then resets faulty switch's state
           controller so that can keep trying.
         */
        @Override
        public void run()
        {
            try
            {
                Thread.sleep(RESET_SLEEP_WAIT_MS);
            }
            catch(InterruptedException ex)
            {
                // Error: should never have received an interrupted
                // exception.  Set had_exception flag to true so that
                // can re-report error.
                ex.printStackTrace();
                had_exception.set(true);
            }
            hardware_overrides.force_reset_clean();
        }
        
        /** IHardwareChangeApplier interface methods*/
        @Override
        public boolean apply(List<FTableUpdate> to_apply)
        {
            // fail apply to switch error_probability of the time.
            if (rand.nextFloat() < error_probability)
            {
                // start thread to clean up switch so that will be in
                // clean state for next events.
                Thread t = new Thread(this);
                t.start();
                return false;
            }
            return true;
        }

        @Override
        public boolean undo(List<FTableUpdate> to_undo)
        {
            // always succeed on undo
            return true;
        }
    }
}