package com.xiangxue.jack.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Tb_user {
    public Long user_id;

    public String user_name;

    public Integer seq;

    //properties配置文件
    public RouteBean routeBean;
}
