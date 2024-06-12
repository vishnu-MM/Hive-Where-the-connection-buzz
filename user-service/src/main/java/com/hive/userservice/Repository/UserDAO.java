package com.hive.userservice.Repository;

import com.hive.userservice.Entity.User;
import com.hive.userservice.Utility.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDAO extends JpaRepository<User,Long> {
    Boolean existsByEmail(String email);
    Boolean existsByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Page<User> findUsersByRole(Role role, Pageable pageable);
    List<User> findUsersByUsernameContaining(String search);
    List<User> findUsersByNameContaining(String search);
}