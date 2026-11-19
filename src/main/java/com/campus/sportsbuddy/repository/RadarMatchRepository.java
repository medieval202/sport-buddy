package com.campus.sportsbuddy.repository;

import com.campus.sportsbuddy.model.RadarMatch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RadarMatchRepository extends JpaRepository<RadarMatch, Long> {

    @EntityGraph(attributePaths = {"requester", "candidate"})
    @Query("select m from RadarMatch m where m.id = :id")
    Optional<RadarMatch> findDetailById(@Param("id") Long id);
}
