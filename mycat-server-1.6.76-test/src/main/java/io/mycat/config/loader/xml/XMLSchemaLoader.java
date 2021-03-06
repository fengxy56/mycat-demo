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
package io.mycat.config.loader.xml;

import io.mycat.backend.datasource.PhysicalDBPool;
import io.mycat.config.loader.SchemaLoader;
import io.mycat.config.model.*;
import io.mycat.config.model.rule.RuleConfig;
import io.mycat.config.model.rule.TableRuleConfig;
import io.mycat.config.util.ConfigException;
import io.mycat.config.util.ConfigUtil;
import io.mycat.route.function.AbstractPartitionAlgorithm;
import io.mycat.route.function.TableRuleAware;
import io.mycat.util.DecryptUtil;
import io.mycat.util.ObjectUtil;
import io.mycat.util.SplitUtil;
import io.mycat.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mycat
 */
@SuppressWarnings("unchecked")
public class XMLSchemaLoader implements SchemaLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLSchemaLoader.class);

    private final static String DEFAULT_DTD = "/schema.dtd";
    private final static String DEFAULT_XML = "/schema.xml";

    private final Map<String, TableRuleConfig> tableRules;
    private final Map<String, DataHostConfig> dataHosts;
    private final Map<String, DataNodeConfig> dataNodes;
    private final Map<String, SchemaConfig> schemas;

    public XMLSchemaLoader(String schemaFile, String ruleFile) {
        //?????????rule.xml
        XMLRuleLoader ruleLoader = new XMLRuleLoader(ruleFile);
        //???tableRules???????????????????????????Schema???rule????????????????????????????????????????????????
        this.tableRules = ruleLoader.getTableRules();
        //??????ruleLoader
        ruleLoader = null;
        this.dataHosts = new HashMap<String, DataHostConfig>();
        this.dataNodes = new HashMap<String, DataNodeConfig>();
        this.schemas = new HashMap<String, SchemaConfig>();
        //????????????schema??????
        this.load(DEFAULT_DTD, schemaFile == null ? DEFAULT_XML : schemaFile);
    }

    public XMLSchemaLoader() {
        this(null, null);
    }

    @Override
    public Map<String, TableRuleConfig> getTableRules() {
        return tableRules;
    }

    @Override
    public Map<String, DataHostConfig> getDataHosts() {
        return (Map<String, DataHostConfig>) (dataHosts.isEmpty() ? Collections.emptyMap() : dataHosts);
    }

    @Override
    public Map<String, DataNodeConfig> getDataNodes() {
        return (Map<String, DataNodeConfig>) (dataNodes.isEmpty() ? Collections.emptyMap() : dataNodes);
    }

    @Override
    public Map<String, SchemaConfig> getSchemas() {
        return (Map<String, SchemaConfig>) (schemas.isEmpty() ? Collections.emptyMap() : schemas);
    }

    private void load(String dtdFile, String xmlFile) {
        InputStream dtd = null;
        InputStream xml = null;
        try {
            dtd = XMLSchemaLoader.class.getResourceAsStream(dtdFile);
            xml = XMLSchemaLoader.class.getResourceAsStream(xmlFile);
            Element root = ConfigUtil.getDocument(dtd, xml).getDocumentElement();
            //??????????????????DataHost
            loadDataHosts(root);
            //??????????????????DataNode
            loadDataNodes(root);
            //?????????????????????Schema
            loadSchemas(root);
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException(e);
        } finally {

            if (dtd != null) {
                try {
                    dtd.close();
                } catch (IOException e) {
                }
            }

            if (xml != null) {
                try {
                    xml.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void loadSchemas(Element root) {
        NodeList list = root.getElementsByTagName("schema");
        for (int i = 0, n = list.getLength(); i < n; i++) {
            Element schemaElement = (Element) list.item(i);
            //??????????????????
            String name = schemaElement.getAttribute("name");
            String dataNode = schemaElement.getAttribute("dataNode");
            String randomDataNode = schemaElement.getAttribute("randomDataNode");
            String checkSQLSchemaStr = schemaElement.getAttribute("checkSQLschema");
            String sqlMaxLimitStr = schemaElement.getAttribute("sqlMaxLimit");
            int sqlMaxLimit = -1;
            //??????sql?????????????????????
            if (sqlMaxLimitStr != null && !sqlMaxLimitStr.isEmpty()) {
                sqlMaxLimit = Integer.parseInt(sqlMaxLimitStr);
            }

            // check dataNode already exists or not,???schema??????????????????datanode
            String defaultDbType = null;
            //?????????????????????dataNode
            if (dataNode != null && !dataNode.isEmpty()) {
                List<String> dataNodeLst = new ArrayList<String>(1);
                dataNodeLst.add(dataNode);
                checkDataNodeExists(dataNodeLst);
                String dataHost = dataNodes.get(dataNode).getDataHost();
                defaultDbType = dataHosts.get(dataHost).getDbType();
            } else {
                dataNode = null;
            }
            //??????schema?????????tables
            Map<String, TableConfig> tables = loadTables(schemaElement);
            //??????schema????????????
            if (schemas.containsKey(name)) {
                throw new ConfigException("schema " + name + " duplicated!");
            }

            // ?????????table??????????????????dataNode?????????????????????table???????????????dataNode??????
            if (dataNode == null && tables.size() == 0) {
                throw new ConfigException(
                        "schema " + name + " didn't config tables,so you must set dataNode property!");
            }

            SchemaConfig schemaConfig = new SchemaConfig(name, dataNode,
                    tables, sqlMaxLimit, "true".equalsIgnoreCase(checkSQLSchemaStr),randomDataNode);

            //??????DB????????????????????????sql???????????????????????????
            if (defaultDbType != null) {
                schemaConfig.setDefaultDataNodeDbType(defaultDbType);
                if (!"mysql".equalsIgnoreCase(defaultDbType)) {
                    schemaConfig.setNeedSupportMultiDBType(true);
                }
            }

            // ?????????????????????mysql?????????????????????????????????????????????????????????????????????????????????
            for (TableConfig tableConfig : tables.values()) {
                if (isHasMultiDbType(tableConfig)) {
                    schemaConfig.setNeedSupportMultiDBType(true);
                    break;
                }
            }
            //????????????dataNode???DB??????
            Map<String, String> dataNodeDbTypeMap = new HashMap<>();
            for (String dataNodeName : dataNodes.keySet()) {
                DataNodeConfig dataNodeConfig = dataNodes.get(dataNodeName);
                String dataHost = dataNodeConfig.getDataHost();
                DataHostConfig dataHostConfig = dataHosts.get(dataHost);
                if (dataHostConfig != null) {
                    String dbType = dataHostConfig.getDbType();
                    dataNodeDbTypeMap.put(dataNodeName, dbType);
                }
            }
            schemaConfig.setDataNodeDbTypeMap(dataNodeDbTypeMap);
            schemas.put(name, schemaConfig);
        }
    }


    /**
     * ?????????????????????, ?????? YYYYMM???YYYYMMDD ????????????
     * <p>
     * YYYYMM????????? 	  yyyymm,2015,01,60
     * YYYYMMDD??????:  yyyymmdd,2015,01,10,50
     *
     * @param tableNameElement
     * @param tableNameSuffixElement
     * @return
     */
    private String doTableNameSuffix(String tableNameElement, String tableNameSuffixElement) {

        String newTableName = tableNameElement;

        String[] params = tableNameSuffixElement.split(",");
        String suffixFormat = params[0].toUpperCase();
        if (suffixFormat.equals("YYYYMM")) {

            //????????????
            int yyyy = Integer.parseInt(params[1]);
            int mm = Integer.parseInt(params[2]);
            int mmEndIdx = Integer.parseInt(params[3]);

            //????????????
            SimpleDateFormat yyyyMMSDF = new SimpleDateFormat("yyyyMM");

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, yyyy);
            cal.set(Calendar.MONTH, mm - 1);
            cal.set(Calendar.DATE, 0);

            //????????????
            StringBuffer tableNameBuffer = new StringBuffer();
            for (int mmIdx = 0; mmIdx <= mmEndIdx; mmIdx++) {
                tableNameBuffer.append(tableNameElement);
                tableNameBuffer.append(yyyyMMSDF.format(cal.getTime()));
                cal.add(Calendar.MONTH, 1);

                if (mmIdx != mmEndIdx) {
                    tableNameBuffer.append(",");
                }
            }
            newTableName = tableNameBuffer.toString();

        } else if (suffixFormat.equals("YYYYMMDD")) {

            //????????????
            int yyyy = Integer.parseInt(params[1]);
            int mm = Integer.parseInt(params[2]);
            int dd = Integer.parseInt(params[3]);
            int ddEndIdx = Integer.parseInt(params[4]);

            //????????????
            SimpleDateFormat yyyyMMddSDF = new SimpleDateFormat("yyyyMMdd");

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, yyyy);
            cal.set(Calendar.MONTH, mm - 1);
            cal.set(Calendar.DATE, dd);

            //????????????
            StringBuffer tableNameBuffer = new StringBuffer();
            for (int ddIdx = 0; ddIdx <= ddEndIdx; ddIdx++) {
                tableNameBuffer.append(tableNameElement);
                tableNameBuffer.append(yyyyMMddSDF.format(cal.getTime()));

                cal.add(Calendar.DATE, 1);

                if (ddIdx != ddEndIdx) {
                    tableNameBuffer.append(",");
                }
            }
            newTableName = tableNameBuffer.toString();
        }
        return newTableName;
    }


    private Map<String, TableConfig> loadTables(Element node) {

        // Map<String, TableConfig> tables = new HashMap<String, TableConfig>();

        // ???????????????????????????[`] BEN GONG
        final String schemaName = node.getAttribute("name");
        Map<String, TableConfig> tables = new TableConfigMap();
        NodeList nodeList = node.getElementsByTagName("table");
        List<Element> list = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element tableElement = (Element) nodeList.item(i);
            String tableNameElement = tableElement.getAttribute("name").toUpperCase();
            if("true".equalsIgnoreCase(tableElement.getAttribute("splitTableNames"))){
                String[] split = tableNameElement.split(",");
                for (String name : split) {
                    Element node1 = (Element)tableElement.cloneNode(true);
                    node1.setAttribute("name",name);
                    list.add(node1);
                }
            }else {
                list.add(tableElement);
            }
        }
        loadTable(schemaName, tables, list);
        return tables;
    }

    private void loadTable(String schemaName, Map<String, TableConfig> tables,  List<Element>  nodeList) {
        for (int i = 0; i < nodeList.size(); i++) {
            Element tableElement = (Element) nodeList.get(i);
            String tableNameElement = tableElement.getAttribute("name").toUpperCase();

            //TODO:??????, ?????????????????????????????????
            String tableNameSuffixElement = tableElement.getAttribute("nameSuffix").toUpperCase();
            if (!"".equals(tableNameSuffixElement)) {

                if (tableNameElement.split(",").length > 1) {
                    throw new ConfigException("nameSuffix " + tableNameSuffixElement + ", require name parameter cannot multiple breaks!");
                }
                //??????????????????????????????
                tableNameElement = doTableNameSuffix(tableNameElement, tableNameSuffixElement);
            }
            //?????????????????????????????????????????????????????????????????????
            String[] tableNames = tableNameElement.split(",");
            String primaryKey = tableElement.hasAttribute("primaryKey") ? tableElement.getAttribute("primaryKey").toUpperCase() : null;
            //?????????????????????????????????????????????????????????sequence handler???
            boolean autoIncrement = false;
            if (tableElement.hasAttribute("autoIncrement")) {
                autoIncrement = Boolean.parseBoolean(tableElement.getAttribute("autoIncrement"));
            }

            boolean fetchStoreNodeByJdbc = false;
            if (tableElement.hasAttribute("fetchStoreNodeByJdbc")) {
                fetchStoreNodeByJdbc = Boolean.parseBoolean(tableElement.getAttribute("fetchStoreNodeByJdbc"));
            }
            //????????????????????????????????????????????????????????????
            boolean needAddLimit = true;
            if (tableElement.hasAttribute("needAddLimit")) {
                needAddLimit = Boolean.parseBoolean(tableElement.getAttribute("needAddLimit"));
            }
            //??????type????????????global
            String tableTypeStr = tableElement.hasAttribute("type") ? tableElement.getAttribute("type") : null;
            int tableType = TableConfig.TYPE_GLOBAL_DEFAULT;
            if ("global".equalsIgnoreCase(tableTypeStr)) {
                tableType = TableConfig.TYPE_GLOBAL_TABLE;
            }
            //??????dataNode????????????????????????dataNode???
            String dataNode = tableElement.getAttribute("dataNode");
            TableRuleConfig tableRule = null;
            if (tableElement.hasAttribute("rule")) {
                String ruleName = tableElement.getAttribute("rule");
                tableRule = tableRules.get(ruleName);
                if (tableRule == null) {
                    throw new ConfigException("rule " + ruleName + " is not found!");
                }
            }

            boolean ruleRequired = false;
            //?????????????????????????????????
            if (tableElement.hasAttribute("ruleRequired")) {
                ruleRequired = Boolean.parseBoolean(tableElement.getAttribute("ruleRequired"));
            }

            if (tableNames == null) {
                throw new ConfigException("table name is not found!");
            }
            //distribute?????????????????????dataNode
            String distPrex = "distribute(";
            boolean distTableDns = dataNode.startsWith(distPrex);
            if (distTableDns) {
                dataNode = dataNode.substring(distPrex.length(), dataNode.length() - 1);
            }
            //????????????
            String subTables = tableElement.getAttribute("subTables");

            for (int j = 0; j < tableNames.length; j++) {

                String tableName = tableNames[j];
                TableRuleConfig tableRuleConfig = tableRule;
                if (tableRuleConfig != null) {
                    //????????????TableRuleAware???function??????????????????  ??????????????????????????????
                    RuleConfig rule = tableRuleConfig.getRule();
                    if (rule.getRuleAlgorithm() instanceof TableRuleAware) {
                        //??????ObjectUtil.copyObject????????????,????????????crc32??????????????????????????????,??????????????????????????????
                        tableRuleConfig = (TableRuleConfig) ObjectUtil.copyObject(tableRuleConfig);
                        String name = tableRuleConfig.getName();
                        String newRuleName = getNewRuleName(schemaName, tableName, name);
                        tableRuleConfig.setName(newRuleName);
                        TableRuleAware tableRuleAware = (TableRuleAware) tableRuleConfig.getRule().getRuleAlgorithm();
                        tableRuleAware.setRuleName(newRuleName);
                        tableRules.put(newRuleName, tableRuleConfig);
                    }
                }

                TableConfig table = new TableConfig(tableName, primaryKey,
                        autoIncrement, needAddLimit, tableType, dataNode,
                        getDbType(dataNode),
                        (tableRuleConfig != null) ? tableRuleConfig.getRule() : null,
                        ruleRequired, null, false, null, null, subTables, fetchStoreNodeByJdbc);
                //??????????????????TableConfig???????????????????????????dataNode????????????,??????Rule????????????????????? @cjw
                if ((tableRuleConfig != null) && (tableRuleConfig.getRule().getRuleAlgorithm() instanceof TableRuleAware)) {
                    AbstractPartitionAlgorithm newRuleAlgorithm = tableRuleConfig.getRule().getRuleAlgorithm();
                    ((TableRuleAware)newRuleAlgorithm).setTableConfig(table);
                    newRuleAlgorithm.init();
                }
                checkDataNodeExists(table.getDataNodes());
                // ?????????????????????????????????????????????
                if (table.getRule() != null) {
                    checkRuleSuitTable(table);
                }

                if (distTableDns) {
                    distributeDataNodes(table.getDataNodes());
                }
                //????????????
                if (tables.containsKey(table.getName())) {
                    throw new ConfigException("table " + tableName + " duplicated!");
                }
                //??????map
                tables.put(table.getName(), table);
            }
            //??????tableName???????????????????????????????????????????????????????????????
            if (tableNames.length == 1) {
                TableConfig table = tables.get(tableNames[0]);
                // process child tables
                processChildTables(tables, table, dataNode, tableElement);
            }
        }
    }

    private String getNewRuleName(String schemaName, String tableName, String name) {
        return name + "_" + schemaName + "_" + tableName;
    }

    /**
     * distribute datanodes in multi hosts,means ,dn1 (host1),dn100
     * (host2),dn300(host3),dn2(host1),dn101(host2),dn301(host3)...etc
     * ?????????host??????datanode??????host????????????????????????????????????host1??????dn1,dn2???host2??????dn100???dn101???host3??????dn300???dn301,
     * ??????host??????????????? 0->dn1 (host1),1->dn100(host2),2->dn300(host3),3->dn2(host1),4->dn101(host2),5->dn301(host3)
     *
     * @param theDataNodes
     */
    private void distributeDataNodes(ArrayList<String> theDataNodes) {
        Map<String, ArrayList<String>> newDataNodeMap = new HashMap<String, ArrayList<String>>(dataHosts.size());
        for (String dn : theDataNodes) {
            DataNodeConfig dnConf = dataNodes.get(dn);
            String host = dnConf.getDataHost();
            ArrayList<String> hostDns = newDataNodeMap.get(host);
            hostDns = (hostDns == null) ? new ArrayList<String>() : hostDns;
            hostDns.add(dn);
            newDataNodeMap.put(host, hostDns);
        }

        ArrayList<String> result = new ArrayList<String>(theDataNodes.size());
        boolean hasData = true;
        while (hasData) {
            hasData = false;
            for (ArrayList<String> dns : newDataNodeMap.values()) {
                if (!dns.isEmpty()) {
                    result.add(dns.remove(0));
                    hasData = true;
                }
            }
        }
        theDataNodes.clear();
        theDataNodes.addAll(result);
    }

    private Set<String> getDbType(String dataNode) {
        Set<String> dbTypes = new HashSet<>();
        String[] dataNodeArr = SplitUtil.split(dataNode, ',', '$', '-');
        for (String node : dataNodeArr) {
            DataNodeConfig datanode = dataNodes.get(node);
            DataHostConfig datahost = dataHosts.get(datanode.getDataHost());
            dbTypes.add(datahost.getDbType());
        }

        return dbTypes;
    }

    private Set<String> getDataNodeDbTypeMap(String dataNode) {
        Set<String> dbTypes = new HashSet<>();
        String[] dataNodeArr = SplitUtil.split(dataNode, ',', '$', '-');
        for (String node : dataNodeArr) {
            DataNodeConfig datanode = dataNodes.get(node);
            DataHostConfig datahost = dataHosts.get(datanode.getDataHost());
            dbTypes.add(datahost.getDbType());
        }
        return dbTypes;
    }

    private boolean isHasMultiDbType(TableConfig table) {
        Set<String> dbTypes = table.getDbTypes();
        for (String dbType : dbTypes) {
            if (!"mysql".equalsIgnoreCase(dbType)) {
                return true;
            }
        }
        return false;
    }

    private void processChildTables(Map<String, TableConfig> tables,
                                    TableConfig parentTable, String dataNodes, Element tableNode) {

        // parse child tables
        NodeList childNodeList = tableNode.getChildNodes();
        for (int j = 0; j < childNodeList.getLength(); j++) {
            Node theNode = childNodeList.item(j);
            if (!theNode.getNodeName().equals("childTable")) {
                continue;
            }
            Element childTbElement = (Element) theNode;
            //??????????????????
            String cdTbName = childTbElement.getAttribute("name").toUpperCase();
            String primaryKey = childTbElement.hasAttribute("primaryKey") ? childTbElement.getAttribute("primaryKey").toUpperCase() : null;

            boolean autoIncrement = false;
            if (childTbElement.hasAttribute("autoIncrement")) {
                autoIncrement = Boolean.parseBoolean(childTbElement.getAttribute("autoIncrement"));
            }
            boolean needAddLimit = true;
            if (childTbElement.hasAttribute("needAddLimit")) {
                needAddLimit = Boolean.parseBoolean(childTbElement.getAttribute("needAddLimit"));
            }

            String subTables = childTbElement.getAttribute("subTables");
            //??????join??????????????????parent????????????????????????????????????
            String joinKey = childTbElement.getAttribute("joinKey").toUpperCase();
            String parentKey = childTbElement.getAttribute("parentKey").toUpperCase();
            TableConfig table = new TableConfig(cdTbName, primaryKey,
                    autoIncrement, needAddLimit,
                    TableConfig.TYPE_GLOBAL_DEFAULT, dataNodes,
                    getDbType(dataNodes), null, false, parentTable, true,
                    joinKey, parentKey, subTables, false);

            if (tables.containsKey(table.getName())) {
                throw new ConfigException("table " + table.getName() + " duplicated!");
            }
            tables.put(table.getName(), table);
            //????????????????????????????????????
            processChildTables(tables, table, dataNodes, childTbElement);
        }
    }

    private void checkDataNodeExists(Collection<String> nodes) {
        if (nodes == null || nodes.size() < 1) {
            return;
        }
        for (String node : nodes) {
            if (!dataNodes.containsKey(node)) {
                throw new ConfigException("dataNode '" + node + "' is not found!");
            }
        }
    }

    /**
     * ?????????????????????????????????, ??????????????????????????????????????????????????????dataNode????????????<br>
     * ???????????????????????????:<br>
     * {@code
     * <table name="hotnews" primaryKey="ID" autoIncrement="true" dataNode="dn1,dn2"
     * rule="mod-long" />
     * }
     * <br>
     * ??????????????????:<br>
     * {@code
     * <function name="mod-long" class="io.mycat.route.function.PartitionByMod">
     * <!-- how many data nodes -->
     * <property name="count">3</property>
     * </function>
     * }
     * <br>
     * shard table datanode(2) < function count(3) ????????????????????????
     */
    private void checkRuleSuitTable(TableConfig tableConf) {
        AbstractPartitionAlgorithm function = tableConf.getRule().getRuleAlgorithm();
        int suitValue = function.suitableFor(tableConf);
        switch (suitValue) {
            case -1:
                // ?????????,?????????????????????
                throw new ConfigException("Illegal table conf : table [ " + tableConf.getName() + " ] rule function [ "
                        + tableConf.getRule().getFunctionName() + " ] partition size : " + tableConf.getRule().getRuleAlgorithm().getPartitionNum() + " > table datanode size : "
                        + tableConf.getDataNodes().size() + ", please make sure table datanode size = function partition size");
            case 0:
                // table datanode size == rule function partition size
                break;
            case 1:
                // ????????????????????????,??????warn log
                LOGGER.warn("table conf : table [ {} ] rule function [ {} ] partition size : {} < table datanode size : {} , this cause some datanode to be redundant",
                        new String[]{
                                tableConf.getName(),
                                tableConf.getRule().getFunctionName(),
                                String.valueOf(tableConf.getRule().getRuleAlgorithm().getPartitionNum()),
                                String.valueOf(tableConf.getDataNodes().size())
                        });
                break;
        }
    }

    private void loadDataNodes(Element root) {
        //??????DataNode??????
        NodeList list = root.getElementsByTagName("dataNode");
        for (int i = 0, n = list.getLength(); i < n; i++) {
            Element element = (Element) list.item(i);
            String dnNamePre = element.getAttribute("name");

            String databaseStr = element.getAttribute("database");
            String host = element.getAttribute("dataHost");
            //??????????????????
            if (empty(dnNamePre) || empty(databaseStr) || empty(host)) {
                throw new ConfigException("dataNode " + dnNamePre + " define error ,attribute can't be empty");
            }
            //dnNames???name???,databases???database???,hostStrings???dataHost??????????????????????????????',', '$', '-'???????????????????????????database?????????*dataHost?????????=name?????????
            //??????dataHost?????????database????????????????????????????????????dataHost????????????database
            //?????????<dataNode name="dn1$0-75" dataHost="localhost$1-10" database="db$0-759" />
            //?????????localhost1??????dn1$0-75,localhost2?????????dn1$0-75?????????db$76-151???
            String[] dnNames = io.mycat.util.SplitUtil.split(dnNamePre, ',', '$', '-');
            String[] databases = io.mycat.util.SplitUtil.split(databaseStr, ',', '$', '-');
            String[] hostStrings = io.mycat.util.SplitUtil.split(host, ',', '$', '-');

            if (dnNames.length > 1 && dnNames.length != databases.length * hostStrings.length) {
                throw new ConfigException("dataNode " + dnNamePre
                        + " define error ,dnNames.length must be=databases.length*hostStrings.length");
            }
            if (dnNames.length > 1) {

                List<String[]> mhdList = mergerHostDatabase(hostStrings, databases);
                for (int k = 0; k < dnNames.length; k++) {
                    String[] hd = mhdList.get(k);
                    String dnName = dnNames[k];
                    String databaseName = hd[1];
                    String hostName = hd[0];
                    createDataNode(dnName, databaseName, hostName);
                }

            } else {
                createDataNode(dnNamePre, databaseStr, host);
            }

        }
    }

    /**
     * ??????DataHost???Database?????????DataHost????????????Database??????
     *
     * @param hostStrings
     * @param databases
     * @return
     */
    private List<String[]> mergerHostDatabase(String[] hostStrings, String[] databases) {
        List<String[]> mhdList = new ArrayList<>();
        for (int i = 0; i < hostStrings.length; i++) {
            String hostString = hostStrings[i];
            for (int i1 = 0; i1 < databases.length; i1++) {
                String database = databases[i1];
                String[] hd = new String[2];
                hd[0] = hostString;
                hd[1] = database;
                mhdList.add(hd);
            }
        }
        return mhdList;
    }

    private void createDataNode(String dnName, String database, String host) {

        DataNodeConfig conf = new DataNodeConfig(dnName, database, host);
        if (dataNodes.containsKey(conf.getName())) {
            throw new ConfigException("dataNode " + conf.getName() + " duplicated!");
        }

        if (!dataHosts.containsKey(host)) {
            throw new ConfigException("dataNode " + dnName + " reference dataHost:" + host + " not exists!");
        }

        dataHosts.get(host).addDataNode(conf.getName());
        dataNodes.put(conf.getName(), conf);
    }

    private boolean empty(String dnName) {
        return dnName == null || dnName.length() == 0;
    }

    private DBHostConfig createDBHostConf(String dataHost, Element node,
                                          String dbType, String dbDriver, int maxCon, int minCon, String filters, long logTime) {

        String nodeHost = node.getAttribute("host");
        String nodeUrl = node.getAttribute("url");
        String user = node.getAttribute("user");
        String password = node.getAttribute("password");
        String usingDecrypt = node.getAttribute("usingDecrypt");
        String checkAliveText = node.getAttribute("checkAlive");
        if (checkAliveText == null)checkAliveText = Boolean.TRUE.toString();
        boolean checkAlive = Boolean.parseBoolean(checkAliveText);

        String passwordEncryty = DecryptUtil.DBHostDecrypt(usingDecrypt, nodeHost, user, password);

        String weightStr = node.getAttribute("weight");
        int weight = "".equals(weightStr) ? PhysicalDBPool.WEIGHT : Integer.parseInt(weightStr);

        String ip = null;
        int port = 0;
        if (empty(nodeHost) || empty(nodeUrl) || empty(user)) {
            throw new ConfigException(
                    "dataHost "
                            + dataHost
                            + " define error,some attributes of this element is empty: "
                            + nodeHost);
        }
        if ("native".equalsIgnoreCase(dbDriver)) {
            int colonIndex = nodeUrl.indexOf(':');
            ip = nodeUrl.substring(0, colonIndex).trim();
            port = Integer.parseInt(nodeUrl.substring(colonIndex + 1).trim());
        } else {
            URI url;
            try {
                url = new URI(nodeUrl.substring(5));
            } catch (Exception e) {
                throw new ConfigException("invalid jdbc url " + nodeUrl + " of " + dataHost);
            }
            ip = url.getHost();
            port = url.getPort();
        }

        DBHostConfig conf = new DBHostConfig(nodeHost, ip, port, nodeUrl, user, passwordEncryty, password,checkAlive);
        conf.setDbType(dbType);
        conf.setMaxCon(maxCon);
        conf.setMinCon(minCon);
        conf.setFilters(filters);
        conf.setLogTime(logTime);
        conf.setWeight(weight);    //????????????
        return conf;
    }

    private void loadDataHosts(Element root) {
        NodeList list = root.getElementsByTagName("dataHost");
        for (int i = 0, n = list.getLength(); i < n; ++i) {

            Element element = (Element) list.item(i);
            String name = element.getAttribute("name");
            //??????????????????
            if (dataHosts.containsKey(name)) {
                throw new ConfigException("dataHost name " + name + "duplicated!");
            }
            //?????????????????????
            int maxCon = Integer.parseInt(element.getAttribute("maxCon"));
            //?????????????????????
            int minCon = Integer.parseInt(element.getAttribute("minCon"));
            /**
             * ????????????????????????
             * 1. balance="0", ?????????????????????????????????????????????????????????????????? writeHost ??????
             * 2. balance="1"???????????? readHost ??? stand by writeHost ?????? select ???????????????
             * 3. balance="2"????????????????????????????????? writeHost???readhost ????????????
             * 4. balance="3"???????????????????????????????????? wiriterHost ????????? readhost ?????????writerHost ??????????????????
             */
            int balance = Integer.parseInt(element.getAttribute("balance"));
            /**
             * ??????????????????
             * 1. balanceType=0, ??????
             * 2. balanceType=1???????????????
             * 3. balanceType=2???????????????
             */
            String balanceTypeStr = element.getAttribute("balanceType");
            int balanceType = balanceTypeStr.equals("") ? 0 : Integer.parseInt(balanceTypeStr);
            /**
             * ??????????????????
             * -1 ?????????????????????
             * 1 ????????????????????????
             * 2 ??????MySQL???????????????????????????????????????
             * ??????????????? show slave status
             * 3 ?????? MySQL galary cluster ???????????????
             */
            String switchTypeStr = element.getAttribute("switchType");
            int switchType = switchTypeStr.equals("") ? -1 : Integer.parseInt(switchTypeStr);
            //?????????????????????
            String slaveThresholdStr = element.getAttribute("slaveThreshold");
            int slaveThreshold = slaveThresholdStr.equals("") ? -1 : Integer.parseInt(slaveThresholdStr);

            //?????? tempReadHostAvailable ???????????? 0 ????????????????????????????????? ??????????????????????????????
            String tempReadHostAvailableStr = element.getAttribute("tempReadHostAvailable");
            boolean tempReadHostAvailable = !tempReadHostAvailableStr.equals("") && Integer.parseInt(tempReadHostAvailableStr) > 0;
            /**
             * ?????? ?????????
             * ??????????????? 0 - ???????????????????????????????????? writeHost
             */
            String writeTypStr = element.getAttribute("writeType");
            int writeType = "".equals(writeTypStr) ? PhysicalDBPool.WRITE_ONLYONE_NODE : Integer.parseInt(writeTypStr);


            String dbDriver = element.getAttribute("dbDriver");
            String dbType = element.getAttribute("dbType");
            String filters = element.getAttribute("filters");
            String logTimeStr = element.getAttribute("logTime");
            String slaveIDs = element.getAttribute("slaveIDs");
            String maxRetryCountStr = element.getAttribute("maxRetryCount");
            int maxRetryCount;
            if (StringUtil.isEmpty(maxRetryCountStr)) {
                maxRetryCount = 3;
            } else {
                maxRetryCount = Integer.valueOf(maxRetryCountStr);
            }
            long logTime = "".equals(logTimeStr) ? PhysicalDBPool.LONG_TIME : Long.parseLong(logTimeStr);
			
            String notSwitch =  element.getAttribute("notSwitch");
			if(StringUtil.isEmpty(notSwitch)) {
				notSwitch = DataHostConfig.CAN_SWITCH_DS;
			}
            //??????????????????
            String heartbeatSQL = element.getElementsByTagName("heartbeat").item(0).getTextContent();
            //?????? ?????????sql??????,??????oracle
            NodeList connectionInitSqlList = element.getElementsByTagName("connectionInitSql");
            String initConSQL = null;
            if (connectionInitSqlList.getLength() > 0) {
                initConSQL = connectionInitSqlList.item(0).getTextContent();
            }
            //??????writeHost
            NodeList writeNodes = element.getElementsByTagName("writeHost");
            DBHostConfig[] writeDbConfs = new DBHostConfig[writeNodes.getLength()];
            Map<Integer, DBHostConfig[]> readHostsMap = new HashMap<Integer, DBHostConfig[]>(2);
            Set<String> writeHostNameSet = new HashSet<String>(writeNodes.getLength());
            for (int w = 0; w < writeDbConfs.length; w++) {
                Element writeNode = (Element) writeNodes.item(w);
                writeDbConfs[w] = createDBHostConf(name, writeNode, dbType, dbDriver, maxCon, minCon, filters, logTime);
                if (writeHostNameSet.contains(writeDbConfs[w].getHostName())) {
                    throw new ConfigException("writeHost " + writeDbConfs[w].getHostName() + " duplicated!");
                } else {
                    writeHostNameSet.add(writeDbConfs[w].getHostName());
                }
                NodeList readNodes = writeNode.getElementsByTagName("readHost");
                //????????????????????????readHost
                if (readNodes.getLength() != 0) {
                    DBHostConfig[] readDbConfs = new DBHostConfig[readNodes.getLength()];
                    Set<String> readHostNameSet = new HashSet<String>(readNodes.getLength());
                    for (int r = 0; r < readDbConfs.length; r++) {
                        Element readNode = (Element) readNodes.item(r);
                        readDbConfs[r] = createDBHostConf(name, readNode, dbType, dbDriver, maxCon, minCon, filters, logTime);
                        if (readHostNameSet.contains(readDbConfs[r].getHostName())) {
                            throw new ConfigException("readHost " + readDbConfs[r].getHostName() + " duplicated!");
                        } else {
                            readHostNameSet.add(readDbConfs[r].getHostName());
                        }
                    }
                    readHostsMap.put(w, readDbConfs);
                }
            }

            DataHostConfig hostConf = new DataHostConfig(name, dbType, dbDriver,
                    writeDbConfs, readHostsMap, switchType, slaveThreshold, tempReadHostAvailable);

            hostConf.setMaxCon(maxCon);
            hostConf.setMinCon(minCon);
            hostConf.setBalance(balance);
            hostConf.setBalanceType(balanceType);
            hostConf.setWriteType(writeType);
            hostConf.setHearbeatSQL(heartbeatSQL);
            hostConf.setConnectionInitSql(initConSQL);
            hostConf.setFilters(filters);
            hostConf.setLogTime(logTime);
            hostConf.setSlaveIDs(slaveIDs);
			hostConf.setNotSwitch(notSwitch);
            hostConf.setMaxRetryCount(maxRetryCount);
            dataHosts.put(hostConf.getName(), hostConf);
        }
    }

}
