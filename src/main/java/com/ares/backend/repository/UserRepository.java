package com.ares.backend.repository;

import com.ares.backend.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    // Find user by email (for login)
    Optional<User> findByEmail(String email);

    // Find user by phone
    Optional<User> findByPhone(String phone);

    // Find user by OAuth provider ID (Google/GitHub)
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    // Check if email already registered
    boolean existsByEmail(String email);
}