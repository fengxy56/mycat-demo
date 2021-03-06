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
package io.mycat.manager.response;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger; import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import io.mycat.MycatServer;
import io.mycat.backend.BackendConnection;
import io.mycat.backend.datasource.PhysicalDBNode;
import io.mycat.backend.datasource.PhysicalDBPool;
import io.mycat.backend.datasource.PhysicalDatasource;
import io.mycat.backend.jdbc.JDBCConnection;
import io.mycat.backend.mysql.nio.MySQLConnection;
import io.mycat.config.ConfigInitializer;
import io.mycat.config.ErrorCode;
import io.mycat.config.MycatCluster;
import io.mycat.config.MycatConfig;
import io.mycat.config.model.FirewallConfig;
import io.mycat.config.model.SchemaConfig;
import io.mycat.config.model.UserConfig;
import io.mycat.config.util.DnPropertyUtil;
import io.mycat.manager.ManagerConnection;
import io.mycat.net.NIOProcessor;
import io.mycat.net.mysql.OkPacket;

/**
 * @author mycat
 * @author zhuam
 */
public final class ReloadConfig {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ReloadConfig.class);

	public static void execute(ManagerConnection c, final boolean loadAll) {
		
		// reload @@config_all ????????????????????????????????????
		if ( loadAll && !NIOProcessor.backends_old.isEmpty() ) {
			c.writeErrMessage(ErrorCode.ER_YES, "The are several unfinished db transactions before executing \"reload @@config_all\", therefore the execution is terminated for logical integrity and please try again later.");
			return;
		}
		
		final ReentrantLock lock = MycatServer.getInstance().getConfig().getLock();		
		lock.lock();
		try {
			ListenableFuture<Boolean> listenableFuture = MycatServer.getInstance().getListeningExecutorService().submit(
				new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return loadAll ? reload_all() : reload();
					}
				}
			);
			Futures.addCallback(listenableFuture, new ReloadCallBack(c), MycatServer.getInstance().getListeningExecutorService());
		} finally {
			lock.unlock();
		}
	}

	public static boolean reload_all() {
		
		/**
		 *  1?????????????????????
		 *  1.1???ConfigInitializer ????????????????????????
		 *  1.2???DataNode/DataHost ??????????????????
		 */
		ConfigInitializer loader = new ConfigInitializer(true);
		Map<String, UserConfig> newUsers = loader.getUsers();
		Map<String, SchemaConfig> newSchemas = loader.getSchemas();
		Map<String, PhysicalDBNode> newDataNodes = loader.getDataNodes();
		Map<String, PhysicalDBPool> newDataHosts = loader.getDataHosts();
		MycatCluster newCluster = loader.getCluster();
		FirewallConfig newFirewall = loader.getFirewall();
		
		/**
		 * 1.2?????????????????????
		 */
		loader.testConnection();

		/**
		 *  2?????????
		 *  2.1????????? dataSource ????????????????????????
		 *  2.2????????? dataSource ?????????????????? ??????????????? 2.3
		 *  2.3????????? dataSource ????????????????????????
		 *  2.4????????? dataSource ?????????????????????????????? ????????????
		 *  2.5????????? dataSource ??????????????????????????????
		 */
		
		MycatConfig config = MycatServer.getInstance().getConfig();
		
		/**
		 * 2.1 ????????? dataSource ??????????????????????????? ???????????????????????????
		 */
		
		boolean isReloadStatusOK = true;
		
		/**
		 * 2.2????????? dataHosts ?????????
		 */
		for (PhysicalDBPool dbPool : newDataHosts.values()) {					
			String hostName = dbPool.getHostName();
			
			// ?????? schemas
			ArrayList<String> dnSchemas = new ArrayList<String>(30);
			for (PhysicalDBNode dn : newDataNodes.values()) {
				if (dn.getDbPool().getHostName().equals(hostName)) {
					dnSchemas.add(dn.getDatabase());
				}
			}
			dbPool.setSchemas( dnSchemas.toArray(new String[dnSchemas.size()]) );
			
			// ?????? data host
			String dnIndex = DnPropertyUtil.loadDnIndexProps().getProperty(dbPool.getHostName(), "0");
			if ( !"0".equals(dnIndex) ) {
				LOGGER.info("init datahost: " + dbPool.getHostName() + "  to use datasource index:" + dnIndex);
			}			
			
			dbPool.init( Integer.valueOf(dnIndex) );			
			if ( !dbPool.isInitSuccess() ) {
				isReloadStatusOK = false;
				break;
			}
		}
		
		/**
		 *  TODO??? ?????????????????????
		 *    
		 *  ?????? dataHosts ?????????????????????
		 */
		if ( isReloadStatusOK ) {

			config.getSystem().setUseSqlStat(loader.getSystem().getUseSqlStat());
			MycatServer.getInstance().ensureSqlstatRecycleFuture();
			
			/**
			 * 2.3??? ??????????????????????????????????????????????????????????????????
			 */
			config.reload(newUsers, newSchemas, newDataNodes, newDataHosts, newCluster, newFirewall, true);



			/**
			 * 2.4??? ??????????????????
			 */
			LOGGER.warn("1???clear old backend connection(size): " + NIOProcessor.backends_old.size());
			
			// ??????????????? reload ??????????????? old Cons
			Iterator<BackendConnection> iter = NIOProcessor.backends_old.iterator();
			while( iter.hasNext() ) {
				BackendConnection con = iter.next();
				con.close("clear old datasources");
				iter.remove();	
			}
			
			Map<String, PhysicalDBPool> oldDataHosts = config.getBackupDataHosts();
			for (PhysicalDBPool dbPool : oldDataHosts.values()) {			
				dbPool.stopHeartbeat();
				
				// ?????????????????????????????????
				for (PhysicalDatasource ds : dbPool.getAllDataSources()) {					
					//
					for (NIOProcessor processor : MycatServer.getInstance().getProcessors()) {
						for (BackendConnection con : processor.getBackends().values()) {
							if (con instanceof MySQLConnection) {
								MySQLConnection mysqlCon = (MySQLConnection) con;
								if ( mysqlCon.getPool() == ds) {
									NIOProcessor.backends_old.add( con );
								}

			                } else if (con instanceof JDBCConnection) {
			                    JDBCConnection jdbcCon = (JDBCConnection) con;
			                    if (jdbcCon.getPool() == ds) {
			                    	NIOProcessor.backends_old.add( con );
			                    }
			                }
			            }
					}
				}				
			}			
			LOGGER.warn("2???to be recycled old backend connection(size): " + NIOProcessor.backends_old.size());

			//????????????
			MycatServer.getInstance().getCacheService().clearCache();
			MycatServer.getInstance().initRuleData();
			return true;
			
		} else {
			// ?????????????????????????????????????????????????????????
			LOGGER.warn("reload failed, clear previously created datasources ");
			for (PhysicalDBPool dbPool : newDataHosts.values()) {
				dbPool.clearDataSources("reload config");
				dbPool.stopHeartbeat();
			}
			return false;
		}
	}

    public static boolean reload() {
    	
    	/**
		 *  1???????????????????????? ConfigInitializer ????????????????????????, ??????????????????????????????,??????????????? dataHost  dataNode
		 */
        ConfigInitializer loader = new ConfigInitializer(false);
        Map<String, UserConfig> users = loader.getUsers();
        Map<String, SchemaConfig> schemas = loader.getSchemas();
        Map<String, PhysicalDBNode> dataNodes = loader.getDataNodes();
        Map<String, PhysicalDBPool> dataHosts = loader.getDataHosts();
        MycatCluster cluster = loader.getCluster();
        FirewallConfig firewall = loader.getFirewall();


		MycatServer.getInstance().getConfig().getSystem().setUseSqlStat(loader.getSystem().getUseSqlStat());
		MycatServer.getInstance().ensureSqlstatRecycleFuture();
        
        /**
         * 2??????????????????????????????????????????
         */
        MycatServer.getInstance().getConfig().reload(users, schemas, dataNodes, dataHosts, cluster, firewall, false);

        /**
         * 3???????????????
         */
        MycatServer.getInstance().getCacheService().clearCache();
		MycatServer.getInstance().initRuleData();
        return true;
    }
    
	/**
	 * ?????????????????????????????????????????????????????????
	 */
	private static class ReloadCallBack implements FutureCallback<Boolean> {

		private ManagerConnection mc;

		private ReloadCallBack(ManagerConnection c) {
			this.mc = c;
		}

		@Override
		public void onSuccess(Boolean result) {
			if (result) {
				LOGGER.warn("send ok package to client " + String.valueOf(mc));
				OkPacket ok = new OkPacket();
				ok.packetId = 1;
				ok.affectedRows = 1;
				ok.serverStatus = 2;
				ok.message = "Reload config success".getBytes();
				ok.write(mc);
			} else {
				mc.writeErrMessage(ErrorCode.ER_YES, "Reload config failure");
			}
		}

		@Override
		public void onFailure(Throwable t) {
			mc.writeErrMessage(ErrorCode.ER_YES, "Reload config failure");
		}
	}
}
