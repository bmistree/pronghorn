package experiments;

import pronghorn.switch_factory.NoLogVersionFactory;
import pronghorn.switch_factory.IVersionListenerFactory;

public enum VersionListenerFactoryArgs
{
    NO_LISTENER_FACTORY ("no-listener-factory"),
    SWITCH_LISTENER_FACTORY ("switch-listener-factory");

    public final String name;
    private VersionListenerFactoryArgs(String _name)
    {
        name = _name;
    }

    public static IVersionListenerFactory produce_factory(String factory_name)
    {
        if (factory_name.equals(NO_LISTENER_FACTORY.name))
        {
            return new NoLogVersionFactory();
        }
        else if (factory_name.equals(SWITCH_LISTENER_FACTORY.name))
        {
            System.err.println(
                "Error, still need to allow switch listener factory.");
            assert(false);
        }
        
        return null;
    }
}