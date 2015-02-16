package pronghorn;
import pronghorn.PortStatsJava._InternalPortStats;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import ralph.Variables.NonAtomicNumberVariable;
import ralph.RalphGlobals;

public class OFStatsMessageParser
{
    public static _InternalPortStats of_port_stats_to_ralph(
        OFPortStatisticsReply of_port_stats,RalphGlobals ralph_globals)
    {
        _InternalPortStats to_return =
            new _InternalPortStats(ralph_globals,null,null);
        to_return.port_number = new NonAtomicNumberVariable (
            false,new Double(of_port_stats.getPortNumber()),ralph_globals);
        to_return.tx_dropped = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getTransmitDropped())),ralph_globals);
        to_return.rx_packets = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getReceivePackets())),ralph_globals);
        to_return.rx_frame_err = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getReceiveFrameErrors())),ralph_globals);
        to_return.rx_bytes = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getReceiveBytes())),ralph_globals);
        to_return.tx_errors = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getTransmitErrors())),ralph_globals);
        to_return.rx_errors = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getreceiveErrors())),ralph_globals);
        to_return.tx_bytes = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getTransmitBytes())),ralph_globals);
        to_return.rx_dropped = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getReceiveDropped())),ralph_globals);
        to_return.tx_packets = new NonAtomicNumberVariable (
            false,(new Double(of_port_stats.getTransmitPackets())),ralph_globals);
        return to_return;
    }
}