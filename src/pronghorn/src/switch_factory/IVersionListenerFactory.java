package pronghorn.switch_factory;

import RalphVersions.IVersionListener;

public interface IVersionListenerFactory
{
    public IVersionListener construct(String switch_id);
}
