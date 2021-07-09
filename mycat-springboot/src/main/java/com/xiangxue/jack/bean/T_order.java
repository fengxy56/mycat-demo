package com.xiangxue.jack.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;


@Getter
@Setter
public class T_order {
    public Long orderId;

    public String orderName;

    public String orderType;

    public Date createTime;
}
