package com.memoagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.memoagent.entity.Diary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {
}
