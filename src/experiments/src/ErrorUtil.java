package experiments;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import RalphExtended.ExtendedHardwareOverrides;
import RalphExtended.IHardwareChangeApplier;

import pronghorn.FTableUpdate;

public class ErrorUtil
{
    /**
       extra debugging flag: something for us to watch out for in case
       we had an exception.
    */
    final public static AtomicBoolean had_exception =
        new AtomicBoolean(false);


    public static class FaultyHardwareChangeApplier
        implements IHardwareChangeApplier<List<FTableUpdate>>, Runnable
    {
        private final ExtendedHardwareOverrides<List<FTableUpdate>> hardware_overrides;
        private final float error_probability;

        private final static Random rand = new Random();
        
        /**
           When switch fails, wait this much time before restoring it.
         */
        private final static int RESET_SLEEP_WAIT_MS = 50;
        
        public FaultyHardwareChangeApplier(
            ExtendedHardwareOverrides<List<FTableUpdate>> _hardware_overrides,
            float _error_probability)
        {
            hardware_overrides = _hardware_overrides;
            error_probability = _error_probability;
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