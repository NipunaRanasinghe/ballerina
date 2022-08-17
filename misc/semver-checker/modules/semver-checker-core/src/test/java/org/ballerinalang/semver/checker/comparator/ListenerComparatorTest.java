/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
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

package org.ballerinalang.semver.checker.comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.ballerinalang.semver.checker.exception.SemverTestException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import static org.ballerinalang.semver.checker.util.TestUtils.executeTestData;

/**
 * Listener definition comparator related test cases.
 *
 * @since 2201.2.0
 */
public class ListenerComparatorTest {

    private static final String LISTENER_TEST_DATA_ROOT = "src/test/resources/testcases/ListenerDeclaration/";
    private static final String LISTENER_ANNOTATION_TESTCASE = LISTENER_TEST_DATA_ROOT + "annotation.json";
    private static final String LISTENER_DOCUMENTATION_TESTCASE = LISTENER_TEST_DATA_ROOT + "documentation.json";
    private static final String LISTENER_EXPRESSION_TESTCASE = LISTENER_TEST_DATA_ROOT + "listenerExpression.json";
    private static final String LISTENER_VARIABLE_NAME_TESTCASE = LISTENER_TEST_DATA_ROOT + "variableName.json";
    private static final String LISTENER_QUALIFIER_TESTCASE = LISTENER_TEST_DATA_ROOT + "qualifier.json";
    private static final String LISTENER_TYPE_DESCRIPTOR_TESTCASE = LISTENER_TEST_DATA_ROOT + "typeDescriptor.json";
    private static final String ADVANCE_LISTENER_TESTCASE = LISTENER_TEST_DATA_ROOT + "advanceListener.json";

    @Test(dataProvider = "ListenerTestDataProvider")
    public void testListenerAnnotation(JsonElement testData) throws Exception {
        executeTestData(testData);
    }

    @Test(dataProvider = "ListenerTestDataProvider")
    public void testListenerDocumentation(JsonElement testData) throws Exception {
        executeTestData(testData);
    }

    @Test(dataProvider = "ListenerTestDataProvider")
    public void testListenerExpression(JsonElement testData) throws Exception {
        executeTestData(testData);
    }

    @Test(dataProvider = "ListenerTestDataProvider")
    public void testListenerVariableName(JsonElement testData) throws Exception {
        executeTestData(testData);
    }

    @Test(dataProvider = "ListenerTestDataProvider")
    public void testListenerQualifier(JsonElement testData) throws Exception {
        executeTestData(testData);
    }

    @Test(dataProvider = "ListenerTestDataProvider")
    public void testListenerTypeDescriptor(JsonElement testData) throws Exception {
        executeTestData(testData);
    }

    @Test(dataProvider = "ListenerTestDataProvider")
    public void testAdvanceListener(JsonElement testData) throws Exception {
        executeTestData(testData);
    }

    @DataProvider(name = "ListenerTestDataProvider")
    public Object[] ListenerTestDataProvider(Method method) throws SemverTestException {
        String filePath;
        switch (method.getName()) {
            case "testListenerAnnotation":
                filePath = LISTENER_ANNOTATION_TESTCASE;
                break;
            case "testListenerDocumentation":
                filePath = LISTENER_DOCUMENTATION_TESTCASE;
                break;
            case "testListenerExpression":
                filePath = LISTENER_EXPRESSION_TESTCASE;
                break;
            case "testListenerVariableName":
                filePath = LISTENER_VARIABLE_NAME_TESTCASE;
                break;
            case "testListenerQualifier":
                filePath = LISTENER_QUALIFIER_TESTCASE;
                break;
            case "testListenerTypeDescriptor":
                filePath = LISTENER_TYPE_DESCRIPTOR_TESTCASE;
                break;
            case "testAdvanceListener":
                filePath = ADVANCE_LISTENER_TESTCASE;
                break;
            default:
                filePath = null;
        }

        if (filePath == null) {
            throw new SemverTestException("Failed to load dataset for method: " + method.getName());
        }
        try (FileReader reader = new FileReader(filePath)) {
            Object testCaseObject = JsonParser.parseReader(reader);
            JsonArray fileData = (JsonArray) testCaseObject;
            List<JsonElement> elementList = new LinkedList<>();
            fileData.forEach(elementList::add);
            return elementList.toArray();
        } catch (IOException e) {
            throw new SemverTestException("failed to load test data");
        }
    }
}
