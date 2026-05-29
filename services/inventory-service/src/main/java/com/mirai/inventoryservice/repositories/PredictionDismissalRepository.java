package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.PredictionDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PredictionDismissalRepository extends JpaRepository<PredictionDismissal, UUID> {

    /**
     * Active dismissals: dismissed within the lookback window. Older rows are
     * ignored at read time, which gives us the "auto-archive after 30 days"
     * behavior without a cleanup cron.
     */
    @Query("SELECT pd FROM PredictionDismissal pd WHERE pd.dismissedAt > :cutoff ORDER BY pd.dismissedAt DESC")
    List<PredictionDismissal> findActiveSince(java.time.OffsetDateTime cutoff);

    /**
     * Hard delete by item_id. The PK lookup is the same as
     * {@link JpaRepository#deleteById}, but exposed as an explicit method so
     * the service layer reads cleanly.
     */
    void deleteByItemId(UUID itemId);

    static OffsetDateTime defaultCutoff() {
        return OffsetDateTime.now().minusDays(30);
    }
}
