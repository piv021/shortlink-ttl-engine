package ru.promoit.shortener.core.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.promoit.shortener.core.model.ShortLink;

public interface LinkRepository {
    void save(ShortLink link);

    Optional<ShortLink> findByShortUrl(String shortUrl);

    List<ShortLink> findByOwnerUuid(UUID ownerUuid);

    Collection<ShortLink> findAll();

    boolean deleteByShortUrl(String shortUrl);

    boolean existsByShortUrl(String shortUrl);
}
