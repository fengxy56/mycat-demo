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
package io.mycat.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLReplaceStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.parser.SQLStatementParser;
import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.WallProvider;

import io.mycat.MycatServer;
import io.mycat.config.loader.xml.XMLServerLoader;
import io.mycat.config.model.FirewallConfig;
import io.mycat.config.model.UserConfig;
import io.mycat.config.model.UserPrivilegesConfig;
import io.mycat.net.handler.FrontendPrivileges;
import io.mycat.route.parser.druid.MycatSchemaStatVisitor;
import io.mycat.route.parser.druid.MycatStatementParser;

/**
 * @author mycat
 */
public class MycatPrivileges implements FrontendPrivileges {
	/**
	 * ???????????????????????????new?????????
	 */
	private static MycatPrivileges instance = new MycatPrivileges();
	
    private static final Logger ALARM = LoggerFactory.getLogger("alarm");
    
    private static boolean check = false;	
	private final static ThreadLocal<WallProvider> contextLocal = new ThreadLocal<WallProvider>();

    public static MycatPrivileges instance() {
    	return instance;
    }
    
    private MycatPrivileges() {
    	super();
    }
    
    @Override
    public boolean schemaExists(String schema) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        return conf.getSchemas().containsKey(schema);
    }

    @Override
    public boolean userExists(String user, String host) {
    	//????????????????????????
    	return checkFirewallWhiteHostPolicy(user, host);
    }

    @Override
    public String getPassword(String user) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        if (user != null && user.equals(conf.getSystem().getClusterHeartbeatUser())) {
            return conf.getSystem().getClusterHeartbeatPass();
        } else {
            UserConfig uc = conf.getUsers().get(user);
            if (uc != null) {
                return uc.getPassword();
            } else {
                return null;
            }
        }
    }

    @Override
    public Set<String> getUserSchemas(String user) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        
        UserConfig uc = conf.getUsers().get(user);
        if (uc != null) {
            return uc.getSchemas();
        } else {
            return null;
        }
    
     }
    
    @Override
    public Boolean isReadOnly(String user) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
       
        UserConfig uc = conf.getUsers().get(user);
        if (uc != null) {
            return uc.isReadOnly();
        } else {
            return null;
        }
    }

	@Override
	public int getBenchmark(String user) {
		MycatConfig conf = MycatServer.getInstance().getConfig();
        UserConfig uc = conf.getUsers().get(user);
        if (uc != null) {
            return uc.getBenchmark();
        } else {
            return 0;
        }
	}

	/**
	 * ??????????????????????????????????????????????????????????????????????????????????????????????????????
	 * ???????????????????????????
	 * @see  XMLServerLoader
	 * @see  FirewallConfig
	 *
	 * @modification ???????????????????????????????????????
	 * @date 2016/12/8
	 * @modifiedBy Hash Zhang
	 */
	@Override
	public boolean checkFirewallWhiteHostPolicy(String user, String host) {
		
		MycatConfig mycatConfig = MycatServer.getInstance().getConfig();
        FirewallConfig firewallConfig = mycatConfig.getFirewall();
        
        //????????? ???????????????
        boolean isPassed = false;
        
        Map<String, List<UserConfig>> whitehost = firewallConfig.getWhitehost();
        Map<Pattern, List<UserConfig>> whitehostMask = firewallConfig.getWhitehostMask();
        if ((whitehost == null || whitehost.size() == 0)&&(whitehostMask == null || whitehostMask.size() == 0)) {
        	Map<String, UserConfig> users = mycatConfig.getUsers();
        	isPassed = users.containsKey(user);
        	
        } else {
        	List<UserConfig> list = whitehost.get(host);
			Set<Pattern> patterns = whitehostMask.keySet();
			if(patterns != null && patterns.size() > 0){
				for(Pattern pattern : patterns) {
					if(pattern.matcher(host).find()){
						isPassed = true;
						break;
					}
				}
			}
			if (list != null) {
				for (UserConfig userConfig : list) {
					if (userConfig.getName().equals(user)) {
						isPassed = true;
						break;
					}
				}
			}        	
        }
        
        if ( !isPassed ) {
        	 ALARM.error(new StringBuilder().append(Alarms.FIREWALL_ATTACK).append("[host=").append(host)
                     .append(",user=").append(user).append(']').toString());
        	 return false;
        }        
        return true;
	}

	
	/**
	 * @see https://github.com/alibaba/druid/wiki/%E9%85%8D%E7%BD%AE-wallfilter
	 */
	@Override
	public boolean checkFirewallSQLPolicy(String user, String sql) {
		
		boolean isPassed = true;
		
		if( contextLocal.get() == null ){
			FirewallConfig firewallConfig = MycatServer.getInstance().getConfig().getFirewall();
			if ( firewallConfig != null) {
				if ( firewallConfig.isCheck() ) {
					contextLocal.set(firewallConfig.getProvider());
					check = true;
				}
			}
		}
		
		if( check ){
			WallCheckResult result = contextLocal.get().check(sql);
			
			// ?????? druid ??????????????????SHOW FULL TABLES WHERE Table_type != 'VIEW' ?????????????????? BUG
			// ???????????????????????????Druid WallCheck ????????????SQL?????????????????????????????????????????????????????????????????????
			// ?????? ?????????????????? ShowFullTables ?????????show full tables ????????????????????????????????? 
//			List<SQLStatement> stmts =  result.getStatementList();
//			if ( !stmts.isEmpty() &&  !( stmts.get(0) instanceof SQLShowTablesStatement) ) {				
//				if ( !result.getViolations().isEmpty()) {				
//					isPassed = false;
//					ALARM.warn("Firewall to intercept the '" + user + "' unsafe SQL , errMsg:"
//							+ result.getViolations().get(0).getMessage() +
//							" \r\n " + sql);
//		        }				
//			}
			
			if ( !result.getViolations().isEmpty()) {				
				isPassed = false;
				ALARM.warn("Firewall to intercept the '" + user + "' unsafe SQL , errMsg:"
						+ result.getViolations().get(0).getMessage() +
						" \r\n " + sql);
	        }	
			
			
		}
		return isPassed;
	}

	// ??????SQL??????
	@Override
	public boolean checkDmlPrivilege(String user, String schema, String sql) {

		if ( schema == null ) {
			return true;
		}
		
		boolean isPassed = false;

		MycatConfig conf = MycatServer.getInstance().getConfig();
		UserConfig userConfig = conf.getUsers().get(user);
		if (userConfig != null) {
			
			UserPrivilegesConfig userPrivilege = userConfig.getPrivilegesConfig();
			if ( userPrivilege != null && userPrivilege.isCheck() ) {				
			
				UserPrivilegesConfig.SchemaPrivilege schemaPrivilege = userPrivilege.getSchemaPrivilege( schema );
				if ( schemaPrivilege != null ) {
		
					String tableName = null;
					int index = -1;
					
					//TODO ??????????????????????????????SQL ?????????
					
					//??????bug
					// https://github.com/alibaba/druid/issues/1309
					//com.alibaba.druid.sql.parser.ParserException: syntax error, error in :'begin',expect END, actual EOF begin
					if ( sql != null && sql.length() == 5 && sql.equalsIgnoreCase("begin") ) {
						return true;
					}
					
					SQLStatementParser parser = new MycatStatementParser(sql);			
					SQLStatement stmt = parser.parseStatement();

					if (stmt instanceof SQLReplaceStatement || stmt instanceof SQLInsertStatement) {
						index = 0;
					} else if (stmt instanceof SQLUpdateStatement ) {
						index = 1;
					} else if (stmt instanceof SQLSelectStatement ) {
						index = 2;
					} else if (stmt instanceof SQLDeleteStatement ) {
						index = 3;
					}

					if ( index > -1) {
						
						MycatSchemaStatVisitor schemaStatVisitor = new MycatSchemaStatVisitor();
						stmt.accept(schemaStatVisitor);
						String key = schemaStatVisitor.getCurrentTable();
						if ( key != null ) {
							
							if (key.contains("`")) {
								key = key.replaceAll("`", "");
							}
							
							int dotIndex = key.indexOf(".");
							if (dotIndex > 0) {
								tableName = key.substring(dotIndex + 1);
							} else {
								tableName = key;
							}							
							
							//??????table ??????, ???????????????????????????, ????????????????????????????????????
							UserPrivilegesConfig.TablePrivilege tablePrivilege = schemaPrivilege.getTablePrivilege( tableName );
							if ( tablePrivilege.getDml()[index] > 0 ) {
								isPassed = true;
							}
							
						} else {
							//skip
							isPassed = true;
						}
						
						
					} else {						
						//skip
						isPassed = true;
					}
					
				} else {					
					//skip
					isPassed = true;
				}
				
			} else {
				//skip
				isPassed = true;
			}

		} else {
			//skip
			isPassed = true;
		}
		
		if( !isPassed ) {
			 ALARM.error(new StringBuilder().append(Alarms.DML_ATTACK ).append("[sql=").append( sql )
                     .append(",user=").append(user).append(']').toString());
		}
		
		return isPassed;
	}

	@Override
	public boolean checkDataNodeDmlPrivilege(String user, String dataNode, String sql) {
		if (dataNode == null) {
			return true;
		}

		boolean isPassed = false;

		MycatConfig conf = MycatServer.getInstance().getConfig();
		UserConfig userConfig = conf.getUsers().get(user);
		if (userConfig != null) {

			UserPrivilegesConfig userPrivilege = userConfig.getPrivilegesConfig();
			if (userPrivilege != null && userPrivilege.isCheck()) {

				UserPrivilegesConfig.DataNodePrivilege dataNodePrivilege = userPrivilege.getDataNodePrivilege(dataNode);
				if (dataNodePrivilege != null) {

					if (sql != null && sql.length() == 5 && sql.equalsIgnoreCase("begin")) {
						return true;
					}

					//?????? dataNode ??? select ??????, ???????????????????????????, ????????????????????????????????????
					if (dataNodePrivilege.getDml()[2] > 0) {
						isPassed = true;
					}
				}
			} else {
				return true;
			}
		}
		return isPassed;
	}
}