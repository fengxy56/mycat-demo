package com.xiangxue.jack.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TOrderItem {

    public Long order_item_id;

    public int order_id;

    public String order_content;
}
