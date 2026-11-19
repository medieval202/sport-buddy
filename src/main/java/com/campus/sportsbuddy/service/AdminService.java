package com.campus.sportsbuddy.service;

import com.campus.sportsbuddy.dto.CreditUpdateRequest;
import com.campus.sportsbuddy.dto.UserResponse;
import com.campus.sportsbuddy.model.Role;
import com.campus.sportsbuddy.model.User;
import com.campus.sportsbuddy.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> students() {
        return userRepository.findByRoleOrderByCreatedAtDesc(Role.STUDENT)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateStudent(Long userId, CreditUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能管理学生账户");
        }
        if (request.creditScore() != null) {
            user.setCreditScore(request.creditScore());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        return UserResponse.from(userRepository.save(user));
    }
}
