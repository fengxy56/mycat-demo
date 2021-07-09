package com.xiangxue.jack.bean;

import lombok.Data;

import java.util.Date;

@Data
public class ZgTicket {

    private String ticketId;

    private Integer ticketCount;

    private String ticketStatus;

    private Date ticketTime;

    private Integer version;

    public static void main(String[] args) {
        String x = "insert into t_user(user_id,user_name) values(#{user_id},#{user_name})";
//        System.out.println(x.substring(0,x.indexOf("into") + 4));
//        String from = x.substring(0,x.indexOf("from") + 4);
        String into = x.substring(x.indexOf("into") + 4);
//        System.out.println(x.substring(x.indexOf("into") + 4));
        System.out.println(into.substring(0,into.indexOf("(")));
//        System.out.println(from);

//        System.out.println(from.substring(0,from.indexOf("where")).trim());
    }
}
