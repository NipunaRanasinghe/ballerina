/*
 * Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.debugger.test.adapter.run;

import org.apache.commons.lang3.tuple.Pair;
import org.ballerinalang.debugger.test.DebugAdapterBaseTestCase;
import org.ballerinalang.debugger.test.utils.BallerinaTestDebugPoint;
import org.ballerinalang.debugger.test.utils.DebugUtils;
import org.ballerinalang.test.context.BallerinaTestException;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;

/**
 * Test class for single bal file related debug scenarios for run command.
 */
public class SingleBalFileRunDebugTest extends DebugAdapterBaseTestCase {

    @BeforeClass
    public void setup() {
        testSingleFileName = "hello_world.bal";
        testProjectPath = Paths.get(testProjectBaseDir.toString(), testProjectName).toString();
        testEntryFilePath = Paths.get(testSingleFileBaseDir.toString(), testSingleFileName).toString();
    }

    @Test
    public void testSingleBalFileDebugScenarios() throws BallerinaTestException {
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 23));
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 29));
        initDebugSession(DebugUtils.DebuggeeExecutionKind.RUN);

        // Test for debug engage
        Pair<BallerinaTestDebugPoint, StoppedEventArguments> debugHitInfo = waitForDebugHit(20000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(0));

        // Test for step over
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.STEP_OVER);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), new BallerinaTestDebugPoint(testEntryFilePath, 24));

        // Test for break point hit
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.NEXT_BREAKPOINT);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(1));

        // Test for step in
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.STEP_IN);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), new BallerinaTestDebugPoint(testEntryFilePath, 33));

        // Test for step out
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.STEP_OUT);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), new BallerinaTestDebugPoint(testEntryFilePath, 29));
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        terminateDebugSession();
    }
}
