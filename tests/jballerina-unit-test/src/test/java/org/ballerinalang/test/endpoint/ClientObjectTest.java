/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.test.endpoint;

import io.ballerina.runtime.api.utils.StringUtils;
import org.ballerinalang.test.BAssertUtil;
import org.ballerinalang.test.BCompileUtil;
import org.ballerinalang.test.BRunUtil;
import org.ballerinalang.test.CompileResult;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Client Object and Remote function related test cases.
 *
 * @since 0.985.0
 */
public class ClientObjectTest {

    private CompileResult remoteBasic;

    @BeforeClass
    public void setupRemoteBasic() {
        remoteBasic = BCompileUtil.compile("test-src/endpoint/new/remote_basic.bal");
    }

    @Test
    public void testRemoteFunctions() {
        Object returns = BRunUtil.invoke(remoteBasic, "test1", new Object[] { (4) });
        Assert.assertEquals(returns.toString(), "false");

        returns = BRunUtil.invoke(remoteBasic, "test1", new Object[] { (10) });
        Assert.assertEquals(returns.toString(), "true");
    }

    @Test
    public void testFunctions() {
        Object returns = BRunUtil.invoke(remoteBasic, "test2");
        Assert.assertEquals(returns.toString(), "5");
    }

    @Test
    public void testEndPointDeclInALoop() {
        Object result = BRunUtil.invoke(remoteBasic, "clientObjectDeclaredInLoop");
        Assert.assertEquals(result, 10L);
    }

    @Test
    public void testEndPointDeclInAIfStmtIfBlock() {
        Object result = BRunUtil.invoke(remoteBasic, "clientObjectDeclaredInIfStatement");
        Assert.assertEquals(result, 10L);
    }

    @Test
    public void testEndPointDeclInAIfStmtElseBlock() {
        Object result = BRunUtil.invoke(remoteBasic, "clientObjectDeclaredInIfStatementElseBlock");
        Assert.assertEquals(result, 10L);
    }

    @Test
    public void testReferringEndpointInDifferentPkg() {
        CompileResult compileResult = BCompileUtil.compile("test-src/endpoint/TestEndpointProject");

        BRunUtil.invoke(compileResult, "testCheck");

        BRunUtil.invoke(compileResult, "testNewEP", new Object[] { StringUtils.fromString("done") });
    }

    @Test
    public void testRemoteBasicsNegative() {
        CompileResult compileResult = BCompileUtil.compile("test-src/endpoint/new/remote_basic_negative.bal");
        int errIdx = 0;
        BAssertUtil.validateError(compileResult, errIdx++, "invalid qualifier 'remote'", 22, 1);
        BAssertUtil.validateError(compileResult, errIdx++, "invalid qualifier 'remote'", 26, 1);
        BAssertUtil.validateError(compileResult, errIdx++, "invalid qualifier 'remote'", 30, 1);
        BAssertUtil
                .validateError(compileResult, errIdx++,
                        "invalid remote method call '.pqr()': use '->pqr()' for remote method calls", 51, 13);
        BAssertUtil
                .validateError(compileResult, errIdx++,
                        "invalid method call '->abc()': '->' can only be used with remote methods", 53, 13);

        BAssertUtil.validateError(compileResult, errIdx++, "unknown type 'XXX'", 59, 5);
        BAssertUtil
                .validateError(compileResult, errIdx++,
                        "invalid remote method call: expected a client object, but found 'other'", 61, 13);
        BAssertUtil
                .validateError(compileResult, errIdx++,
                        "invalid remote method call: expected a client object, but found 'map'", 65, 9);
        BAssertUtil
                .validateError(compileResult, errIdx++,
                        "invalid remote method call: expected a client object, but found 'Bar'", 69, 9);
        BAssertUtil
                .validateError(compileResult, errIdx++,
                        "invalid remote method call '.pqr()': use '->pqr()' for remote method calls", 85, 13);
        BAssertUtil
                .validateError(compileResult, errIdx++,
                        "invalid remote method call '.pqr()': use '->pqr()' for remote method calls", 93, 13);
        BAssertUtil.validateError(compileResult, errIdx++, "remote qualifier only allowed in client and " +
                "service objects", 112, 5);
        BAssertUtil.validateError(compileResult, errIdx++, "remote qualifier only allowed in client and " +
                "service objects", 121, 5);
        BAssertUtil.validateError(compileResult, errIdx++, "invalid remote method call '.foo()': use '->foo()'" +
                " for remote method calls", 146, 5);
        BAssertUtil.validateError(compileResult, errIdx++, "invalid method call '->bar()': '->' can only be used" +
                " with remote methods", 147, 5);
        String doubleDeclMessage =
                "unsupported remote method name, '%s' already exists as a method or field name in the object type";
        BAssertUtil.validateError(compileResult, errIdx++,
                String.format(doubleDeclMessage, "DoubleMethod.a"), 155, 14);
        BAssertUtil.validateError(compileResult, errIdx++,
                String.format(doubleDeclMessage, "DoubleMethodOtherOrder.a"), 165, 21);
        BAssertUtil.validateError(compileResult, errIdx++, "redeclared symbol 'DoubleRemoteMethod.a'", 175, 21);
        BAssertUtil.validateError(compileResult, errIdx++,
                String.format(doubleDeclMessage, "$anonType$_0.a"), 186, 18);
        BAssertUtil.validateError(compileResult, errIdx++,
                String.format(doubleDeclMessage, "$anonType$_1.a"), 196, 25);
        BAssertUtil.validateError(compileResult, errIdx++, "redeclared symbol '$anonType$_2.a'", 206, 25);
        Assert.assertEquals(compileResult.getErrorCount(), errIdx);
    }

    @AfterClass
    public void tearDown() {
        remoteBasic = null;
    }
}
