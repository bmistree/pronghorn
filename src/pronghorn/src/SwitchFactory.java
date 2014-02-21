package pronghorn;

import pronghorn.RTable;
import pronghorn.RTable._InternalRoutingTableEntry;
import pronghorn.SwitchJava._InternalSwitch;
import ralph.Variables.AtomicTextVariable;
import ralph.Variables.AtomicNumberVariable;
import ralph.Variables.AtomicListVariable;
import ralph.RalphGlobals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


public class SwitchFactory
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

    public SwitchFactory(RalphGlobals _ralph_globals)
    {
        ralph_globals = _ralph_globals;
    }

    /**
       @param _factory_switch_prefix --- @see doc for
       factory_switch_prefix.
     */
    public void init(String _factory_switch_prefix)
    {
        factory_switch_prefix = _factory_switch_prefix;
    }

    /**
       Do not actually push changes to routing table to hardware, just
       say that we did.
     */
    public PronghornInternalSwitch construct(
        double available_capacity)
    {
        return construct(available_capacity,null);
    }

    /**
       @param to_handle_pushing_changes --- Can be null, in which
       case, will simulate pushing changes to hardware.  (Ie., will
       always return message that says it did push changes to hardware
       without doing any work.)  
     */
    public PronghornInternalSwitch construct(
        double available_capacity,
        RoutingTableToHardware to_handle_pushing_changes)
    {
        // create a new switch id
        Integer factory_local_unique_id = atomic_switch_id.addAndGet(1);
        String new_switch_id =
            factory_switch_prefix + SWITCH_PREFIX_TO_ID_SEPARATOR +
            factory_local_unique_id.toString();
                
        PronghornInternalSwitch to_return =
            new PronghornInternalSwitch(new_switch_id,ralph_globals);

        // override internal routing table variable
        InternalRoutingTableList internal_rtable_list = null;
        if (to_handle_pushing_changes == null)
        {
            internal_rtable_list =
                new InternalRoutingTableList(
                    hardware_pushing_service,ralph_globals);
        }
        else
        {
            internal_rtable_list =
                new InternalRoutingTableList(
                    to_handle_pushing_changes,hardware_pushing_service,
                    ralph_globals);
        }
        
        to_return.rtable =
            new AtomicListVariable<_InternalRoutingTableEntry,_InternalRoutingTableEntry>(
                false,internal_rtable_list,
                RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry,
                ralph_globals);
        
        // produce and overwrite a switch id associated with this switch
        to_return.switch_id =
            new AtomicTextVariable(false,new_switch_id,ralph_globals);

        // set available capacity
        to_return.available_capacity =
            new AtomicNumberVariable(false,available_capacity,ralph_globals);
        
        return to_return;
    }

    public class PronghornInternalSwitch extends _InternalSwitch
    {
        public String ralph_internal_switch_id;
        public PronghornInternalSwitch(
            String _ralph_internal_switch_id,RalphGlobals ralph_globals)
        {
            super(ralph_globals);
            ralph_internal_switch_id = _ralph_internal_switch_id;
        }
    }
}