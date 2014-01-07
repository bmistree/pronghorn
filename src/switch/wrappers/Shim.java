package pronghorn;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
   Serves as intermediate layer between Ralph and Floodlight
 */
public class Shim implements Runnable
{
    /**
       The port that local floodlight server is running on.
     */
    private int floodlight_port;
    /**
       Whenever we see that there was a new switch or we see that a
       switch went down, we notify this handler.
     */
    private SwitchStatusHandler switch_status_handler = null;

    private Set<String> switch_id_set = new HashSet<String>();
    private ScheduledExecutorService executor;

    private static final long POLL_PERIOD_MS = 1000;
    
    public Shim(
        int _floodlight_port,
        SwitchStatusHandler _switch_status_handler) 
    {
        floodlight_port = _floodlight_port;
        switch_status_handler = _switch_status_handler;

        // schedule this class to poll the floodlight controller
        // periodically.
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(
            this, POLL_PERIOD_MS, POLL_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    public void stop()
    {
        executor.shutdown();
    }


    public String issue_get(String what_to_get)
    {
        String result = "";
        try {
            HttpURLConnection connection = null;
            URL url = new URL(
                "http","localhost",floodlight_port,what_to_get);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader rd =
                new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null)
                result += line;
            rd.close();
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        return result;
    }
    
    public Set<String> list_switches()
    {
        String all_switches_json = issue_get(
            "/wm/core/controller/switches/json");

        // all_switches_json has a complicated json format.  All
        // that's really important here though is the dpid of the
        // switch. which should be somewhere in the json similar to
        // ... "dpid":"00:00:00:00:00:00:00:01" ...
        // just write a regexp to grab all of them.

        String regex = "\"dpid\":\"(.*?)\"";

        Set<String> switch_ids = new HashSet<String>();
        Matcher m = Pattern.compile(regex).matcher(all_switches_json);
        while (m.find()) 
            switch_ids.add(m.group());

        return switch_ids;
    }
        
    /**
       Periodically poll to see if there's a new switch, or if an old
       switch is dead.  In either case, inform switch_status_handler.
     */
    public void run()
    {
        // list all switches
        Set<String> current_switch_ids = list_switches();

        for (String current_switch_id : current_switch_ids)
        {
            if (! switch_id_set.contains(current_switch_id))
            {
                switch_id_set.add(current_switch_id);
                switch_status_handler.new_switch(current_switch_id);
            }
        }

        // Breaking remove into two loops so that we do not invalidate
        // set iterator.
        Set<String> ids_to_remove = new HashSet<String>();
        for (String prev_switch_id : switch_id_set)
        {
            if (! current_switch_ids.contains(prev_switch_id))
                ids_to_remove.add(prev_switch_id);
        }

        for (String to_remove_switch_id : ids_to_remove)
        {
            current_switch_ids.remove(to_remove_switch_id);
            switch_status_handler.removed_switch(to_remove_switch_id);
        }
        
    }
}