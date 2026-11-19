package pl.ecommanager.integrations.allegro.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AllegroConnectionRepository extends JpaRepository<AllegroConnection, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from AllegroConnection connection where connection.id = 1")
    AllegroConnection lockConnection();
}
