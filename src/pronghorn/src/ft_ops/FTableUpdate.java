package pronghorn.ft_ops;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.instruction.OFInstruction;

import net.floodlightcontroller.packet.IPv4;



public class FTableUpdate
{
    final private OFMatch match;
    final private List<OFInstruction> instructions;
    /**
       true if this is an insertion flow mod.  false if it's a
       deletion.
     */
    private boolean insertion;


    protected static final Logger log =
        LoggerFactory.getLogger(FTableUpdate.class);
    
    public static FTableUpdate create_insert_update(
        OFMatch match, List<OFInstruction> instructions)
    {
        FTableUpdate to_return = new FTableUpdate(
            match,instructions,true);
        return to_return;
    }

    /**
       @param instructions --- Although sending a flow mod remove to
       the controller does not require having any instructions, if we
       need to undo this removal (ie, re-add a flow mod), we must know
       the instructions that this re-added flow_mod should allow.
       That is why we have the additional parameter below.
     */
    public static FTableUpdate create_remove_update(
        OFMatch match, List<OFInstruction> instructions)
    {
        FTableUpdate to_return = new FTableUpdate(
            match,instructions,false);
        return to_return;
    }


    /**
       Takes update and generates a flow mod out of it, which
       floodlight sends to switch.
     */
    public OFFlowMod to_flow_mod(int xid)
    {
        OFFlowMod to_return = new OFFlowMod();

        // decide whether to add or delete
        if (insertion)
            to_return.setCommand(OFFlowMod.OFPFC_ADD);
        else
            to_return.setCommand(OFFlowMod.OFPFC_DELETE_STRICT);

        // handle matches
        to_return.setMatch(match);
        
        // add operations for insertions
        if (insertion)
            to_return.setInstructions(instructions);
        
        return to_return;
    }


    /**
       Takes current flow mod instruction and creates an FTableUpdate
       that is the opposite of it.  If we run an FTableUpdate and then
       its update, we should be in the state that we were before
       running either.
     */
    public FTableUpdate create_undo()
    {
        return new FTableUpdate(match,instructions,! insertion);
    }
    
    /**
       Private constructor: use static methods to construct insertion
       and remove updates.
     */
    private FTableUpdate(
        OFMatch _match, List<OFInstruction> _instructions,
        boolean _insertion)
    {
        match = _match;
        instructions = _instructions;
        insertion = _insertion;
    }
}