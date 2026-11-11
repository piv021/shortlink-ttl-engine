package ru.promoit.shortener.core.port;

import java.util.List;
import java.util.UUID;

public interface NotificationPort {
    void notifyUser(UUID userUuid, String message);

    List<String> consumeUserNotifications(UUID userUuid);
}