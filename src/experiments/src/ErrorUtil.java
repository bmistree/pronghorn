package experiments;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import ralph.RalphGlobals;
import ralph.Variables.NonAtomicTextVariable;

import RalphExtended.ExtendedHardwareOverrides;
import RalphExtended.IHardwareChangeApplier;

import RalphVersions.IVersionListener;

import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FTableUpdate;
import pronghorn.ft_ops.FlowTableToHardware;
import pronghorn.ft_ops.DeltaListStateSupplier;
import pronghorn.switch_factory.InternalPronghornSwitchGuard;
import pronghorn.switch_factory.SwitchSpeculateListener;
import pronghorn.SwitchJava._InternalSwitch;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.SwitchDeltaJava.SwitchDelta;

import experiments.IErrorApplicationJava.IErrorApplication;


public class ErrorUtil
{
    // gives time to settle changes
    private static final int SLEEP_TIME_BETWEEN_TESTS_MS = 500;
    private static final Random rand = new Random();
    private static final int MAX_NUM_OPS_BEFORE_CHECK = 20;
    /**
       Perform:
          BASE_NUM_OPS_BEFORE_CHECK +
          rand(0,RANDOM_NUM_ADDITIONAL_OPS_BEFORE_CHECK)
       for each run.
     */
    private static final int BASE_NUM_OPS_BEFORE_CHECK = 100;
    private static final int RANDOM_NUM_ADDITIONAL_OPS_BEFORE_CHECK = 20;

    
    /**
       extra debugging flag: something for us to watch out for in case
       we had an exception.
    */
    final public static AtomicBoolean had_exception =
        new AtomicBoolean(false);

    public static List<Boolean> run_operations(
        int num_ops_to_run, IErrorApplication error_app,
        String faulty_switch_id,List<String> switch_id_list)
    {
        List<Boolean> results_list = new ArrayList<Boolean>();
        /*
          Successively add and remove flow table entries, even in the
          face of errors.
         */
        for (int i = 0; i < num_ops_to_run; ++i)
        {
            try
            {
                Thread.sleep(SLEEP_TIME_BETWEEN_TESTS_MS);
            }
            catch(InterruptedException ex)
            {
                ex.printStackTrace();
                assert(false);
            }
            
            // perform random number of operations
            int num_ops_to_perform =
                BASE_NUM_OPS_BEFORE_CHECK + 
                rand.nextInt(RANDOM_NUM_ADDITIONAL_OPS_BEFORE_CHECK);

            for (int j = 0; j < num_ops_to_perform; ++j)
            {
                try
                {
                    if ((j % 2) == 0)
                        error_app.block_traffic_all_switches();
                    else
                        error_app.remove_first_entry_all_switches();
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                    return results_list;
                }
            }

            int num_switches = switch_id_list.size();
            boolean logical_correct =
                check_logical_state(
                    switch_id_list,error_app,num_ops_to_perform,
                    faulty_switch_id);
            boolean physical_correct =
                check_physical_state(
                    num_ops_to_perform,
                    // subtracting 1 from total number of switches
                    // because one switch is the simulated faulty
                    // software switch
                    num_switches -1);


            boolean success = logical_correct && physical_correct;
            results_list.add(success);
            if ((i %5) == 0)
                System.out.println("Finished " + i + " with result " + success);

            // clear hardware
            clear_hardware(num_switches -1);

            // logical clear switches
            logical_clear_switches(error_app,switch_id_list);
        }
        return results_list;
    }

    public static void add_faulty_switch(
        RalphGlobals ralph_globals,String faulty_switch_id, boolean should_speculate,
        float failure_probability, Instance prong,
        IVersionListener ft_version_listener,IVersionListener port_version_listener)
    {
        try
        {
            _InternalSwitch internal_switch =
                create_faulty_switch(
                    ralph_globals,faulty_switch_id,should_speculate,
                    failure_probability,ft_version_listener,
                    port_version_listener);
            prong.add_switch(internal_switch);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            assert(false);
        }
    }
        

    

    /**
       Translate true-s to 1-s and false-s to 0-s.
     */
    public static void write_results(
        String output_filename, List<Boolean> results_list)
    {
        StringBuffer results_buffer = new StringBuffer();
        for (Boolean trial_result : results_list)
        {
            if (trial_result.booleanValue())
                results_buffer.append("1,");
            else
                results_buffer.append("0,");
        }
        Util.write_results_to_file(output_filename,results_buffer.toString());
    }
    
    private static boolean check_logical_state(
        List<String> switch_id_list,IErrorApplication error_app,
        int num_ops_to_perform, String faulty_switch_to_skip)
    {
        // now, check that all the switches have the correct
        // number of rules on them, flush hardware, and flush
        // logical rules.
        boolean expected_number_rules_zero =
            (num_ops_to_perform %2) == 0;
        boolean all_expected = true;
        for (String pronghorn_switch_id : switch_id_list)
        {
            // ignore faulty switch id, because we aren't cleaning it
            // up after it fails.  Here, we're more concerned that the
            // other switches stay in sync, despite error.
            if (pronghorn_switch_id.equals(faulty_switch_to_skip))
                continue;
            try
            {
                int num_flow_table_entries =
                    error_app.num_flow_table_entries(pronghorn_switch_id).intValue();
                if (expected_number_rules_zero)
                {
                    if (num_flow_table_entries != 0)
                        all_expected = false;
                }
                else
                {
                    if (num_flow_table_entries != 1)
                        all_expected = false;
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                assert(false);
            }
        }
        return all_expected;
    }
    
    /**
       Run through all the switches and check that they have the
       expected number of flow table entries on them.

       @param {int} num_physical_switches --- Should be the total
       number of switches in the system, minus one (the one that's
       used to force errors).
     */
    private static boolean check_physical_state(
        int num_ops_performed, int num_physical_switches)
    {
        int expected_number_of_entries = 0;
        if ((num_ops_performed %2) == 1)
            expected_number_of_entries = 1;

        List<String> ovs_switch_names =
            Util.produce_ovs_switch_names(num_physical_switches);

        for (String ovs_switch_name : ovs_switch_names)
        {
            int flow_tab_entries =
                Util.ovs_hardware_flow_table_size(ovs_switch_name);
            if (flow_tab_entries != expected_number_of_entries)
                return false;
        }
        return true;
    }

    
    private static void clear_hardware(int num_physical_switches)
    {
        List<String> ovs_switch_names =
            Util.produce_ovs_switch_names(num_physical_switches);

        for (String ovs_switch_name : ovs_switch_names)
            Util.ovs_clear_flows_hardware (ovs_switch_name);
    }


    
    private static void logical_clear_switches(
        IErrorApplication error_app,List<String> switch_list)
    {
        for (String switch_id : switch_list)
        {
            try
            {
                error_app.logical_clear_switch_do_not_flush_clear_to_hardware(switch_id);
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                assert(false);
            }
        }
    }

    
    
    static _InternalSwitch create_faulty_switch(
        RalphGlobals ralph_globals,String new_switch_id, boolean speculate,
        float error_probability, IVersionListener ft_version_listener,
        IVersionListener port_version_listener)
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
                switch_speculate_listener, ft_version_listener);
        to_handle_pushing_changes.set_hardware_overrides(
            faulty_switch_guard_num_var.extended_hardware_overrides);

        internal_switch_delta.switch_lock = faulty_switch_guard_num_var;

        // do not override port change guard.  this is because faulty
        // switch is only faulty for flow table.
        
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