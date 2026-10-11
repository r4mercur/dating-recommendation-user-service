package com.bjarne.datingrecommendationsuserservice.moderation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByReferenceId(String referenceId);

    boolean existsByReporterIdAndReportedIdAndStatus(Long reporterId, Long reportedId, ReportStatus status);

    List<Report> findAllByReportedIdAndStatus(Long reportedId, ReportStatus status);

    /** The moderation queue: oldest first, so nothing waits forever. */
    @Query("select r from Report r join fetch r.reported left join fetch r.reporter where r.status = :status order by r.createdAt asc")
    List<Report> findQueue(ReportStatus status);

    @Query("select r.reported.id, count(r) from Report r where r.reported.id in :userIds and r.status = :status group by r.reported.id")
    List<Object[]> countByReported(Collection<Long> userIds, ReportStatus status);

    @Modifying
    @Query("delete from Report r where r.reporter.id in :userIds or r.reported.id in :userIds")
    void deleteAllInvolving(Collection<Long> userIds);
}
