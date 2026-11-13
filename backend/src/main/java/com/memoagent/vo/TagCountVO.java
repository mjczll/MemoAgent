package com.memoagent.vo;

import lombok.Data;

/**
 * 标签及其使用次数，供日记页的筛选条渲染。
 */
@Data
public class TagCountVO {

    private String name;

    private long count;
}
