package com.taskscheduler.service;

import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User create(User user) {
        validate(user);

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new BusinessRuleException(
                "Username already exists: " + user.getUsername()
            );
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new BusinessRuleException(
                "Email already exists: " + user.getEmail()
            );
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user);
    }

    @Override
    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() ->
                new EntityNotFoundException("User not found: " + id)
            );
    }

    @Override
    public List<User> getAll() {
        return userRepository.findAll();
    }

    @Override
    public User update(Long id, User user) {
        User existing = getById(id);

        validate(user);

        if (!existing.getUsername().equals(user.getUsername())
                && userRepository.existsByUsername(user.getUsername())) {
            throw new BusinessRuleException(
                "Username already exists: " + user.getUsername()
            );
        }

        if (!existing.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(user.getEmail())) {
            throw new BusinessRuleException(
                "Email already exists: " + user.getEmail()
            );
        }

        existing.setUsername(user.getUsername());
        existing.setPassword(passwordEncoder.encode(user.getPassword()));
        existing.setFirstName(user.getFirstName());
        existing.setLastName(user.getLastName());
        existing.setEmail(user.getEmail());
        existing.setRole(user.getRole());
        existing.setEnabled(user.isEnabled());

        return userRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        User user = getById(id);
        userRepository.delete(user);
    }

    private void validate(User user) {

        if (user == null) {
            throw new ValidationException("User must not be null");
        }

        if (isBlank(user.getUsername())) {
            throw new ValidationException("Username must not be blank");
        }

        if (isBlank(user.getPassword())) {
            throw new ValidationException("Password must not be blank");
        }

        if (isBlank(user.getFirstName())) {
            throw new ValidationException("First name must not be blank");
        }

        if (isBlank(user.getLastName())) {
            throw new ValidationException("Last name must not be blank");
        }

        if (isBlank(user.getEmail())) {
            throw new ValidationException("Email must not be blank");
        }

        if (!user.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Invalid email format");
        }

        if (user.getRole() == null) {
            throw new ValidationException("Role must not be null");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}