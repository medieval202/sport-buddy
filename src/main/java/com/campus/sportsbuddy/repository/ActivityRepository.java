package com.campus.sportsbuddy.repository;

import com.campus.sportsbuddy.model.Activity;
import com.campus.sportsbuddy.model.ActivityStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    @EntityGraph(attributePaths = {"creator", "participants"})
    List<Activity> findByStatusOrderByStartTimeAsc(ActivityStatus status);

    @EntityGraph(attributePaths = {"creator", "participants"})
    List<Activity> findByStatusOrderByCreatedAtDesc(ActivityStatus status);

    @EntityGraph(attributePaths = {"creator", "participants"})
    @Query("""
            select distinct a from Activity a
            left join a.participants p
            where a.creator.id = :userId or p.id = :userId
            order by a.createdAt desc
            """)
    List<Activity> findMine(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"creator", "participants"})
    @Query("select a from Activity a where a.id = :id")
    Optional<Activity> findDetailById(@Param("id") Long id);
}
