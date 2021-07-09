package com.xiangxue.jack.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class T_order_sharing_by_intfile {
    public Long orderId;

    public String orderName;

    public Date createTime;

    public String province;
}
