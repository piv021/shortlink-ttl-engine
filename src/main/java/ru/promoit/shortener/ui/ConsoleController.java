package ru.promoit.shortener.ui;

import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import ru.promoit.shortener.core.model.ShortLink;
import ru.promoit.shortener.core.port.NotificationPort;
import ru.promoit.shortener.core.service.UrlShortenerService;

public class ConsoleController {
    private final UrlShortenerService service;
    private final NotificationPort notificationPort;
    private UUID currentUserUuid;

    public ConsoleController(UrlShortenerService service, NotificationPort notificationPort) {
        this.service = service;
        this.notificationPort = notificationPort;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Сервис сокращения ссылок PROMO IT (Java 25 LTS) ===");
        printHelp();

        while (true) {
            System.out.print("\nВведите команду (или 'help'): ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "shorten" -> handleShorten(parts);
                    case "open" -> handleOpen(parts);
                    case "my-links" -> handleMyLinks();
                    case "edit-limit" -> handleEditLimit(parts);
                    case "delete" -> handleDelete(parts);
                    case "whoami" -> handleWhoAmI();
                    case "switch-user" -> handleSwitchUser(parts);
                    case "new-user" -> {
                        currentUserUuid = UUID.randomUUID();
                        System.out.println("Создан новый пользователь с UUID: " + currentUserUuid);
                    }
                    case "notifications" -> handleNotifications();
                    case "help" -> printHelp();
                    case "exit" -> {
                        System.out.println("Завершение работы сервиса.");
                        return;
                    }
                    // Guarded Pattern Matching (JEP 441): прямой ввод короткой ссылки в консоль
                    case String s when s.contains("/") -> {
                        String target = service.navigate(parts[0]);
                        System.out.println("Перенаправление на: " + target);
                    }
                    default -> System.out.println("Неизвестная команда. Введите 'help' для списка доступных команд.");
                }
            } catch (NumberFormatException _) {
                System.out.println("[ОШИБКА]: Ожидалось целое число для лимита переходов.");
            } catch (IllegalArgumentException e) {
                System.out.println("[ОШИБКА ВАЛИДАЦИИ]: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("[ОШИБКА]: " + e.getMessage());
            }
        }
    }

    private void ensureUserInitialized() {
        if (currentUserUuid == null) {
            currentUserUuid = UUID.randomUUID();
            System.out.println("Вам автоматически присвоен UUID: " + currentUserUuid);
            System.out.println("Сохраните его для управления своими ссылками.");
        }
    }

    private void handleShorten(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Использование: shorten [длинная_ссылка] [опционально: лимит_переходов]");
            return;
        }
        ensureUserInitialized();
        Integer limit = parts.length >= 3 ? Integer.parseInt(parts[2]) : null;
        ShortLink link = service.createShortLink(parts[1], currentUserUuid, limit);
        System.out.println("Короткая ссылка создана: " + link.getShortUrl());
        System.out.println("Лимит переходов: " + link.getMaxClicks() + " | Действует до: " + link.getExpiresAt());
    }

    private void handleOpen(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Использование: open [короткая_ссылка]");
            return;
        }
        String original = service.navigate(parts[1]);
        System.out.println("Выполнен переход на: " + original);
    }

    private void handleMyLinks() {
        if (currentUserUuid == null) {
            System.out.println("У вас пока нет активного UUID. Создайте ссылку или выполните 'switch-user'.");
            return;
        }
        List<ShortLink> links = service.getUserLinks(currentUserUuid);
        if (links.isEmpty()) {
            System.out.println("У вас нет активных ссылок.");
            return;
        }
        System.out.println("Ваши ссылки (UUID " + currentUserUuid + "):");
        for (ShortLink l : links) {
            System.out.println(" - %s -> %s [Переходы: %d/%d, Истекает: %s]"
                    .formatted(
                            l.getShortUrl(),
                            l.getOriginalUrl(),
                            l.getCurrentClicks(),
                            l.getMaxClicks(),
                            l.getExpiresAt()));
        }
    }

    private void handleEditLimit(String[] parts) {
        if (parts.length < 3) {
            System.out.println("Использование: edit-limit [короткая_ссылка] [новый_лимит]");
            return;
        }
        ensureUserInitialized();
        int newLimit = Integer.parseInt(parts[2]);
        service.updateMaxClicks(parts[1], currentUserUuid, newLimit);
        System.out.println("Лимит для ссылки " + parts[1] + " успешно обновлен до " + newLimit);
    }

    private void handleDelete(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Использование: delete [короткая_ссылка]");
            return;
        }
        ensureUserInitialized();
        service.deleteLink(parts[1], currentUserUuid);
        System.out.println("Ссылка " + parts[1] + " успешно удалена.");
    }

    private void handleWhoAmI() {
        if (currentUserUuid == null) {
            System.out.println("UUID еще не сгенерирован (создайте первую ссылку или введите 'new-user').");
        } else {
            System.out.println("Текущий UUID пользователя: " + currentUserUuid);
        }
    }

    private void handleSwitchUser(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Использование: switch-user [UUID]");
            return;
        }
        currentUserUuid = UUID.fromString(parts[1]);
        System.out.println("Вы переключились на пользователя: " + currentUserUuid);
    }

    private void handleNotifications() {
        if (currentUserUuid == null) {
            System.out.println("Сначала авторизуйтесь по UUID или создайте ссылку.");
            return;
        }
        List<String> notes = notificationPort.consumeUserNotifications(currentUserUuid);
        if (notes.isEmpty()) {
            System.out.println("Новых уведомлений нет.");
        } else {
            notes.forEach(n -> System.out.println(" * " + n));
        }
    }

    private void printHelp() {
        System.out.println("""
                Доступные команды:
                  shorten [URL] [лимит]         - Сократить ссылку (UUID создается автоматически при первом вызове)
                  open [короткая_ссылка]        - Перейти по короткой ссылке в браузере (можно просто вставить короткую ссылку)
                  my-links                      - Показать все активные ссылки текущего пользователя
                  edit-limit [ссылка] [лимит]   - Изменить лимит переходов (только для владельца)
                  delete [короткая_ссылка]      - Удалить короткую ссылку (только для владельца)
                  whoami                        - Показать текущий UUID
                  new-user                      - Сгенерировать новый UUID (режим нового пользователя)
                  switch-user [UUID]            - Переключиться на существующий UUID
                  notifications                 - Просмотреть историю уведомлений текущего пользователя
                  help                          - Показать эту справку
                  exit                          - Выйти из программы""");
    }
}
