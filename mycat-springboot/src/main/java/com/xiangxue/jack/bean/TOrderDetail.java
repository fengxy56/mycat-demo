package com.xiangxue.jack.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class TOrderDetail {
    public Long orderDetailId;

    public Long orderId;

    public String orderDetailName;

    public Date createTime;
}
