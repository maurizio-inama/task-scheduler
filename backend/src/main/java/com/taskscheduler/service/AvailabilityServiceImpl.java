package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Availability;
import com.taskscheduler.domain.repository.AvailabilityRepository;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;

    public AvailabilityServiceImpl(
            AvailabilityRepository availabilityRepository,
            UserRepository userRepository) {
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Availability create(Availability availability) {
        validate(availability);

        Long userId = availability.getUser().getId();

        if (userId == null || !userRepository.existsById(userId)) {
            throw new EntityNotFoundException(
                    "User not found: " + userId
            );
        }

        return availabilityRepository.save(availability);
    }

    @Override
    public Availability getById(Long id) {
        return availabilityRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Availability not found: " + id
                        )
                );
    }

    @Override
    public List<Availability> getAll() {
        return availabilityRepository.findAll();
    }

    @Override
    public Availability update(Long id, Availability availability) {
        Availability existing = getById(id);

        validate(availability);

        Long userId = availability.getUser().getId();

        if (userId == null || !userRepository.existsById(userId)) {
            throw new EntityNotFoundException(
                    "User not found: " + userId
            );
        }

        existing.setUser(availability.getUser());
        existing.setStartDateTime(availability.getStartDateTime());
        existing.setEndDateTime(availability.getEndDateTime());

        return availabilityRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        Availability availability = getById(id);
        availabilityRepository.delete(availability);
    }

    private void validate(Availability availability) {

        if (availability == null) {
            throw new ValidationException(
                    "Availability must not be null"
            );
        }

        if (availability.getUser() == null) {
            throw new ValidationException(
                    "User must not be null"
            );
        }

        if (availability.getStartDateTime() == null) {
            throw new ValidationException(
                    "Start date/time must not be null"
            );
        }

        if (availability.getEndDateTime() == null) {
            throw new ValidationException(
                    "End date/time must not be null"
            );
        }

        if (!availability.getStartDateTime()
                .isBefore(availability.getEndDateTime())) {
            throw new ValidationException(
                    "Start date/time must be before end date/time"
            );
        }
    }
}