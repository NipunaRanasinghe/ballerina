/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.mysql.init;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import org.ballerinalang.model.values.BError;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.mysql.utils.SQLDBUtils;
import org.ballerinalang.sql.Constants;
import org.ballerinalang.test.util.BCompileUtil;
import org.ballerinalang.test.util.BRunUtil;
import org.ballerinalang.test.util.CompileResult;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;

/**
 * This test case validates the connections.
 *
 * @since 1.2.0
 */
public class ConnectionInitTest {
    private static final String DB_NAME = "CONNECT_DB";
    private CompileResult result;
    private DB database;

    @BeforeClass
    public void setup() throws ManagedProcessException {
        result = BCompileUtil.compile(Paths.get("test-src", "connection",
                "connection_init_test.bal").toString());
        DBConfigurationBuilder configBuilder = DBConfigurationBuilder.newBuilder();
        configBuilder.setPort(SQLDBUtils.DB_PORT);
        configBuilder.setDataDir(SQLDBUtils.DB_DIRECTORY);
        configBuilder.setDeletingTemporaryBaseAndDataDirsOnShutdown(true);
        database = DB.newEmbeddedDB(configBuilder.build());
        database.start();
        database.createDB(DB_NAME, SQLDBUtils.DB_USER_NAME, SQLDBUtils.DB_USER_PW);
        String sqlFile = SQLDBUtils.SQL_RESOURCE_DIR + File.separator + SQLDBUtils.CONNECTIONS_DIR +
                File.separator + "connections_test_data.sql";
        database.source(sqlFile, DB_NAME);
    }

    @BeforeSuite
    protected void checkEnvironment() {
        if (!SQLDBUtils.ENABLE_TEST) {
            throw new SkipException("Tests cannot be executed in windows");
        }
    }

    @Test
    public void testWithMandatoryFields() {
        BValue[] returnVal = BRunUtil.invoke(result, "testConnectionWithNoFields");
        Assert.assertTrue(returnVal[0] instanceof BError);
        BError error = (BError) returnVal[0];
        Assert.assertEquals(error.getReason(), SQLDBUtils.SQL_APPLICATION_ERROR_REASON);
    }

    @Test
    public void testWithURLParams() {
        BValue[] args = {new BString(SQLDBUtils.DB_HOST), new BString(SQLDBUtils.DB_USER_NAME),
                new BString(SQLDBUtils.DB_USER_PW), new BString(DB_NAME), new BInteger(SQLDBUtils.DB_PORT)};
        BValue[] returnVal = BRunUtil.invoke(result, "testWithURLParams", args);
        Assert.assertNull(returnVal[0]);
    }

    @Test
    public void testWithoutHost() {
        BValue[] returnVal = BRunUtil.invoke(result, "testWithoutHost");
        Assert.assertNull(returnVal[0]);
    }

    @Test
    public void testWithOptions() {
        BValue[] returnVal = BRunUtil.invoke(result, "testWithOptions");
        Assert.assertNull(returnVal[0]);
    }

    @Test
    public void testWithConnectionPool() {
        BValue[] returnVal = BRunUtil.invoke(result, "testWithConnectionPool");
        Assert.assertFalse(returnVal[0] instanceof BError);
        Assert.assertTrue(returnVal[0] instanceof BMap);
        BMap connectionPool = (BMap) returnVal[0];
        Assert.assertEquals(connectionPool.get(Constants.ConnectionPool.MAX_CONNECTION_LIFE_TIME_SECONDS).stringValue()
                , "1800");
        Assert.assertEquals(connectionPool.get(Constants.ConnectionPool.MAX_OPEN_CONNECTIONS).stringValue(), "25");
    }

    @Test
    public void testWithConnectionParams() {
        BValue[] returnVal = BRunUtil.invoke(result, "testWithConnectionParams");
        Assert.assertNull(returnVal[0]);
    }

    @AfterClass
    public void cleanup() throws ManagedProcessException {
        SQLDBUtils.deleteDirectory(new File(SQLDBUtils.DB_DIRECTORY));
        database.stop();
    }
}
