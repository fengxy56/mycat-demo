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
package io.mycat.backend.mysql.nio.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.mycat.backend.BackendConnection;
import io.mycat.backend.mysql.nio.MySQLConnection;
import io.mycat.backend.mysql.xa.TxState;
import io.mycat.config.ErrorCode;
import io.mycat.net.mysql.ErrorPacket;
import io.mycat.route.RouteResultsetNode;
import io.mycat.server.NonBlockingSession;

/**
 * @author mycat
 */
public class CommitNodeHandler extends MultiNodeHandler implements ResponseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommitNodeHandler.class);
	private final NonBlockingSession session;
    protected byte[] responseData;

	public CommitNodeHandler(NonBlockingSession session) {
        super(session);
		this.session = session;
    }

    public CommitNodeHandler(NonBlockingSession session, byte[] responseData) {
        super(session);
        this.session = session;
        this.responseData = responseData;

    }

    public void commit() {
        final int initCount = session.getTargetCount();
        lock.lock();
        try {
            reset(initCount);
        } finally {
            lock.unlock();
        }
        for (RouteResultsetNode rrn : session.getTargetKeys()) {
            final BackendConnection conn = session.getTarget(rrn);
            commit(conn);
        }
    }

	public void commit(BackendConnection conn) {
		conn.setResponseHandler(CommitNodeHandler.this);
		boolean isClosed=conn.isClosedOrQuit();
		if(isClosed)
		{
			session.getSource().writeErrMessage(ErrorCode.ER_UNKNOWN_ERROR,
					"receive commit,but find backend con is closed or quit");
			LOGGER.error( conn+"receive commit,but fond backend con is closed or quit");
		}
	   if(conn instanceof MySQLConnection)
	   {
		   MySQLConnection mysqlCon = (MySQLConnection) conn;
		   if (mysqlCon.getXaStatus() == 1)
		   {
			   String xaTxId = session.getXaTXID()+",'"+mysqlCon.getSchema()+"'";
			   String[] cmds = new String[]{"XA END " + xaTxId,
					   "XA PREPARE " + xaTxId};
			   mysqlCon.execBatchCmd(cmds);
		   } else
		   {
			   conn.commit();
		   }
	   }else
	   {
		   conn.commit();
	   }
	}

	@Override
	public void connectionAcquired(BackendConnection conn) {
		LOGGER.error("unexpected invocation: connectionAcquired from commit");

	}

	@Override
	public void okResponse(byte[] ok, BackendConnection conn) {
		if(conn instanceof MySQLConnection)
		{
			MySQLConnection mysqlCon = (MySQLConnection) conn;
			switch (mysqlCon.getXaStatus())
			{
				case TxState.TX_STARTED_STATE:
					if (mysqlCon.batchCmdFinished())
					{
						String xaTxId = session.getXaTXID()+",'"+mysqlCon.getSchema()+"'";
						mysqlCon.execCmd("XA COMMIT " + xaTxId);
						mysqlCon.setXaStatus(TxState.TX_PREPARED_STATE);
					}
					return;
				case TxState.TX_PREPARED_STATE:
				{
					mysqlCon.setXaStatus(TxState.TX_INITIALIZE_STATE);
					break;
				}
				default:
				//	LOGGER.error("Wrong XA status flag!");
			}
			
			/* 1.  ???????????????,xa ????????????     */
			if(TxState.TX_INITIALIZE_STATE==mysqlCon.getXaStatus()){
				if(session.getXaTXID()!=null){
					session.setXATXEnabled(false);
				}
			}
		}
		
		/* 2. preAcStates ???true,???????????????,???????????????true???preAcStates ???ac???????????????    */
        if(session.getSource().isPreAcStates()&&!session.getSource().isAutocommit()){
        	session.getSource().setAutocommit(true);
        }
        // session.clearResources(false);
        // ServerConnection source = session.getSource();
        // source.write(ok);
        if (decrementCountBy(1)) {
            if (responseData != null) {
                cleanAndFeedback(responseData);
            } else {
                cleanAndFeedback(ok);
            }

        }
	}

	@Override
	public void errorResponse(byte[] err, BackendConnection conn) {
		ErrorPacket errPkg = new ErrorPacket();
		errPkg.read(err);
		String errInfo = new String(errPkg.message);
		session.getSource().setTxInterrupt(errInfo);
        // errPkg.write(session.getSource());
        if (decrementCountBy(1)) {
            cleanAndFeedback(errPkg.writeToBytes());
        }

	}

	@Override
	public void rowEofResponse(byte[] eof, BackendConnection conn) {
		LOGGER.error(new StringBuilder().append("unexpected packet for ")
				.append(conn).append(" bound by ").append(session.getSource())
				.append(": field's eof").toString());
	}

	@Override
	public void fieldEofResponse(byte[] header, List<byte[]> fields,
			byte[] eof, BackendConnection conn) {
		LOGGER.error(new StringBuilder().append("unexpected packet for ")
				.append(conn).append(" bound by ").append(session.getSource())
				.append(": field's eof").toString());
	}

	@Override
	public void rowResponse(byte[] row, BackendConnection conn) {
		LOGGER.warn(new StringBuilder().append("unexpected packet for ")
				.append(conn).append(" bound by ").append(session.getSource())
				.append(": row data packet").toString());
	}

	@Override
	public void writeQueueAvailable() {

	}

	@Override
	public void connectionError(Throwable e, BackendConnection conn) {
        if (decrementCountBy(1)) {
            cleanAndFeedback(createErrPkg(e.getMessage()).writeToBytes());
        }

	}

	@Override
	public void connectionClose(BackendConnection conn, String reason) {
        if (decrementCountBy(1)) {
            cleanAndFeedback(createErrPkg(reason).writeToBytes());
        }

	}

    private void cleanAndFeedback(byte[] responseData) {
        // clear all resources
        session.clearResources(false);
        if (session.closed()) {
            return;
        }
        if (this.isFail()) {
            createErrPkg(error).write(session.getSource());
        } else {
            session.getSource().write(responseData);
        }
    }
}
