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

    public PronghornInternalSwitch construct(double available_capacity)
    {
        // create a new switch id
        Integer factory_local_unique_id = atomic_switch_id.addAndGet(1);
        String new_switch_id =
            factory_switch_prefix + SWITCH_PREFIX_TO_ID_SEPARATOR +
            factory_local_unique_id.toString();
                
        PronghornInternalSwitch to_return =
            new PronghornInternalSwitch(new_switch_id);

        // override internal routing table variable
        InternalRoutingTableList internal_rtable_list =
            new InternalRoutingTableList();
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
    

    
    private class InternalRoutingTableList
        extends ExtendedInternalAtomicList<
        _InternalRoutingTableEntry,_InternalRoutingTableEntry>
    {
        // For now, any change to the internal routing table just gets
        // accepted.
        public InternalRoutingTableList()
        {
            super (RTable.STRUCT_LOCKED_MAP_WRAPPER__RoutingTableEntry);
        }
        protected boolean apply_changes_to_hardware(
            ListTypeDataWrapper<
                _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
        {
            return true;
        }
        protected void undo_dirty_changes_to_hardware(
            ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry> to_undo)
        {
        }
    }
}