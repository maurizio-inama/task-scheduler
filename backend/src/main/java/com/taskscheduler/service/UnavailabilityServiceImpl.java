package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.repository.UnavailabilityRepository;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UnavailabilityServiceImpl implements UnavailabilityService {

    private final UnavailabilityRepository unavailabilityRepository;
    private final UserRepository userRepository;

    public UnavailabilityServiceImpl(
            UnavailabilityRepository unavailabilityRepository,
            UserRepository userRepository) {

        this.unavailabilityRepository = unavailabilityRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Unavailability create(Unavailability unavailability) {

        validate(unavailability);

        Long userId = unavailability.getUser().getId();

        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException(
                    "User not found: " + userId
            );
        }

        if (hasOverlap(unavailability, userId)) {
            throw new BusinessRuleException(
                    "Unavailability overlaps an existing unavailability"
            );
        }

        return unavailabilityRepository.save(unavailability);
    }

    @Override
    public Unavailability getById(Long id) {

        return unavailabilityRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Unavailability not found: " + id
                        )
                );
    }

    @Override
    public List<Unavailability> getAll() {
        return unavailabilityRepository.findAll();
    }

    @Override
    public Unavailability update(
            Long id,
            Unavailability unavailability) {

        Unavailability existing = getById(id);

        validate(unavailability);

        Long userId = unavailability.getUser().getId();

        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException(
                    "User not found: " + userId
            );
        }

        if (unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThanAndIdNot(
                        userId,
                        unavailability.getEndDateTime(),
                        unavailability.getStartDateTime(),
                        id)) {

            throw new BusinessRuleException(
                    "Unavailability overlaps an existing unavailability"
            );
        }

        existing.setUser(unavailability.getUser());
        existing.setStartDateTime(unavailability.getStartDateTime());
        existing.setEndDateTime(unavailability.getEndDateTime());
        existing.setReason(unavailability.getReason());

        return unavailabilityRepository.save(existing);
    }

    @Override
    public void delete(Long id) {

        Unavailability existing = getById(id);

        unavailabilityRepository.delete(existing);
    }

    private boolean hasOverlap(
            Unavailability unavailability,
            Long userId) {

        return unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        userId,
                        unavailability.getEndDateTime(),
                        unavailability.getStartDateTime()
                );
    }

    private void validate(Unavailability unavailability) {

        if (unavailability == null) {
            throw new ValidationException(
                    "Unavailability must not be null"
            );
        }

        if (unavailability.getUser() == null) {
            throw new ValidationException(
                    "User must not be null"
            );
        }

        LocalDateTime start =
                unavailability.getStartDateTime();

        LocalDateTime end =
                unavailability.getEndDateTime();

        if (start == null) {
            throw new ValidationException(
                    "Start date time must not be null"
            );
        }

        if (end == null) {
            throw new ValidationException(
                    "End date time must not be null"
            );
        }

        if (!start.isBefore(end)) {
            throw new ValidationException(
                    "Start date time must be before end date time"
            );
        }
    }
}
