package experiments;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ralph.RalphGlobals;

import ralph_version_protobuffs.VersionMessageProto.VersionMessage;
import ralph_version_protobuffs.VersionRequestProto.VersionRequestMessage;
import ralph_version_protobuffs.VersionResponseProto.VersionResponseMessage;
import ralph_version_protobuffs.SingleDeviceUpdateListProto.SingleDeviceUpdateListMessage;
import ralph_version_protobuffs.SingleDeviceUpdateProto.SingleDeviceUpdateMessage;

import pronghorn.ft_ops.VersionedFTableUpdatesWithMetadata;

/**
   Use this method to issue queries to get all updates from a remote
   pronghorn instance.
 */

public class VersionQuerier implements Runnable
{
    protected static final Logger log =
        LoggerFactory.getLogger(VersionQuerier.class);

    
    private Socket sock = null;

    /**
       Keys are transaction ids and values are the futures
       corresponding to the results.

       Additions and removals are protected by synchronized keyword on
       methods that interact with it.
     */
    private final Map<Long,FutureVersionQueryResult> query_map =
        new HashMap<Long,FutureVersionQueryResult>();

    private final AtomicLong next_query_id = new AtomicLong(0L);
    private final RalphGlobals ralph_globals;
    
    public VersionQuerier(
        RalphGlobals _ralph_globals,
        String version_server_host_to_connect_to,
        int version_server_port_to_connect_to)
    {
        ralph_globals = _ralph_globals;
        
        try
        {
            sock = new Socket(
                version_server_host_to_connect_to,
                version_server_port_to_connect_to);
            sock.setTcpNoDelay(true);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            assert(false);
            // in case asserts are disabled
            System.exit(-1);
        }
    }

    /**
       Listens for server query responses
     */
    @Override
    public void run()
    {
        while(true)
        {
            VersionMessage vm = null;
            try
            {
                vm = VersionMessage.parseDelimitedFrom(
                    sock.getInputStream());
            }
            catch (IOException e)
            {
                e.printStackTrace();
                assert(false);
                System.exit(-1);
            }

            // someone is querying for our version.  do not respond.
            // not keeping track of versions locally.
            if (! vm.hasResponse())
                continue;

            List<VersionedFTableUpdatesWithMetadata> query_response =
                new ArrayList<VersionedFTableUpdatesWithMetadata>();

            VersionResponseMessage response_message = vm.getResponse();
            long query_id = response_message.getQueryId();
            for (SingleDeviceUpdateListMessage update_list_msg :
                     response_message.getDeviceListList())
            {
                for (SingleDeviceUpdateMessage update_msg :
                         update_list_msg.getUpdateListList())
                {
                    VersionedFTableUpdatesWithMetadata update = 
                        new VersionedFTableUpdatesWithMetadata(update_msg);
                    query_response.add(update);
                }
            }

            set_version_response(query_response,query_id);
        }
    }

    /**
       The synchronized here protects query_map.
     */
    synchronized private void set_version_response(
        List<VersionedFTableUpdatesWithMetadata> query_response,
        long query_id)
    {
        FutureVersionQueryResult result = query_map.remove(query_id);
        //// DEBUG
        if (result == null)
        {
            log.error("No query result object to set.");
            assert(false);
            System.exit(-1);
        }
        //// END DEBUG
        result.put_result(query_response);
    }

    /**
       The synchronized here protects query_map and ensures that can
       only have one writer at a time to sock's output stream.
     */
    synchronized public FutureVersionQueryResult get_all_versions()
    {
        long query_id = next_query_id.getAndIncrement();

        VersionRequestMessage.Builder request =
            VersionRequestMessage.newBuilder();
        request.setQueryId(query_id);

        VersionMessage.Builder vm_builder = VersionMessage.newBuilder();
        vm_builder.setRequest(request);
        vm_builder.setTimestamp(ralph_globals.clock.get_int_timestamp());
        VersionMessage version_message = vm_builder.build();

        // send query to version manager of remote host.
        try
        {
            version_message.writeDelimitedTo(sock.getOutputStream());
        }
        catch (IOException e)
        {
            e.printStackTrace();
            assert(false);
            System.exit(-1);
            return null;
        }

        // add query to query map
        FutureVersionQueryResult to_return = new FutureVersionQueryResult();
        query_map.put(query_id,to_return);
        return to_return;
    }
    
    synchronized public void close()
    {
        // FIXME: clearly could lead to very messy shutdown.
        try
        {
            sock.close();
        }
        catch(IOException ex)
        {
            // FIXME: handle appropriately
            ex.printStackTrace();
            assert(false);
            System.exit(-1);
        }

        for (FutureVersionQueryResult query_result :
                 query_map.values())
        {
            query_result.cancel(true);
        }
    }
}
