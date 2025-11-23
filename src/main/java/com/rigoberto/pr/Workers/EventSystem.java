package com.rigoberto.pr.Workers;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.rigoberto.pr.Repositories.PostgreSQLEventRepository;
import org.json.JSONObject;

import java.util.concurrent.Executors;

public class EventSystem {

    private final EventBus eventBus;
    private final PostgreSQLEventRepository repo;
    private final EventWorker worker;

    public EventSystem(String jdbcUrl, String user, String pwd) throws Exception {

        this.eventBus = new AsyncEventBus(
                "persistent-eventbus",
                Executors.newCachedThreadPool()
        );

        this.repo = new PostgreSQLEventRepository(jdbcUrl, user, pwd);

        this.worker = new EventWorker(repo, eventBus, 5);

        worker.start();
    }

    public void registerListener(Object listener) {
        eventBus.register(listener);
    }

    public void post(Object event) throws Exception {
        // Serializar usando org.json
        JSONObject json = new JSONObject(event);
        String payload = json.toString();
        String type = event.getClass().getName();
        repo.saveEvent(type, payload, 5);
    }
}

