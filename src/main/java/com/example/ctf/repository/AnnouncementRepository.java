package com.example.ctf.repository;

import com.example.ctf.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, String> {

    List<Announcement> findAllByOrderByCreatedAtDesc();
}
