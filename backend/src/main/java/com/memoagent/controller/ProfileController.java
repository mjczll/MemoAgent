package com.memoagent.controller;

import com.memoagent.common.Result;
import com.memoagent.dto.ProfileUpdateRequest;
import com.memoagent.service.ProfileService;
import com.memoagent.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人偏好：阶段 8.2，设置页读写。 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public Result<ProfileVO> get() {
        return Result.ok(profileService.get());
    }

    @PutMapping
    public Result<ProfileVO> update(@RequestBody ProfileUpdateRequest request) {
        return Result.ok(profileService.update(request));
    }
}
