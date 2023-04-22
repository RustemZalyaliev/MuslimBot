package com.example.muslimbot.repo;

import com.example.muslimbot.entities.Muslim;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MuslimRepository extends CrudRepository<Muslim, Long> {
}
