package com.campus.sportsbuddy.service;

import com.campus.sportsbuddy.dto.ProfileUpdateRequest;
import com.campus.sportsbuddy.dto.UserResponse;
import com.campus.sportsbuddy.model.SkillLevel;
import com.campus.sportsbuddy.model.User;
import com.campus.sportsbuddy.repository.UserRepository;
import com.campus.sportsbuddy.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(currentUser());
    }

    @Transactional
    public UserResponse updateProfile(ProfileUpdateRequest request) {
        User user = currentUser();
        user.setDisplayName(request.displayName().trim());
        user.setSportPreferences(join(request.sportPreferences()));
        user.setSkillLevel(request.skillLevel() == null
                ? SkillLevel.BEGINNER : request.skillLevel());
        user.setCampusZone(request.campusZone().trim());
        user.setAvailableTimeSlots(join(request.availableTimeSlots()));
        if (request.latitude() != null && request.longitude() != null) {
            user.setLatitude(request.latitude());
            user.setLongitude(request.longitude());
        }
        return UserResponse.from(userRepository.save(user));
    }

    public User currentUser() {
        return userRepository.findByUsername(SecurityUtils.currentUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    private String join(java.util.List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
