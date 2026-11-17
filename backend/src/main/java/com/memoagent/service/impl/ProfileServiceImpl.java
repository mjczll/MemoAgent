package com.memoagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.memoagent.common.CurrentUser;
import com.memoagent.dto.ProfileUpdateRequest;
import com.memoagent.entity.Profile;
import com.memoagent.mapper.ProfileMapper;
import com.memoagent.service.ProfileService;
import com.memoagent.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final String DEFAULT_DISPLAY_NAME = "未命名";
    private static final int DEFAULT_REVIEW_DAY = 0;
    private static final String DEFAULT_REVIEW_TIME = "21:30";

    private final ProfileMapper profileMapper;

    @Override
    public ProfileVO get() {
        Profile profile = requireProfile();
        return toVO(profile);
    }

    @Override
    @Transactional
    public ProfileVO update(ProfileUpdateRequest request) {
        Profile profile = requireProfile();
        applyRequest(profile, request);
        profileMapper.updateById(profile);
        return toVO(profile);
    }

    /** 偏好行不存在时按默认值初始化。 */
    private Profile requireProfile() {
        Long userId = CurrentUser.id();
        Profile profile = profileMapper.selectById(userId);
        if (profile != null) {
            return profile;
        }
        profile = new Profile();
        profile.setUserId(userId);
        profile.setDisplayName(DEFAULT_DISPLAY_NAME);
        profile.setSignature("");
        profile.setReviewDay(DEFAULT_REVIEW_DAY);
        profile.setReviewTime(DEFAULT_REVIEW_TIME);
        profile.setAutoExtract(true);
        profile.setWeeklyReview(true);
        profileMapper.insert(profile);
        return profile;
    }

    private void applyRequest(Profile profile, ProfileUpdateRequest request) {
        if (request == null) {
            return;
        }
        if (request.getDisplayName() != null && StringUtils.hasText(request.getDisplayName())) {
            profile.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getSignature() != null) {
            profile.setSignature(request.getSignature().trim());
        }
        if (request.getReviewDay() != null) {
            int day = Math.max(0, Math.min(request.getReviewDay(), 6));
            profile.setReviewDay(day);
        }
        if (request.getReviewTime() != null) {
            profile.setReviewTime(request.getReviewTime().trim());
        }
        if (request.getAutoExtract() != null) {
            profile.setAutoExtract(request.getAutoExtract());
        }
        if (request.getWeeklyReview() != null) {
            profile.setWeeklyReview(request.getWeeklyReview());
        }
    }

    private ProfileVO toVO(Profile profile) {
        ProfileVO vo = new ProfileVO();
        vo.setDisplayName(profile.getDisplayName());
        vo.setSignature(profile.getSignature());
        vo.setReviewDay(profile.getReviewDay() == null ? DEFAULT_REVIEW_DAY : profile.getReviewDay());
        vo.setReviewTime(StringUtils.hasText(profile.getReviewTime()) ? profile.getReviewTime() : DEFAULT_REVIEW_TIME);
        vo.setAutoExtract(Boolean.TRUE.equals(profile.getAutoExtract()));
        vo.setWeeklyReview(Boolean.TRUE.equals(profile.getWeeklyReview()));
        return vo;
    }
}
