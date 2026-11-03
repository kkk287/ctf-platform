package com.example.ctf.repository;

import com.example.ctf.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {

    List<Event> findAllByOrderByCreatedAtDesc();

    boolean existsByNamespace(String namespace);

    boolean existsByNamespaceAndIdNot(String namespace, String id);
}
