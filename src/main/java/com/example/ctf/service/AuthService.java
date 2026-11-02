package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.Role;
import com.example.ctf.domain.User;
import com.example.ctf.dto.AuthDtos;
import com.example.ctf.repository.UserRepository;
import com.example.ctf.security.JwtService;
import com.example.ctf.security.UserPrincipal;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final DtoMapper mapper;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService, DtoMapper mapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        try {
            UserPrincipal principal = (UserPrincipal) authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()))
                    .getPrincipal();
            User user = requireUser(principal.getId());
            return response(user);
        } catch (AccountStatusException ex) {
            // 封禁账号会触发 DisabledException / LockedException，统一返回“账号已被封禁”
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        } catch (BadCredentialsException ex) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
    }

    @Transactional
    public AuthDtos.LoginResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "用户名已被占用");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? request.username() : request.displayName().trim());
        user.setRole(Role.USER);
        user.setBanned(false);
        userRepository.save(user);
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserDto me(UserPrincipal principal) {
        return mapper.userDto(requireUser(principal.getId()));
    }

    public User requireUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));
    }

    private AuthDtos.LoginResponse response(User user) {
        String token = jwtService.createToken(user);
        return new AuthDtos.LoginResponse(token, jwtService.expiresInSeconds(), mapper.userDto(user));
    }
}
