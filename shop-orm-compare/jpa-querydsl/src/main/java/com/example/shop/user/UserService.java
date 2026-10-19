package com.example.shop.user;

import com.example.shop.common.error.DuplicateEmailException;
import com.example.shop.common.error.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true) // 기본은 레플리카. 쓰기 메서드만 @Transactional로 소스
public class UserService {

    private final UserJpaRepository userRepository;

    public UserService(UserJpaRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 이메일 중복이면 409. IDENTITY라 save()가 곧바로 INSERT를 보내고, UNIQUE 위반은 Spring이 DataIntegrityViolationException으로 번역한다.
     * 이 예외가 Spring Data 리포지토리(@Transactional)를 지나며 바깥 트랜잭션은 rollback-only가 된다 → 이어서 쓰기 불가 (보고서 01-11)
     */
    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        try {
            return UserResponse.from(userRepository.saveAndFlush(new User(request.email(), request.name())));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException(request.email());
        }
    }

    public UserResponse get(long id) {
        return userRepository.findById(id).map(UserResponse::from).orElseThrow(() -> new NotFoundException("User", id));
    }
}
