package com.memoagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.memoagent.entity.Profile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProfileMapper extends BaseMapper<Profile> {
}
