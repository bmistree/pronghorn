package pronghorn.ft_ops;

import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.instruction.OFInstruction;

import net.floodlightcontroller.packet.IPv4;

import ft_ops.serialized_update.SingleFTableUpdateProto.SingleFTableUpdate;
import ft_ops.serialized_update.FTableUpdatesProto.FTableUpdates;

public class FTableUpdate
{
    final private OFMatch match;
    final private List<OFInstruction> instructions;
    final private long cookie;
    /**
       true if this is an insertion flow mod.  false if it's a
       deletion.
     */
    private boolean insertion;


    protected static final Logger log =
        LoggerFactory.getLogger(FTableUpdate.class);

    // FIXME: unclear why not just using constructor.
    public static FTableUpdate create_insert_update(
        OFMatch match, List<OFInstruction> instructions, long cookie)
    {
        FTableUpdate to_return = new FTableUpdate(
            match,instructions,true,cookie);
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
        OFMatch match, List<OFInstruction> instructions, long cookie)
    {
        FTableUpdate to_return = new FTableUpdate(
            match,instructions,false, cookie);
        return to_return;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (!(obj instanceof FTableUpdate))
            return false;

        FTableUpdate other = (FTableUpdate) obj;

        if (other.insertion != insertion)
            return false;

        if (!other.match.equals(match))
            return false;
        
        if (! other.instructions.equals(instructions))
            return false;

        if (other.cookie != cookie)
            return false;

        return true;
    }

    public static FTableUpdate deserialize(SingleFTableUpdate ft_update)
    {
        boolean insertion = ft_update.getInsertion();

        OFMatch match = null;
        if (ft_update.hasOfMatch())
        {
            ByteString of_match_bytestring = ft_update.getOfMatch();
            ByteBuffer of_match_bytebuffer =
                of_match_bytestring.asReadOnlyByteBuffer();
            match = new OFMatch();
            match.readFrom(of_match_bytebuffer);
        }

        List<OFInstruction> instructions = new ArrayList<OFInstruction>();
        for (ByteString of_instruction_bytestring :
                 ft_update.getOfInstructionsList())
        {
            ByteBuffer of_instruction_bytebuffer =
                of_instruction_bytestring.asReadOnlyByteBuffer();
            OFInstruction instruction = new OFInstruction();
            instruction.readFrom(of_instruction_bytebuffer);
            instructions.add(instruction);
        }

        // FIXME: pass cookie through deserialization instead of 0s
        // below.
        assert(false);
        
        if (insertion)
            return create_insert_update(match,instructions,0);
        return create_remove_update(match,instructions,0);
    }
    
    public static FTableUpdates.Builder serialize_update_list(
        List<FTableUpdate> update_list)
    {
        FTableUpdates.Builder to_return = FTableUpdates.newBuilder();
        for (FTableUpdate ft_update : update_list)
            to_return.addUpdates(ft_update.serialize());
        return to_return;
    }

    public static List<FTableUpdate> deserialize_update_list(
        FTableUpdates to_deserialize)
    {
        List<FTableUpdate> to_return = new ArrayList<FTableUpdate>();
        for (SingleFTableUpdate ftable_update_msg :
                 to_deserialize.getUpdatesList())
        {
            FTableUpdate update = deserialize(ftable_update_msg);
            to_return.add(update);
        }
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

        // handle cookies
        to_return.setCookie(cookie);
        to_return.setCookieMask(0xFFFFFFFFFFFFFFFFl);
        
        // add operations for insertions
        if (insertion)
            to_return.setInstructions(instructions);
        
        return to_return;
    }
    
    public SingleFTableUpdate.Builder serialize()
    {
        SingleFTableUpdate.Builder to_return =
            SingleFTableUpdate.newBuilder();

        to_return.setInsertion(insertion);

        if (match != null)
        {
            ByteBuffer bb = ByteBuffer.allocate(match.getLengthU());
            match.writeTo(bb);
            bb.rewind();
            to_return.setOfMatch(ByteString.copyFrom(bb));
        }
        
        for (OFInstruction instruction : instructions)
        {
            ByteBuffer bb = ByteBuffer.allocate(instruction.getLengthU());
            match.writeTo(bb);
            instruction.writeTo(bb);
            bb.rewind();
            to_return.addOfInstructions(ByteString.copyFrom(bb));
        }
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
        return new FTableUpdate(match, instructions, ! insertion, cookie);
    }
    
    /**
       Private constructor: use static methods to construct insertion
       and remove updates.
     */
    private FTableUpdate(
        OFMatch _match, List<OFInstruction> _instructions,
        boolean _insertion, long _cookie)
    {
        match = _match;
        instructions = _instructions;
        insertion = _insertion;
        cookie = _cookie;
    }
}