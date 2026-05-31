package com.gmailai.coreservice.service;

import com.gmailai.coreservice.model.User;
import com.gmailai.coreservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EncryptionUtils encryptionUtils;

    public UserService(UserRepository userRepository, EncryptionUtils encryptionUtils) {
        this.userRepository = userRepository;
        this.encryptionUtils = encryptionUtils;
    }

    public User saveOrUpdateUser(String email, String refreshToken) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        User user = existingUser.orElse(new User());
        user.setEmail(email);
        
        if (refreshToken != null) {
            user.setEncryptedRefreshToken(encryptionUtils.encrypt(refreshToken));
        }
        return userRepository.save(user);
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User updatePreferences(UUID id, User.Tone tone, String signature, Boolean autoApprove) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        if (tone != null) user.setPreferredTone(tone);
        if (signature != null) user.setSignature(signature);
        if (autoApprove != null) user.setAutoApprove(autoApprove);

        return userRepository.save(user);
    }

    public String decryptUserToken(User user) {
        if (user.getEncryptedRefreshToken() == null) return null;
        return encryptionUtils.decrypt(user.getEncryptedRefreshToken());
    }
}