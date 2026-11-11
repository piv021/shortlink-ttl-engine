package ru.promoit.shortener.infra;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import ru.promoit.shortener.core.model.ShortLink;
import ru.promoit.shortener.core.port.LinkRepository;

public class InMemoryLinkRepository implements LinkRepository {
    private final Map<String, ShortLink> storage = new ConcurrentHashMap<>();

    @Override
    public void save(ShortLink link) {
        storage.put(link.getShortUrl(), link);
    }

    @Override
    public Optional<ShortLink> findByShortUrl(String shortUrl) {
        return Optional.ofNullable(storage.get(shortUrl));
    }

    @Override
    public List<ShortLink> findByOwnerUuid(UUID ownerUuid) {
        return storage.values().stream()
                .filter(link -> link.getOwnerUuid().equals(ownerUuid))
                .toList();
    }

    @Override
    public Collection<ShortLink> findAll() {
        return storage.values();
    }

    @Override
    public boolean deleteByShortUrl(String shortUrl) {
        return storage.remove(shortUrl) != null;
    }

    @Override
    public boolean existsByShortUrl(String shortUrl) {
        return storage.containsKey(shortUrl);
    }
}
