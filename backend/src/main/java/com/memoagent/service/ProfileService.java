package com.memoagent.service;

import com.memoagent.dto.ProfileUpdateRequest;
import com.memoagent.vo.ProfileVO;

/** 个人偏好（阶段 8.2）：设置页读写，存储从 localStorage 迁移到后端。 */
public interface ProfileService {

    /** 读取当前用户偏好；不存在时自动初始化默认行。 */
    ProfileVO get();

    /** 全量更新当前用户偏好。 */
    ProfileVO update(ProfileUpdateRequest request);
}
