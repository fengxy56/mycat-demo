package com.xiangxue.jack.service;

import com.xiangxue.jack.bean.T_order_sharing_by_intfile;
import com.xiangxue.jack.dao.CommonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private CommonMapper commonMapper;

//    @Transactional
    @Override
    public int addOrders(T_order_sharing_by_intfile order1, T_order_sharing_by_intfile order2) {
        commonMapper.addT_order_sharing_by_intfile(order1);
        commonMapper.addT_order_sharing_by_intfile(order2);
        if(true) throw new RuntimeException("mycat exception");
        return 1;
    }
}
