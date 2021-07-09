package com.xiangxue.jack.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RouteBean {
    private int tableCount;

    //如果要用id查询的话，需要设置该值
    private Long primaryId;
}
