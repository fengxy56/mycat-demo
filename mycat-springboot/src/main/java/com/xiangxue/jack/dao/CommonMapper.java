package com.xiangxue.jack.dao;


import com.xiangxue.jack.bean.*;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface CommonMapper {

    int saveArea(ConsultConfigArea area);

    int deleteArea(Map param);

    int deleteAreaAll();

    int updateArea(ConsultConfigArea area);

    List<ConsultConfigArea> queryAreaByAreaCode(Map param);

    List<ConsultRecord> queryConsultRecords(Map param);

    List<Map> queryUserByPsptId(Map param);

    int saveUser(ConsultIdCardInfo record);

    int saveRecord(ConsultRecord record);

    int saveRecordCount(ConsultRecordCount recordCount);

    List<ConsultRecord> queryRecords(Map param);

    List<ConsultRecord> queryRecordshaveH(Map param);

    //@Select("select * from consult_content where type = #{type}")
    //    @Select({"<script>", "select * from consult_content a where a.state = 0",
    //            "<if test='type != null'>",
    //            "and a.type = #{type,jdbcType=VARCHAR}", "</if>",
    //            "order by a.itemindex", "</script>"})
    List<ConsultContent> queryContent(Map param);

    int updateRecords(Map param);

    int updateRecordsByPsptId(Map param);

    List<ConsultRecordCount> queryRecordCount(Map param);

    int updateRecordCount(Map param);

    @Select("select * from consult_configarea")
    List<ConsultConfigArea> qryArea();

    List<ConsultContract> qryContracts(Map param);

    int saveContracts(List<ConsultContract> contracts);

    int updateConsultRecord(Map param);

    @Select("select * from consult_configarea where areaCode=#{areaCode}")
    List<ConsultConfigArea> queryAreaById(String areaCode);

    @Insert("insert into consult_configarea(AREACODE,AREANAME,STATE) values(#{areaCode},#{areaName},#{state})")
    int addArea(ConsultConfigArea area);

    @Insert("insert into zg_goods (goodCode, goodName, count\n" +
            "\t\t)\n" +
            "\t\tvalues (#{goodCode,jdbcType=VARCHAR}, #{goodName,jdbcType=VARCHAR},\n" +
            "\t\t#{count,jdbcType=INTEGER}\n" +
            "\t\t)")
    int addGood(ZgGoods zgGoods);

    @Select("select * from zg_goods where goodCode=#{goodCode}")
    List<ZgGoods> queryGoodsByGoodCode(String goodCode);

    @Select("select * from zg_goods")
    List<ZgGoods> queryAll();

    @Update("update zg_ticket set version=versoin+1 where ticketId = #{ticketId} and version = #{version}")
    int updateLock(Map map);


    @Select("select * from zg_ticket where ticketId = #{ticketId}")
    List<ZgTicket> queryTicketById(String ticketId);

    @Update("update zg_ticket set ticketCount=ticketCount-#{ticketCount} where ticketId = #{ticketId} and ticketCount >= #{ticketCount}")
    int updateTicket(ZgTicket zgTicket);

    @Insert("insert into tb_user(user_id,user_name) values(#{user_id},#{user_name})")
    int addUser(Tb_user user);

    @Insert("insert into t_order(orderId,orderName,orderType,createTime) values(#{orderId},#{orderName},#{orderType},now())")
    int addTOrder(T_order order);

    @Insert({"<script>",
//            "/*!mycat:catlet=io.mycat.route.sequence.BatchInsertSequence */",
            "insert into t_order(orderId,orderName,orderType,createTime) values",
            "<foreach collection='list' item='item' index='index' separator=','>",
            "(#{item.orderId},#{item.orderName},#{item.orderType},now())",
            "</foreach>",
            "</script>"})
    void addTOrderBatch(List<T_order> orders);

    @Insert("insert into t_order(order_id,order_content) values(#{order_id},#{order_content})")
    int addT_Order(TOrder order);

    @Insert("insert into t_order_item(order_item_id,order_id,order_content) values(#{order_item_id},#{order_id},#{order_content})")
    int addT_Order_Item(TOrderItem orderItem);

    @Insert({"<script>",
//            "/*!mycat:catlet=io.mycat.route.sequence.BatchInsertSequence */",
            "insert into t_order(order_content) values",
            "<foreach collection='list' item='item' index='index' separator=','>",
            "(#{item.order_content})",
            "</foreach>",
            "</script>"})
    void addT_OrderByBatch(List<TOrder> orders);

    @Select("select * from t_order")
    List<TOrder> queryTOrderTest(TOrder order);

    @Insert("insert into t_order_gd_hash(orderId,orderName,createTime) values(#{orderId},#{orderName},now())")
    int addTt_order_gd_hash(T_order order);

    @Insert("insert into t_order_range(orderId,orderName,createTime) values(#{orderId},#{orderName},now())")
    int addTt_order_range(T_order order);

    @Insert("insert into t_order_murmur_hash(orderId,orderName,createTime) values(#{orderId},#{orderName},now())")
    int addTt_order_murmur_hash(T_order order);

    @Insert("insert into t_order_time_day(orderId,orderName,createTime) values(#{orderId},#{orderName},#{createTime})")
    int addTt_order_time_day(T_order order);

    @Insert("insert into t_order_sharing_by_intfile(orderId,orderName,createTime,province) values(#{orderId},#{orderName},now(),#{province})")
    int addT_order_sharing_by_intfile(T_order_sharing_by_intfile order);

    @Select({"<script>", "select * from t_order_range",
            "<trim prefix='where' prefixOverrides='AND|OR'>",
            "<if test='orderId != null'>",
            "and orderId = #{orderId}",
            "</if>",
            "<if test='orderName != null'>",
            "and orderName = #{orderName}",
            "</if>",
            "</trim>",
            "</script>"})
    List<T_order> queryTOrder(T_order order);

    //    @Insert("insert into t_user${seq}(user_id,user_name) values(#{user_id},#{user_name})")
    @Insert("insert into t_user (user_id,user_name) values(#{user_id},#{user_name})")
    int addT_user(Tb_user user);

    //    @Select("select * from t_user${seq} where user_id=#{user_id}")
//    @Select("select * from t_user where user_id=#{user_id}")
    @Select({"<script>", "select * from t_user",
            "<trim prefix='where' prefixOverrides='AND|OR'>",
            "<if test='user_id != null'>",
            "and user_id = #{user_id}",
            "</if>",
            "<if test='user_name != null'>",
            "and user_name = #{user_name}",
            "</if>",
            "</trim>",
            "</script>"})
    List<Tb_user> queryUser(Tb_user user);

    @Insert("insert into t_order_type (orderType,orderTypeName) values(#{orderType},#{orderTypeName})")
    int addTOrderType(TOrderType tOrderType);

    @Select("select * from t_order_type where orderType = #{orderType}")
    List<TOrderType> queryTOrderType(TOrderType tOrderType);

    @Insert("insert into t_order_detail (orderDetailId,orderId,orderDetailName,createTime) values(#{orderDetailId},#{orderId},#{orderDetailName},now())")
    int addTOrderDetail(TOrderDetail tOrderDetail);
}
