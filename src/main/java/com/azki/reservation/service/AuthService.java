package com.azki.reservation.service;

import com.azki.reservation.domain.user.User;
import com.azki.reservation.dto.request.LoginRequest;
import com.azki.reservation.dto.request.RegisterRequest;
import com.azki.reservation.dto.response.AuthResponse;
import com.azki.reservation.exception.UserAlreadyExistsException;
import com.azki.reservation.repository.UserRepository;
import com.azki.reservation.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        return AuthResponse.bearer(jwtService.generateToken(user.getId(), user.getUsername()));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return AuthResponse.bearer(jwtService.generateToken(user.getId(), user.getUsername()));
    }
}
