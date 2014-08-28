package pronghorn.switch_factory;

import RalphVersions.IVersionListener;

/**
   VersionListeners produced from this version factory won't actually
   perform any logging.
 */
public class NoLogVersionFactory implements IVersionListenerFactory
{
    @Override
    public IVersionListener construct(String switch_id)
    {
        // When presented with null version listeners, won't actually
        // log.
        return null;
    }
}
