package com.taskscheduler.service;

import java.util.List;

import com.taskscheduler.domain.entity.User;

public interface UserService {

    User create(User user);

    User getById(Long id);

    List<User> getAll();

    User update(Long id, User user);

    void delete(Long id);
}
