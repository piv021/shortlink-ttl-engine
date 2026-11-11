package ru.promoit.shortener.infra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.promoit.shortener.core.port.NotificationPort;

public class InMemoryNotificationAdapter implements NotificationPort {
    private final Map<UUID, List<String>> queues = new ConcurrentHashMap<>();

    @Override
    public void notifyUser(UUID userUuid, String message) {
        // JEP 456: использование '_' вместо неиспользуемого ключа в лямбде
        queues.computeIfAbsent(userUuid, _ -> new CopyOnWriteArrayList<>()).add(message);
        System.out.println("\n[УВЕДОМЛЕНИЕ для пользователя " + userUuid + "]: " + message);
    }

    @Override
    public List<String> consumeUserNotifications(UUID userUuid) {
        List<String> list = queues.remove(userUuid);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }
}
