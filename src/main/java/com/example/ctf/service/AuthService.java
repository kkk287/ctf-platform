package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.Role;
import com.example.ctf.domain.User;
import com.example.ctf.dto.AuthDtos;
import com.example.ctf.repository.UserRepository;
import com.example.ctf.security.JwtService;
import com.example.ctf.security.TokenRegistry;
import com.example.ctf.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
    private final TokenRegistry tokenRegistry;
    private final DtoMapper mapper;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       TokenRegistry tokenRegistry, DtoMapper mapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tokenRegistry = tokenRegistry;
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

    /**
     * 登出：按剩余有效期吊销当前令牌。令牌缺失或已失效时幂等返回，不抛错。
     */
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        try {
            Claims claims = jwtService.parse(authorizationHeader.substring(7));
            tokenRegistry.revoke(claims.getId(), jwtService.remainingSeconds(claims));
        } catch (JwtException ex) {
            // 令牌已失效，无需吊销
        }
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
