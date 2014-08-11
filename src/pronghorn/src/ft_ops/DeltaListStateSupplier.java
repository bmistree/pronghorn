package pronghorn.ft_ops;

import java.util.List;
import java.util.ArrayList;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openflow.protocol.OFMatch;

import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionGotoTable;
import org.openflow.protocol.instruction.OFInstructionWriteMetaData;
import org.openflow.protocol.instruction.OFInstructionWriteActions;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.openflow.protocol.instruction.OFInstructionClearActions;
import org.openflow.protocol.instruction.OFInstructionMeter;
import org.openflow.protocol.instruction.OFInstructionClearActions;

import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionCopyTTLOut;
import org.openflow.protocol.action.OFActionCopyTTLIn;
import org.openflow.protocol.action.OFActionSetMPLSTTL;
import org.openflow.protocol.action.OFActionDecrementMPLSTTL;
import org.openflow.protocol.action.OFActionSetMPLSTTL;
import org.openflow.protocol.action.OFActionPopVLAN;
import org.openflow.protocol.action.OFActionPushMPLS;
import org.openflow.protocol.action.OFActionPopMPLS;
import org.openflow.protocol.action.OFActionSetQueue;
import org.openflow.protocol.action.OFActionGroup;
import org.openflow.protocol.action.OFActionSetField;
import org.openflow.protocol.action.OFActionSetNwTTL;
import org.openflow.protocol.action.OFActionDecrementNwTTL;
import org.openflow.protocol.action.OFActionPushPBB;
import org.openflow.protocol.action.OFActionPopPBB;

import org.openflow.protocol.OFOXMFieldType;
import org.openflow.protocol.OFOXMField;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.Ethernet;

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
import pronghorn.MatchJava._InternalMatchField;
import pronghorn.InstructionsJava._InternalInstructions;
import pronghorn.InstructionsJava._InternalInstructionGotoTable;
import pronghorn.InstructionsJava._InternalInstructionWriteMetadata;
import pronghorn.InstructionsJava._InternalInstructionClearActions;
import pronghorn.InstructionsJava._InternalInstructionMeter;

import pronghorn.ActionsJava._InternalAction;
import pronghorn.ActionsJava._InternalActionOutput;
import pronghorn.ActionsJava._InternalActionCopyTTLOut;
import pronghorn.ActionsJava._InternalActionCopyTTLIn;
import pronghorn.ActionsJava._InternalActionSetMPLSTTL;
import pronghorn.ActionsJava._InternalActionDecrementMPLSTTL;
import pronghorn.ActionsJava._InternalActionPushVLAN;
import pronghorn.ActionsJava._InternalActionPopVLAN;
import pronghorn.ActionsJava._InternalActionPushMPLS;
import pronghorn.ActionsJava._InternalActionPopMPLS;
import pronghorn.ActionsJava._InternalActionSetQueue;
import pronghorn.ActionsJava._InternalActionGroup;
import pronghorn.ActionsJava._InternalActionSetNWTTL;
import pronghorn.ActionsJava._InternalActionDecrementNWTTL;
import pronghorn.ActionsJava._InternalActionSetField;
import pronghorn.ActionsJava._InternalActionPushPBB;
import pronghorn.ActionsJava._InternalActionPopPBB;


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
            _InternalMatch internal_match = null;

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

                internal_match =
                    RalphInternalValueRemover.<_InternalMatch>
                    get_internal(entry.match);
                if (internal_match == null)
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

            // FIXME: lock tvar and don't continue in case that get
            // backout.
            
            // non tvar, therefore has different val.val access pattern.
            boolean insertion =
                flow_table_delta.inserted.val.val.booleanValue();
            
            OFMatch match = match_from_internal_match(internal_match);
            if (match == null)
                break;
            
            _InternalInstructions instructions = 
                RalphInternalValueRemover.<_InternalInstructions>
                get_internal(entry.instructions);

            List<OFInstruction> instruction_list =
                instruction_list_from_internal_instructions(instructions);
            
            
            FTableUpdate update_to_push =  null;
            if (insertion)
            {
                update_to_push = FTableUpdate.create_insert_update(
                    match,instruction_list);
            }
            else
            {
                update_to_push = FTableUpdate.create_remove_update(
                    match,instruction_list);
            }
            floodlight_updates.add(update_to_push);
        }
        return floodlight_updates;
    }


    /**
       Generates a list of matches from ralph object
     */
    private OFMatch match_from_internal_match(
        _InternalMatch match)
    {
        // grap internal list
        AtomicListVariable<_InternalMatchField,_InternalMatchField> match_list =
            match.all_matches;
        AtomicInternalList<_InternalMatchField,_InternalMatchField>
            ralph_internal_match_list = null;

        ralph_internal_match_list =
            RalphInternalValueRemover.<
                AtomicInternalList<
            _InternalMatchField,_InternalMatchField>,
                _InternalMatchField>
            list_get_internal(match_list);

        if (ralph_internal_match_list == null)
            return null;
        
        List<_InternalMatchField> internal_match_list = null;
        internal_match_list = RalphInternalValueRemover.
            <ArrayList<_InternalMatchField>> internal_list_get_internal(
                ralph_internal_match_list);
        String ofmatch_generator_string = "";
        for (_InternalMatchField match_field : internal_match_list)
        {
            // FIXME: Am I parsing wildcards correctly here?
            String field_name =
                RalphInternalValueRemover.<String>
                get_internal(match_field.field_name);

            String value =
                RalphInternalValueRemover.<String>
                get_internal(match_field.value);
            
            if ((field_name == null) || (value == null))
                continue;

            ofmatch_generator_string +=
                field_name + "=" + value + ",";
        }

        if (ofmatch_generator_string.equals(""))
            return null;

        try
        {
            return OFMatch.fromString(ofmatch_generator_string);
        }
        catch(IllegalArgumentException ex)
        {
            log.error(
                "User entered incorrect match arguments",
                ex);

            // FIXME: for incorrectly-generated user strings, don't
            // throw exception to user, but do not apply change.
            // Could lead to disagreement if not checking correct
            // field and value insertion in ralph instead.
            return null;
        }
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

        if ((metadata == null) || (metadata_mask == null))
            return null;
            
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
        
        if (clear_actions == null)
            return null;

        return new OFInstructionClearActions();
    }
        
    private OFInstructionMeter produce_meter_from_internal(
        _InternalInstructions _instructions)
    {
        _InternalInstructionMeter meter =
            RalphInternalValueRemover.<_InternalInstructionMeter>
            get_internal(_instructions.meter);

        if (meter == null)
            return null;

        Double meter_id = null;
        meter_id = 
            RalphInternalValueRemover.<Double>
            get_internal(meter.meter_id);

        if (meter_id == null)
            return null;

        return new OFInstructionMeter(meter_id.intValue());
    }


    /*
      Produce actions from ralph actions
     */
    private OFAction action_from_internal_action(_InternalAction action)
    {
        // output action
        // note: additional curly braces scope internal
        // variables... did this because writing this code involved a
        // lot of copy-paste and wanted to ensure that wasn't
        // accidentally reusing variable declared above.
        {
            _InternalActionOutput action_output =
                RalphInternalValueRemover.<_InternalActionOutput>
                get_internal(action.output);

            if (action_output != null)
            {
                Double port_number =
                    RalphInternalValueRemover.<Double>
                    get_internal(action_output.port_number);

                if (port_number != null)
                    return new OFActionOutput(port_number.intValue());
            }
        }

        // copy_ttl_out
        {
            _InternalActionCopyTTLOut action_copy_ttl_out = 
                RalphInternalValueRemover.<_InternalActionCopyTTLOut>
                get_internal(action.copy_ttl_out);
        
            if (action_copy_ttl_out != null)
                return new OFActionCopyTTLOut();
        }

        // copy_ttl_in
        {
            _InternalActionCopyTTLIn action_copy_ttl_in = 
                RalphInternalValueRemover.<_InternalActionCopyTTLIn>
                get_internal(action.copy_ttl_in);
        
            if (action_copy_ttl_in != null)
                return new OFActionCopyTTLIn();
        }

        // set_mpls_ttl
        {
            _InternalActionSetMPLSTTL action_set_mpls_ttl = 
                RalphInternalValueRemover.<_InternalActionSetMPLSTTL>
                get_internal(action.set_mpls_ttl);
        
            if (action_set_mpls_ttl != null)
            {
                Double mpls_ttl =
                    RalphInternalValueRemover.<Double>
                    get_internal(action_set_mpls_ttl.mpls_ttl);
            
                return new OFActionSetMPLSTTL(mpls_ttl.byteValue());
            }
        }

        // decrement_mpls_ttl
        {
            _InternalActionDecrementMPLSTTL action_decrement_mpls_ttl =
                RalphInternalValueRemover.<_InternalActionDecrementMPLSTTL>
                get_internal(action.decrement_mpls_ttl);
        
            if (action_decrement_mpls_ttl != null)
                return new OFActionDecrementMPLSTTL();
        }

        // push_vlan
        {
            _InternalActionPushVLAN action_push_vlan = 
                RalphInternalValueRemover.<_InternalActionPushVLAN>
                get_internal(action.push_vlan);
        
            if (action_push_vlan != null)
            {
                Double ethertype =
                    RalphInternalValueRemover.<Double>
                    get_internal(action_push_vlan.ethertype);
            
                return new OFActionSetMPLSTTL(ethertype.byteValue());
            }
        }

        // pop_vlan
        {
            _InternalActionPopVLAN action_pop_vlan =
                RalphInternalValueRemover.<_InternalActionPopVLAN>
                get_internal(action.pop_vlan);
        
            if (action_pop_vlan != null)
                return new OFActionPopVLAN();
        }

        // push_mpls
        {
            _InternalActionPushMPLS action_push_mpls = 
                RalphInternalValueRemover.<_InternalActionPushMPLS>
                get_internal(action.push_mpls);
        
            if (action_push_mpls != null)
            {
                Double ethertype =
                    RalphInternalValueRemover.<Double>
                    get_internal(action_push_mpls.ethertype);
            
                return new OFActionPushMPLS(ethertype.byteValue());
            }
        }

        // pop_mpls
        {
            _InternalActionPopMPLS action_pop_mpls =
                RalphInternalValueRemover.<_InternalActionPopMPLS>
                get_internal(action.pop_mpls);
        
            if (action_pop_mpls != null)
                return new OFActionPopMPLS();
        }

        // set_queue
        {
            _InternalActionSetQueue action_set_queue =
                RalphInternalValueRemover.<_InternalActionSetQueue>
                get_internal(action.set_queue);

            if (action_set_queue != null)
            {
                Double queue_id =
                    RalphInternalValueRemover.<Double>
                    get_internal(action_set_queue.queue_id);

                OFActionSetQueue to_return = new OFActionSetQueue();
                to_return.setQueueId(queue_id.intValue());
                return to_return;
            }
        }
        
        // group
        {
            _InternalActionGroup action_group =
                RalphInternalValueRemover.<_InternalActionGroup>
                get_internal(action.group);

            if (action_group != null)
            {
                Double group_id =
                    RalphInternalValueRemover.<Double>
                    get_internal(action_group.group_id);

                OFActionGroup to_return = new OFActionGroup();
                to_return.setQueueId(group_id.intValue());
                return to_return;
            }
        }

        // set ip ttl
        {
            _InternalActionSetNWTTL action_set_nw_ttl = 
                RalphInternalValueRemover.<_InternalActionSetNWTTL>
                get_internal(action.set_nw_ttl);
        
            if (action_set_nw_ttl != null)
            {
                Double ttl =
                    RalphInternalValueRemover.<Double>
                    get_internal(action_set_nw_ttl.ttl);
            
                return new OFActionSetNwTTL(ttl.byteValue());
            }
        }

        // decrement ip ttl
        {
            _InternalActionDecrementNWTTL action_decrement_nw_ttl =
                RalphInternalValueRemover.<_InternalActionDecrementNWTTL>
                get_internal(action.decrement_nw_ttl);
        
            if (action_decrement_nw_ttl != null)
                return new OFActionDecrementNwTTL();
        }

        // set field
        {
            _InternalActionSetField action_set_field = 
                RalphInternalValueRemover.<_InternalActionSetField>
                get_internal(action.set_field);
        
            if (action_set_field != null)
            {
                String field_name =
                    RalphInternalValueRemover.<String>
                    get_internal(action_set_field.field_name);
                String value =
                    RalphInternalValueRemover.<String>
                    get_internal(action_set_field.value);

                OFOXMField field =
                    ralph_set_field_to_floodlight_field(
                        field_name,value);
                return new OFActionSetField(field);
            }
        }

        // push pbb
        {
            _InternalActionPushPBB action_push_pbb =
                RalphInternalValueRemover.<_InternalActionPushPBB>
                get_internal(action.push_pbb);

            if (action_push_pbb != null)
            {
                Double ethertype =
                    RalphInternalValueRemover.<Double>
                    get_internal(action_push_pbb.ethertype);

                return new OFActionPushPBB(ethertype.shortValue());
            }
        }

        // pop pbb
        {
            _InternalActionPopPBB action_pop_pbb =
                RalphInternalValueRemover.<_InternalActionPopPBB>
                get_internal(action.pop_pbb);
        
            if (action_pop_pbb != null)
                return new OFActionPopPBB();
        }
        
        return null;
    }

    // helper methods
    private short get_short(String str)
    {
        return (short)(int)Integer.decode(str);
    }
    private int get_int(String str)
    {
        return (int)Integer.decode(str);
    }
    private long get_long(String str)
    {
        return (long)Long.decode(str);
    }
    private byte get_byte(String str)
    {
        return (byte)Byte.decode(str);
    }
    
    
    /**
       Application writers specify field type for set_field actions
       using strings.  This method converts that string into proper
       floodlight OFOXMField.
     */
    private OFOXMField ralph_set_field_to_floodlight_field(
        String field_name, String field_value)
    {
        if (field_name.equals(OFOXMFieldType.IN_PORT.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IN_PORT,
                get_int(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IN_PHY_PORT.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IN_PHY_PORT,
                get_int(field_value));
        }
        if (field_name.equals(OFOXMFieldType.METADATA.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.METADATA,
                get_long(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ETH_DST.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ETH_DST,
                Ethernet.toMACAddress(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ETH_SRC.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ETH_SRC,
                Ethernet.toMACAddress(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ETH_TYPE.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ETH_TYPE,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.VLAN_VID.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.VLAN_VID,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.VLAN_PCP.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.VLAN_PCP,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IP_DSCP.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IP_DSCP,
                field_value.getBytes(Charset.forName("UTF-8")));
        }
        if (field_name.equals(OFOXMFieldType.IP_ECN.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IP_ECN,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IP_PROTO.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IP_PROTO,
                get_byte(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IPV4_SRC.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IPV4_SRC,
                IPv4.toIPv4Address(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IPV4_DST.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IPV4_DST,
                IPv4.toIPv4Address(field_value));
        }
        if (field_name.equals(OFOXMFieldType.TCP_SRC.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.TCP_SRC,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.TCP_DST.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.TCP_DST,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.UDP_SRC.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.UDP_SRC,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.UDP_DST.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.UDP_DST,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.SCTP_SRC.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.SCTP_SRC,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.SCTP_DST.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.SCTP_DST,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ICMPV4_TYPE.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ICMPV4_TYPE,
                get_byte(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ICMPV4_CODE.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ICMPV4_CODE,
                get_byte(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ARP_OP.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ARP_OP,
                get_short(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ARP_SPA.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ARP_SPA,
                get_int(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ARP_TPA.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ARP_TPA,
                get_int(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ARP_SHA.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ARP_SHA,
                Ethernet.toMACAddress(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ARP_THA.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ARP_THA,
                Ethernet.toMACAddress(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IPV6_SRC.getName()))
        {
            // FIXME: no real support for ipv6 address parsing yet.
            // Just using raw bytes.
            return new OFOXMField(
                OFOXMFieldType.IPV6_SRC,    
                field_value.getBytes(Charset.forName("UTF-8")));
        }
        if (field_name.equals(OFOXMFieldType.IPV6_DST.getName()))
        {
            // FIXME: no real support for ipv6 address parsing yet.
            // Just using raw bytes.
            return new OFOXMField(
                OFOXMFieldType.IPV6_DST,
                field_value.getBytes(Charset.forName("UTF-8")));
        }
        if (field_name.equals(OFOXMFieldType.IPV6_FLABEL.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IPV6_FLABEL,
                get_int(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ICMPV6_TYPE.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ICMPV6_TYPE,
                get_byte(field_value));
        }
        if (field_name.equals(OFOXMFieldType.ICMPV6_CODE.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.ICMPV6_CODE,
                get_byte(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IPV6_ND_TARGET.getName()))
        {
            // FIXME: no real support for ipv6 address parsing yet.
            // Just using raw bytes.
            return new OFOXMField(
                OFOXMFieldType.IPV6_ND_TARGET,
                field_value.getBytes(Charset.forName("UTF-8")));
        }
        if (field_name.equals(OFOXMFieldType.IPV6_ND_SLL.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IPV6_ND_SLL,
                Ethernet.toMACAddress(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IPV6_ND_TLL.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IPV6_ND_TLL,
                Ethernet.toMACAddress(field_value));
        }
        if (field_name.equals(OFOXMFieldType.MPLS_LABEL.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.MPLS_LABEL,
                get_int(field_value));
        }
        if (field_name.equals(OFOXMFieldType.MPLS_TC.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.MPLS_TC,
                get_byte(field_value));
        }
        if (field_name.equals(OFOXMFieldType.MPLS_BOS.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.MPLS_BOS,
                get_byte(field_value));
        }
        if (field_name.equals(OFOXMFieldType.PBB_ISID.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.PBB_ISID,
                get_int(field_value));
        }
        if (field_name.equals(OFOXMFieldType.TUNNEL_ID.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.TUNNEL_ID,
                get_long(field_value));
        }
        if (field_name.equals(OFOXMFieldType.IPV6_EXTHDR.getName()))
        {
            return new OFOXMField(
                OFOXMFieldType.IPV6_EXTHDR,
                get_short(field_value));
        }
        // FIXME: Handle case of incorrectly specified field type.
        log.error("Incorrect field type specified for set field.");
        assert(false);
        return null;
    }
}
