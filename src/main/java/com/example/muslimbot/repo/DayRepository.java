package com.example.muslimbot.repo;

import com.example.muslimbot.entities.DayH2;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DayRepository extends CrudRepository<DayH2, String> {
}
