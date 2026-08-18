package com.ayushi.DailyTaskManagementSystem;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ayushi.DailyTaskManagementSystem.model.Task;
import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByDueDate(LocalDate date);
}
