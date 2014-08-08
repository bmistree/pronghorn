package pronghorn.ft_ops;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionGotoTable;
import org.openflow.protocol.instruction.OFInstructionWriteMetaData;
import org.openflow.protocol.instruction.OFInstructionWriteActions;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.openflow.protocol.instruction.OFInstructionClearActions;
import org.openflow.protocol.instruction.OFInstructionMeter;
import org.openflow.protocol.instruction.OFInstructionClearActions;

import RalphExtended.IHardwareStateSupplier;
import ralph.ActiveEvent;
import ralph.RalphObject;
import ralph.AtomicObject;
import ralph.AtomicInternalList;
import ralph.Variables.AtomicListVariable;
import ralph.Variables.AtomicNumberVariable;


import pronghorn.SwitchDeltaJava._InternalFlowTableDelta;
import pronghorn.SwitchDeltaJava._InternalSwitchDelta;
import pronghorn.FTable._InternalFlowTableEntry;
import pronghorn.MatchJava._InternalMatch;
import pronghorn.InstructionsJava._InternalInstructions;
import pronghorn.InstructionsJava._InternalInstructionGotoTable;
import pronghorn.InstructionsJava._InternalInstructionWriteMetadata;
import pronghorn.InstructionsJava._InternalInstructionClearActions;


/**
   When InternalPronghornSwitchGuard is ready to commit, it uses this
   class to grab deltas that should be pushed to hardware.
 */
public class DeltaListStateSupplier
    implements IHardwareStateSupplier<List<FTableUpdate>>
{
    protected static final Logger log =
        LoggerFactory.getLogger(DeltaListStateSupplier.class);

    private final _InternalSwitchDelta switch_delta;


    public DeltaListStateSupplier(_InternalSwitchDelta _switch_delta)
    {
        switch_delta = _switch_delta;
    }

    @Override
    public List<FTableUpdate> get_state_to_push(ActiveEvent active_event)
    {
        // FIXME: ensure that this is a safe access.
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = get_internal_ft_deltas_list();

        // We can get null here if the transaction has been backed
        // out.  In this case, it's okay to set internal_list to an
        // empty list, because we will undo/not apply its changes
        // anyways.

        // FIXME: ensure that this is a safe access.  May have to lock
        // internal_ft_deltas_list object first.
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>> internal_list =
            null;
        internal_ft_deltas_list._lock();
        if (internal_ft_deltas_list.dirty_val != null)
            internal_list = internal_ft_deltas_list.dirty_val.val;
        else
        {
            internal_list =
                new ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>();
        }
        internal_ft_deltas_list._unlock();
        
        List<FTableUpdate> to_return = produce_ftable_updates(internal_list);

        
        // FIXME: REALLY, REALLY shouldn't have to do this here.  only
        // require it because speculate is the only place where we
        // reset deltas to be empty.  We will not reset it if
        // speculation is turned off.  Need a way to ensure that we
        // reset val.  See Issue #10 and comment in speculate of
        // SwitchSpeculateListener.
        internal_ft_deltas_list.force_speculate(
            active_event,
            // so that resets delta list
            new ArrayList<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>>(),
            // forces update on internal val
            true);

        return to_return;
    }
    
    private AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
        get_internal_ft_deltas_list()
    {
        // these accesses are safe, because we assume the invariant
        // that will only receive changes on PronghornSwitchGuard
        // if no other event is writing to them.

        // grabbing ft_deltas to actually get changes made to hardware.
        AtomicListVariable<_InternalFlowTableDelta,_InternalFlowTableDelta>
            ft_deltas_list = switch_delta.ft_deltas;
        AtomicInternalList<_InternalFlowTableDelta,_InternalFlowTableDelta>
            internal_ft_deltas_list = null;

        internal_ft_deltas_list =
            RalphInternalValueRemover.<
                AtomicInternalList<
                    _InternalFlowTableDelta,_InternalFlowTableDelta>,
                _InternalFlowTableDelta>
            list_get_internal(ft_deltas_list);
        return internal_ft_deltas_list;
    }


    /**
       @param {boolean} undo --- true if should actually try to
       undo the changes in dirty, rather than apply them.  Note: if
       reverse is true, this means that we must go backwards through
       the list.
     */
    private List<FTableUpdate> produce_ftable_updates(
        List<RalphObject<_InternalFlowTableDelta,_InternalFlowTableDelta>> dirty)
    {
        List<FTableUpdate> floodlight_updates =
            new ArrayList<FTableUpdate>();
        
        for (RalphObject ro : dirty)
        {
            _InternalFlowTableDelta flow_table_delta = null;
            _InternalFlowTableEntry entry = null;
            _InternalMatch match = null;

            try
            {
                flow_table_delta = (_InternalFlowTableDelta) (ro.get_val(null));
                entry =
                    RalphInternalValueRemover.<_InternalFlowTableEntry>
                    get_internal(flow_table_delta.entry);

                if (entry == null)
                {
                    // can get a null entry in cases where are currently
                    // backing out of an update (and therefore reverting
                    // to original flow_table_delta value).  In that case,
                    // we do not need to produce an update for this target
                    // (no need to push it to switch), and can stop
                    // producing other updates.
                    break;
                }

                match =
                    RalphInternalValueRemover.<_InternalMatch>
                    get_internal(entry.match);
                if (match == null)
                {
                    // see above note about entry.
                    break;
                }
                
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                log.error(
                    "Should always be able to cast to InternalFlowTableDelta",
                    ex.toString());
                assert(false);
            }
            

            // non tvar, therefore has different val.val access pattern.
            boolean insertion =
                flow_table_delta.inserted.val.val.booleanValue();

            // FIXME: double check that match cannot change out from
            // under us during this process.
            String src_ip =
                RalphInternalValueRemover.<String>get_internal(match.src_ip);
            String dst_ip =
                RalphInternalValueRemover.<String>get_internal(match.dst_ip);

            _InternalInstructions instructions = 
                RalphInternalValueRemover.<_InternalInstructions>
                get_internal(entry.instructions);

            List<OFInstruction> instruction_list =
                instruction_list_from_internal_instructions(instructions);
            

            // means that this change was backed out before could
            // complete and src_ip or dst_ip was backed out and reset
            // to default text value.  do not apply change (it will be
            // backed out anyways).
            if (dst_ip.equals("") || src_ip.equals(""))
                break;

            
            FTableUpdate update_to_push =  null;
            if (insertion)
            {
                update_to_push = FTableUpdate.create_insert_update(
                    src_ip, dst_ip,instruction_list);
            }
            else
            {
                update_to_push = FTableUpdate.create_remove_update(
                    src_ip, dst_ip,instruction_list);
            }
            floodlight_updates.add(update_to_push);
        }
        return floodlight_updates;
    }


    /**
       Generates a list of floodlight instructions from ralph object.
     */
    private List<OFInstruction> instruction_list_from_internal_instructions(
        _InternalInstructions instructions)
    {
        List<OFInstruction> to_return = new ArrayList<OFInstruction>();
        
        OFInstructionGotoTable goto_instruction =
            produce_goto_from_internal(instructions);
        if (goto_instruction != null)
            to_return.add(goto_instruction);

        OFInstructionWriteMetaData write_metadata_instruction =
            produce_write_metadata_from_internal(instructions);
        if (write_metadata_instruction != null)
            to_return.add(write_metadata_instruction);

        OFInstructionWriteActions write_actions_instruction =
            produce_write_actions_from_internal(instructions);
        if (write_actions_instruction != null)
            to_return.add(write_actions_instruction);
        
        OFInstructionApplyActions apply_actions_instruction =
            produce_apply_actions_from_internal(instructions);
        if (apply_actions_instruction != null)
            to_return.add(apply_actions_instruction);

        OFInstructionClearActions clear_actions_instruction =
            produce_clear_actions_from_internal(instructions);
        if (clear_actions_instruction != null)
            to_return.add(clear_actions_instruction);

        OFInstructionMeter meter_instruction =
            produce_meter_from_internal(instructions);
        if (meter_instruction != null)
            to_return.add(meter_instruction);
        
        return to_return;
    }

    /*
       Take ralph InternalInstruction and produce floodlight
       instruction from it.
    */
    
    private OFInstructionGotoTable produce_goto_from_internal(
        _InternalInstructions _instructions)
    {
        _InternalInstructionGotoTable goto_table =
            RalphInternalValueRemover.<_InternalInstructionGotoTable>
            get_internal(_instructions.goto_table);

        if (goto_table == null)
            return null;

        Double table_id =
            RalphInternalValueRemover.<Double>get_internal(goto_table.table_id);
        // means got a rollback as was sending
        if (table_id == null)
            return null;
        
        return new OFInstructionGotoTable(table_id.byteValue());
    }

    private OFInstructionWriteMetaData produce_write_metadata_from_internal(
        _InternalInstructions _instructions)
    {
        _InternalInstructionWriteMetadata write_metadata =
            RalphInternalValueRemover.<_InternalInstructionWriteMetadata>
            get_internal(_instructions.write_metadata);

        if (write_metadata == null)
            return null;

        Double metadata = null;
        Double metadata_mask = null;

        metadata = 
            RalphInternalValueRemover.<Double>
            get_internal(write_metadata.metadata);

        metadata_mask = 
            RalphInternalValueRemover.<Double>
            get_internal(write_metadata.metadata_mask);

        return new OFInstructionWriteMetaData(
            metadata.longValue(),metadata_mask.longValue());
    }

    private OFInstructionWriteActions produce_write_actions_from_internal(
        _InternalInstructions _instructions)
    {
        // TODO: fill in stub method
        return null;
    }
    
    private OFInstructionApplyActions produce_apply_actions_from_internal(
        _InternalInstructions _instructions)
    {
        // TODO: fill in stub method
        return null;
    }

    private OFInstructionClearActions produce_clear_actions_from_internal(
        _InternalInstructions _instructions)
    {
        _InternalInstructionClearActions clear_actions =
            RalphInternalValueRemover.<_InternalInstructionClearActions>
            get_internal(_instructions.clear_actions);

        return new OFInstructionClearActions();
    }
        
    private OFInstructionMeter produce_meter_from_internal(
        _InternalInstructions _instructions)
    {
        // TODO: fill in stub method
        return null;
    }
    
}
