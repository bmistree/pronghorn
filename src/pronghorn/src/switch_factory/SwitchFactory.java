package pronghorn.switch_factory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import ralph.RalphGlobals;

import pronghorn.FTable;
import pronghorn.FTable._InternalFlowTableEntry;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.SwitchDeltaJava.SwitchDelta;

import pronghorn.DeltaListStateSupplier;
import pronghorn.StatisticsUpdater;
import pronghorn.FlowTableToHardware;
import pronghorn.IFloodlightShim;

public class SwitchFactory implements ISwitchFactory
{
    /**
       Each factory has a unique prefix that it can use to generate
       ids for each switch.  Format of prefix can be:
          a
          a:b
          a:b:c
          
       etc.  Importantly, should not contain any
       SWITCH_PREFIX_TO_ID_SEPARATORs, because will append
       SWITCH_PREFIX_TO_ID_SEPARATOR<int> to end of the id to generate
       unique switch ids.
     */
    private String factory_switch_prefix;
    private AtomicInteger atomic_switch_id = new AtomicInteger(0);
    private final static String SWITCH_PREFIX_TO_ID_SEPARATOR = ".";


    /**
       Use a separate thread for each switch to send message to switch
       requesting it to apply some change (and receive response).
       These threads are pulled in the executor service
       hardware_pushing_service.
     */
    private final static int PUSH_CHANGE_TO_HARDWARE_THREAD_POOL_SIZE = 150;
    private final static ExecutorService hardware_pushing_service = 
        Executors.newFixedThreadPool(
            PUSH_CHANGE_TO_HARDWARE_THREAD_POOL_SIZE,
            new ThreadFactory()
            {
                // each thread created is a daemon
                public Thread newThread(Runnable r)
                {
                    Thread t=new Thread(r);
                    t.setDaemon(true);
                    return t;
                }
            });

    private RalphGlobals ralph_globals = null;
    private final boolean speculate;
    /**
       @param {int} _collect_statistics_period_ms.  If period < 0, then
       never collect statistics.
     */
    private final int collect_statistics_period_ms;

    public SwitchFactory(
        RalphGlobals _ralph_globals,boolean _speculate,
        int _collect_statistics_period_ms)
    {
        ralph_globals = _ralph_globals;
        speculate = _speculate;
        collect_statistics_period_ms = _collect_statistics_period_ms;
    }

    /**
       @param _factory_switch_prefix --- @see doc for
       factory_switch_prefix.
     */
    @Override
    public void init(String _factory_switch_prefix)
    {
        factory_switch_prefix = _factory_switch_prefix;
    }

    /**
       Do not actually push changes to flow table to hardware, just
       say that we did.
     */
    @Override
    public PronghornInternalSwitch construct(
        double available_capacity,StatisticsUpdater stats_updater)
    {
        return construct(available_capacity,null,null,"",stats_updater);
    }

    /**
       @param to_handle_pushing_changes --- Can be null, in which
       case, will simulate pushing changes to hardware.  (Ie., will
       always return message that says it did push changes to hardware
       without doing any work.)  
     */
    @Override
    public PronghornInternalSwitch construct(
        double available_capacity,
        FlowTableToHardware to_handle_pushing_changes,
        IFloodlightShim shim, String floodlight_switch_id,
        StatisticsUpdater stats_updater)
    {
        // create a new switch id
        Integer factory_local_unique_id = atomic_switch_id.addAndGet(1);
        String new_switch_id =
            factory_switch_prefix + SWITCH_PREFIX_TO_ID_SEPARATOR +
            factory_local_unique_id.toString();

        // Create the switch delta struct
        _InternalSwitchDelta switch_delta =
            new _InternalSwitchDelta(ralph_globals);

        // Create the new switch and return it
        PronghornInternalSwitch internal_switch =
            new PronghornInternalSwitch(
                ralph_globals,new_switch_id,available_capacity,
                switch_delta,collect_statistics_period_ms,shim,
                floodlight_switch_id,stats_updater);

        SwitchSpeculateListener switch_speculate_listener =
            new SwitchSpeculateListener();
        
        // override switch_lock variable: switch_lock variable serves
        // as a guard for both port_deltas and ft_deltas.
        InternalPronghornSwitchGuard switch_guard_num_var =
            new InternalPronghornSwitchGuard(
                ralph_globals,new_switch_id,speculate,
                to_handle_pushing_changes,
                new DeltaListStateSupplier(switch_delta),
                switch_speculate_listener);

        switch_delta.switch_lock = switch_guard_num_var;
        switch_speculate_listener.init(
            switch_delta,internal_switch,switch_guard_num_var);
        
        return internal_switch;
    }    

}