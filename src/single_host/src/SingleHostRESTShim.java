package single_host;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;

import pronghorn.ShimInterface;
import pronghorn.SwitchStatusHandler;
import pronghorn.RTableUpdate;

/**
   Serves as intermediate layer between Ralph and Floodlight
 */
public class SingleHostRESTShim implements Runnable, ShimInterface
{
    /**
       The port that local floodlight server is running on.
     */
    private int floodlight_port;
    
    /**
       Whenever we see that there was a new switch or we see that a
       switch went down, we notify these handlers. 
     */
    private ReentrantLock handler_lock = new ReentrantLock();
    private Set<SwitchStatusHandler> switch_status_handlers =
        new HashSet<SwitchStatusHandler>();

    private Set<String> switch_id_set = new HashSet<String>();
    private ScheduledExecutorService executor;

    private static final long POLL_PERIOD_MS = 1000;
    
    public SingleHostRESTShim(int _floodlight_port)
    {
        floodlight_port = _floodlight_port;

    }


    /** ShimInterface methods */
    @Override
    public void subscribe_switch_status_handler(SwitchStatusHandler ssh)
    {
        handler_lock.lock();
        switch_status_handlers.add(ssh);
        handler_lock.unlock();
    }
    @Override
    public void unsubscribe_switch_status_handler(SwitchStatusHandler ssh)
    {
        /// FIXME: should clarify that unsubscribe is asynchronous:
        /// may still get one or two messages after.
        handler_lock.lock();
        switch_status_handlers.remove(ssh);
        handler_lock.unlock();
    }
    @Override
    public boolean switch_rtable_updates(
        String switch_id,ArrayList<RTableUpdate> updates)
    {
        long start = System.nanoTime();
        for (RTableUpdate update : updates)
        {
            String rtable_update_json =
                rtable_update_to_json(switch_id, update);

            if (update.op == RTableUpdate.Operation.INSERT)
            {
                String update_resource = "/wm/staticflowentrypusher/json";
                issue_post(update_resource,rtable_update_json);
            }
            else
            {
                String update_resource = "/wm/staticflowentrypusher/json/delete";
                issue_post(update_resource,rtable_update_json);
            }
        }
        String ret = issue_post("/wm/pronghorn/switch/" + switch_id + "/barrier/json", "");
        System.out.println(System.nanoTime()-start);
        return ret.equals("true");
    }

    @Override
    public void start()
    {
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

    private String issue_get(String what_to_get)
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

    private String issue_post(String resource,String data)
    {
        String result = "";
        try {
            HttpURLConnection connection = null;
            URL url = new URL(
                "http","localhost",floodlight_port,resource);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            
            // data to post to connection;
            DataOutputStream wr =
                new DataOutputStream(connection.getOutputStream ());
            wr.writeBytes(data);
            wr.flush();
            wr.close();
            
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
            switch_ids.add(m.group(1));

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

                // FIXME: can lead to iterator invalidation if a
                // switch unsubscribes in response to a new_switch
                // message.
                handler_lock.lock();
                for (SwitchStatusHandler ssh : switch_status_handlers)
                    ssh.new_switch(current_switch_id);
                handler_lock.unlock();
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
            switch_id_set.remove(to_remove_switch_id);
            handler_lock.lock();
            // FIXME: can lead to iterator invalidation if a
            // switch unsubscribes in response to a new_switch
            // message.
            for (SwitchStatusHandler ssh : switch_status_handlers)
                ssh.removed_switch(to_remove_switch_id);
            handler_lock.unlock();
        }
    }

    /**
       Take each update and convert it to json that can be sent as
       part of REST command.
     */
    public String rtable_update_to_json(String switch_id, RTableUpdate to_translate)
    {
        String translation = null;
        if (to_translate.op == RTableUpdate.Operation.REMOVE)
        {
            translation =
                String.format("{\"name\":\"%s\"}",to_translate.entry_name);
        }
        else if (to_translate.op == RTableUpdate.Operation.INSERT)
        {
            String entry_name = to_translate.entry_name;
            String active = "true";
            String actions = to_translate.action;
            translation =
                String.format(
                    "{\"switch\":\"%s\", \"name\":\"%s\",\"active\":\"%s\",\"actions\":\"%s\"}",
                    switch_id,entry_name,active,actions);
        }
        // DEBUG
        else
        {
            System.out.println("Unkown update operation.");
            assert(false);
        }
        // END DEBUG

        return translation;
    }
    
}