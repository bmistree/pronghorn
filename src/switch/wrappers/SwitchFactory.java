package pronghorn;

import ralph.ExtendedVariables.ExtendedInternalAtomicList;
import pronghorn.RTable;
import pronghorn.RTable.RoutingTableEntry;
import pronghorn.RTable._InternalRoutingTableEntry;
import java.util.concurrent.atomic.AtomicInteger;
import pronghorn.SwitchJava._InternalSwitch;
import ralph.Variables.AtomicTextVariable;
import ralph.Variables.AtomicNumberVariable;
import ralph.Variables.AtomicListVariable;
import RalphDataWrappers.ListTypeDataWrapper;

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
    
    public SwitchFactory()
    {}

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
                
        PronghornInternalSwitch to_return = new PronghornInternalSwitch(new_switch_id);

        // override internal routing table variable
        InternalRoutingTableList internal_rtable_list = null;
        if (to_handle_pushing_changes == null)
        {
            internal_rtable_list = new InternalRoutingTableList();
        }
        else
        {
            internal_rtable_list =
                new InternalRoutingTableList(to_handle_pushing_changes);
        }
        
        to_return.rtable =
            new AtomicListVariable<_InternalRoutingTableEntry,_InternalRoutingTableEntry>(
                "",false,internal_rtable_list,
                RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry);
        
        // produce and overwrite a switch id associated with this switch
        to_return.switch_id = new AtomicTextVariable("",false,new_switch_id);

        // set available capacity
        to_return.available_capacity =
            new AtomicNumberVariable("",false,available_capacity);
        
        return to_return;
    }

    public class PronghornInternalSwitch extends _InternalSwitch
    {
        public String ralph_internal_switch_id;
        public PronghornInternalSwitch(String _ralph_internal_switch_id)
        {
            super();
            ralph_internal_switch_id = _ralph_internal_switch_id;
        }
    }

    
    /**
       Subclass this object to override behavior of internal list when
       it is asked to push changes to hardware or undo pushed changes
       to hardware.
     */
    public static class RoutingTableToHardware
    {
        public boolean apply_changes_to_hardware(
            ListTypeDataWrapper<
                _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
        {
            // FIXME: don't use System.out.println for this
            System.out.println(
                "\nWarning, using default routingtabletohardware object\n");
            return true;
        }
        public void undo_dirty_changes_to_hardware(
            ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry> to_undo)
        {
            // FIXME: don't use System.out.println for this
            System.out.println(
                "\nWarning, using default routingtabletohardware object\n");            
        }
        
    }


    /**
       By default, will only simulate pushing changes to hardware.
       (Ie., will always return message that says it did push changes
       to hardware without doing any work.)
     */
    private static final RoutingTableToHardware DEFAULT_ROUTING_TABLE_TO_HARDWARE =
        new RoutingTableToHardware();

    private class InternalRoutingTableList
        extends ExtendedInternalAtomicList<
        _InternalRoutingTableEntry,_InternalRoutingTableEntry>
    {
        private RoutingTableToHardware rtable_to_hardware_obj =
            DEFAULT_ROUTING_TABLE_TO_HARDWARE;
        
        // For now, any change to the internal routing table just gets
        // accepted.
        public InternalRoutingTableList()
        {
            super (RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry);
        }

        public InternalRoutingTableList(
            RoutingTableToHardware _rtable_to_hardware_obj)
        {
            super (RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry);
            rtable_to_hardware_obj = _rtable_to_hardware_obj;
        }
        
        protected boolean apply_changes_to_hardware(
            ListTypeDataWrapper<
                _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
        {
            return rtable_to_hardware_obj.apply_changes_to_hardware(dirty);
        }
        protected void undo_dirty_changes_to_hardware(
            ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry> to_undo)
        {
            rtable_to_hardware_obj.undo_dirty_changes_to_hardware(to_undo);
        }
    }
}