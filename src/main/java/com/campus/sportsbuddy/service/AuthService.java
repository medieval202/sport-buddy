package com.campus.sportsbuddy.service;

import com.campus.sportsbuddy.dto.AuthResponse;
import com.campus.sportsbuddy.dto.LoginRequest;
import com.campus.sportsbuddy.dto.RegisterRequest;
import com.campus.sportsbuddy.dto.UserResponse;
import com.campus.sportsbuddy.model.Role;
import com.campus.sportsbuddy.model.SkillLevel;
import com.campus.sportsbuddy.model.User;
import com.campus.sportsbuddy.repository.UserRepository;
import com.campus.sportsbuddy.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setRole(Role.STUDENT);
        user.setSportPreferences(join(request.sportPreferences()));
        user.setSkillLevel(request.skillLevel() == null
                ? SkillLevel.BEGINNER : request.skillLevel());
        user.setCampusZone(request.campusZone().trim());
        user.setAvailableTimeSlots(join(request.availableTimeSlots()));
        user.setLatitude(request.latitude() == null ? 31.2304 : request.latitude());
        user.setLongitude(request.longitude() == null ? 121.4737 : request.longitude());
        user.setCreditScore(100);
        user.setEnabled(true);
        userRepository.save(user);

        return issueToken(user);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username().trim(),
                            request.password()
                    )
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        User user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        return new AuthResponse(jwtService.generateToken(userDetails), UserResponse.from(user));
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
