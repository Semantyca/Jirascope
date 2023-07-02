package com.semantyca.service;

import com.semantyca.dto.document.UserDTO;
import com.semantyca.model.user.AnonymousUser;
import com.semantyca.model.user.User;
import com.semantyca.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger("UserService");
    @Inject
    private UserRepository repository;

    public List<User> getAll() {
        return repository.getAllUsers(100, 0);
    }

    public User get(String id) {
        return repository.findById(UUID.fromString(id));
    }

    public Long add(UserDTO userDTO) {
        User user = new User.Builder()
                .setLogin(userDTO.login())
                .setPwd(userDTO.pwd())
                .setEmail(userDTO.email())
                .build();
        return repository.insert(user, AnonymousUser.ID);
    }

    public User update(UserDTO userDTO) {
        User user = new User.Builder()
                .setLogin(userDTO.login())
                .setPwd(userDTO.pwd())
                .setEmail(userDTO.email())
                .build();
        return repository.update(user);
    }
}
