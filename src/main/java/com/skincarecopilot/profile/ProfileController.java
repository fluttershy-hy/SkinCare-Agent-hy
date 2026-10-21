package com.skincarecopilot.profile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @PutMapping("/{userId}")
    public Profile save(@PathVariable String userId, @RequestBody ProfileUpdateRequest request) {
        return profiles.saveConfirmed(userId, request);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Profile> get(@PathVariable String userId) {
        return profiles.find(userId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProfileError> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ProfileError(exception.getMessage()));
    }

    public record ProfileError(String error) {
    }
}
