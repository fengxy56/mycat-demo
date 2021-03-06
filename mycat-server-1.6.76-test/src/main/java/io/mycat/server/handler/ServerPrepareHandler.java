/*
 * Copyright (c) 2020, OpenCloudDB/MyCAT and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software;Designed and Developed mainly by many Chinese
 * opensource volunteers. you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 2 only, as published by the
 * Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Any questions about this component can be directed to it's project Web address
 * https://code.google.com/p/opencloudb/.
 *
 */
package io.mycat.server.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.google.common.escape.Escapers.Builder;

import io.mycat.backend.mysql.BindValue;
import io.mycat.backend.mysql.ByteUtil;
import io.mycat.backend.mysql.PreparedStatement;
import io.mycat.backend.mysql.nio.handler.PrepareRequestHandler;
import io.mycat.backend.mysql.nio.handler.PrepareRequestHandler.PrepareRequestCallback;
import io.mycat.config.ErrorCode;
import io.mycat.config.Fields;
import io.mycat.net.handler.FrontendPrepareHandler;
import io.mycat.net.mysql.ExecutePacket;
import io.mycat.net.mysql.FieldPacket;
import io.mycat.net.mysql.LongDataPacket;
import io.mycat.net.mysql.OkPacket;
import io.mycat.net.mysql.ResetPacket;
import io.mycat.server.ServerConnection;
import io.mycat.server.response.PreparedStmtResponse;
import io.mycat.util.HexFormatUtil;

/**
 * @author mycat, CrazyPig, zhuam
 */
public class ServerPrepareHandler implements FrontendPrepareHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerPrepareHandler.class);

    private static Escaper varcharEscaper = null;

    static {
        Builder escapeBuilder = Escapers.builder();
        escapeBuilder.addEscape('\0', "\\0");
        escapeBuilder.addEscape('\'', "\\'");
        escapeBuilder.addEscape('\b', "\\b");
        escapeBuilder.addEscape('\n', "\\n");
        escapeBuilder.addEscape('\r', "\\r");
        escapeBuilder.addEscape('\"', "\\\"");
        escapeBuilder.addEscape('$', "\\$");
        escapeBuilder.addEscape('\\', "\\\\");
        varcharEscaper = escapeBuilder.build();
    }

    private ServerConnection source;

    // java int???32??????long???64??????mysql?????????????????????statementId???32???????????????Integer
    private static final AtomicInteger PSTMT_ID_GENERATOR = new AtomicInteger(0);
    //    private static final Map<String, PreparedStatement> pstmtForSql = new ConcurrentHashMap<>();
    private static final Map<Long, PreparedStatement> pstmtForId = new ConcurrentHashMap<>();
    private int maxPreparedStmtCount;

    public ServerPrepareHandler(ServerConnection source, int maxPreparedStmtCount) {
        this.source = source;
        this.maxPreparedStmtCount = maxPreparedStmtCount;
    }

    @Override
    public void prepare(String sql) {

        LOGGER.debug("use server prepare, sql: " + sql);
        PreparedStatement pstmt = null;
        if (pstmt  == null) {
            // ???????????????????????????????????????
            int columnCount = 0;
            int paramCount = getParamCount(sql);
            if (paramCount > maxPreparedStmtCount) {
                source.writeErrMessage(ErrorCode.ER_PS_MANY_PARAM,
                        "Prepared statement contains too many placeholders");
                return;
            }
            pstmt = new PreparedStatement(PSTMT_ID_GENERATOR.incrementAndGet(), sql,
                    paramCount);
            pstmtForId.put(pstmt.getId(), pstmt);
            LOGGER.info("preparestatement  parepare id:{}", pstmt.getId());
        }
        PreparedStmtResponse.response(pstmt, source);
    }


    @Override
    public void sendLongData(byte[] data) {
        LongDataPacket packet = new LongDataPacket();
        packet.read(data);
        long pstmtId = packet.getPstmtId();
        LOGGER.info("preparestatement  long data id:{}", pstmtId);
        PreparedStatement pstmt = pstmtForId.get(pstmtId);
        if (pstmt != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("send long data to prepare sql : " + pstmtForId.get(pstmtId));
            }
            long paramId = packet.getParamId();
            try {
                pstmt.appendLongData(paramId, packet.getLongData());
            } catch (IOException e) {
                source.writeErrMessage(ErrorCode.ERR_FOUND_EXCEPTION, e.getMessage());
            }
        }
    }

    @Override
    public void reset(byte[] data) {
        ResetPacket packet = new ResetPacket();
        packet.read(data);
        long pstmtId = packet.getPstmtId();
        LOGGER.info("preparestatement  long data id:{}", pstmtId);
        PreparedStatement pstmt = pstmtForId.get(pstmtId);
        if (pstmt != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("reset prepare sql : " + pstmtForId.get(pstmtId));
            }
            pstmt.resetLongData();
            source.write(OkPacket.OK);
        } else {
            source.writeErrMessage(ErrorCode.ERR_FOUND_EXCEPTION,
                    "can not reset prepare statement : " + pstmtForId.get(pstmtId));
        }
    }

    @Override
    public void execute(byte[] data) {
        long pstmtId = ByteUtil.readUB4(data, 5);
        PreparedStatement pstmt = null;
        LOGGER.info("preparestatement  execute id:{}", pstmtId);
        if ((pstmt = pstmtForId.get(pstmtId)) == null) {
            source.writeErrMessage(ErrorCode.ER_ERROR_WHEN_EXECUTING_COMMAND,
                    "Unknown pstmtId when executing.");
        } else {
            ExecutePacket packet = new ExecutePacket(pstmt);
            try {
                packet.read(data, source.getCharset());
            } catch (UnsupportedEncodingException e) {
                source.writeErrMessage(ErrorCode.ER_ERROR_WHEN_EXECUTING_COMMAND, e.getMessage());
                return;
            }
            BindValue[] bindValues = packet.values;
            // ??????sql????????????????????????????????????
            String sql = prepareStmtBindValue(pstmt, bindValues);
            // ??????sql
            source.getSession2().setPrepared(true);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("execute prepare sql: " + sql);
            }

            pstmt.resetLongData();
            source.query(sql);
        }
    }


    @Override
    public void close(byte[] data) {
        long pstmtId = ByteUtil.readUB4(data, 5); // ??????prepare stmt id
        LOGGER.info("preparestatement  close id:{}", pstmtId);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("close prepare stmt, stmtId = " + pstmtId);
        }
        PreparedStatement pstmt = pstmtForId.remove(pstmtId);
    }

    @Override
    public void clear() {
        this.pstmtForId.clear();
//    this.pstmtForSql.clear();
    }



    // ???????????????sql????????????????????????
    private int getParamCount(String sql) {
        char[] cArr = sql.toCharArray();
        int count = 0;
        for (int i = 0; i < cArr.length; i++) {
            if (cArr[i] == '?') {
                count++;
            }
        }
        return count;
    }

    /**
     * ??????sql??????,????????????????????????????????????
     */
    private String prepareStmtBindValue(PreparedStatement pstmt, BindValue[] bindValues) {
        String sql = pstmt.getStatement();
        int[] paramTypes = pstmt.getParametersType();

        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (int i = 0, len = sql.length(); i < len; i++) {
            char c = sql.charAt(i);
            if (c != '?') {
                sb.append(c);
                continue;
            }
            // ????????????????
            int paramType = paramTypes[idx];
            BindValue bindValue = bindValues[idx];
            idx++;
            // ???????????????????????????
            if (bindValue.isNull) {
                sb.append("NULL");
                continue;
            }
            // ????????????, ???????????????????????????
            switch (paramType & 0xff) {
                case Fields.FIELD_TYPE_TINY:
                    sb.append(String.valueOf(bindValue.byteBinding));
                    break;
                case Fields.FIELD_TYPE_SHORT:
                    sb.append(String.valueOf(bindValue.shortBinding));
                    break;
                case Fields.FIELD_TYPE_LONG:
                    sb.append(String.valueOf(bindValue.intBinding));
                    break;
                case Fields.FIELD_TYPE_LONGLONG:
                    sb.append(String.valueOf(bindValue.longBinding));
                    break;
                case Fields.FIELD_TYPE_FLOAT:
                    sb.append(String.valueOf(bindValue.floatBinding));
                    break;
                case Fields.FIELD_TYPE_DOUBLE:
                    sb.append(String.valueOf(bindValue.doubleBinding));
                    break;
                case Fields.FIELD_TYPE_VAR_STRING:
                case Fields.FIELD_TYPE_STRING:
                case Fields.FIELD_TYPE_VARCHAR:
                    bindValue.value = varcharEscaper.asFunction().apply(String.valueOf(bindValue.value));
                    sb.append("'" + bindValue.value + "'");
                    break;
                case Fields.FIELD_TYPE_TINY_BLOB:
                case Fields.FIELD_TYPE_BLOB:
                case Fields.FIELD_TYPE_MEDIUM_BLOB:
                case Fields.FIELD_TYPE_LONG_BLOB:
                    if (bindValue.value instanceof ByteArrayOutputStream) {
                        byte[] bytes = ((ByteArrayOutputStream) bindValue.value).toByteArray();
                        sb.append("X'" + HexFormatUtil.bytesToHexString(bytes) + "'");
                    } else {
                        // ???????????????????????????else, ??????long data???????????????(ByteArrayOutputStream)?????????
                        LOGGER.warn(
                                "bind value is not a instance of ByteArrayOutputStream, maybe someone change the implement of long data storage!");
                        sb.append("'" + bindValue.value + "'");
                    }
                    break;
                case Fields.FIELD_TYPE_TIME:
                case Fields.FIELD_TYPE_DATE:
                case Fields.FIELD_TYPE_DATETIME:
                case Fields.FIELD_TYPE_TIMESTAMP:
                    sb.append("'" + bindValue.value + "'");
                    break;
                default:
                    bindValue.value = varcharEscaper.asFunction().apply(String.valueOf(bindValue.value));
                    sb.append(bindValue.value.toString());
                    break;
            }
        }
        return sb.toString();
    }
}