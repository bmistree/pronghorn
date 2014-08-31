package pronghorn.ft_ops;

import ralph.RalphGlobals;

import RalphVersions.VersionServer.ServerThread;
import RalphVersions.IVersionListener;
import RalphVersions.VersionListener;
import RalphVersions.IVersionManager;
import RalphVersions.VersionManager;

import pronghorn.switch_factory.IVersionListenerFactory;

public class TCPVersionManager implements IVersionListenerFactory
{
    private final String version_server_ip_address;
    private final int version_server_port_number;
    private final RalphGlobals ralph_globals;
    private final VersionManager version_manager;
    
    public TCPVersionManager(
        String _version_server_ip_address,int _version_server_port_number,
        RalphGlobals _ralph_globals)
    {
        version_server_ip_address = _version_server_ip_address;
        version_server_port_number = _version_server_port_number;
        ralph_globals = _ralph_globals;
        version_manager = new VersionManager(_ralph_globals.clock);

        start_version_server();
    }

    private void start_version_server()
    {
        ServerThread server_thread =
            new ServerThread(
                version_manager, version_server_ip_address,
                version_server_port_number);
        server_thread.start();
    }
    
    @Override
    public IVersionListener construct(String switch_id)
    {
        return new VersionListener(
            ralph_globals.clock, version_manager,
            switch_id, VersionUpdateSerializer.FTABLE_SERIALIZER);
    }
}
