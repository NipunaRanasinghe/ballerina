/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.test.types.finite;

import org.ballerinalang.test.BCompileUtil;
import org.ballerinalang.test.CompileResult;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.ballerinalang.test.BAssertUtil.validateError;

/**
 * Negative tests for finite types.
 *
 * @since 0.990.0
 */
public class FiniteTypeNegativeTest {

    private CompileResult resultNegativeOne, resultNegativeTwo;

    @BeforeClass
    public void setup() {
        resultNegativeOne = BCompileUtil.compile("test-src/types/finite/func_type_labeling_negative.bal");
        resultNegativeTwo = BCompileUtil.compile("test-src/types/finite/finite_type_negative.bal");
    }

    @Test()
    public void finiteAssignmentStateType() {
        validateError(resultNegativeOne, 0, "incompatible types: expected 'FuncType', " +
                "found 'function (int) returns (int)'", 20, 19);
        validateError(resultNegativeOne, 1, "incompatible types: expected 'string', " +
                "found 'int'", 23, 16);
        validateError(resultNegativeOne, 2, "incompatible types: expected 'FuncType', " +
                "found 'function (string) returns (string)'", 27, 19);
    }

    @Test()
    public void testInvalidLiteralAssignment() {
        int i = 0;
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'Finite', found 'string'",
                33, 16);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntType', found 'float'",
                52, 17);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'FloatType', found '5'",
                59, 19);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'FloatType', found '5d'",
                64, 19);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'DecimalType', found '5'",
                71, 21);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'DecimalType', found '5.0f'",
                76, 21);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntType', found 'int'",
                81, 17);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'string', " +
                "found 'StringOrIntVal'", 89, 17);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'int', found 'StringOrInt'",
                92, 14);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 't3', found 'float'",
                102, 13);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 't3', found 'float'",
                103, 13);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '(t|t2)', found 'float'",
                106, 14);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '(t|t2)', found 'decimal'",
                107, 14);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'Foo', found 'float'",
                116, 14);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'Foo2', found 'int'",
                117, 15);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'Foo4', found 'int'",
                118, 15);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '\"chiran\"', found 'int'",
                119, 18);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1f', found 'float'",
                123, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.0f', found 'float'",
                124, 13);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.121f', found 'float'",
                125, 15);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.2e12f', found 'float'",
                126, 16);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0x1p-1f', found 'float'",
                127, 16);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0x.12p12f', found 'decimal'",
                128, 18);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.2e12f', found 'decimal'",
                129, 16);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '2d', found 'decimal'",
                131, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.2d', found 'decimal'",
                132, 14);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.21d', found 'float'",
                133, 15);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0.1219e-1f', " +
                "found 'decimal'", 134, 19);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '12.1d', found 'float'",
                135, 15);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0x.12p12f', found 'decimal'",
                139, 22);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.2e12f', found 'decimal'",
                140, 20);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '2d', found 'decimal'",
                142, 16);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.2d', found 'decimal'",
                143, 18);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'Float1', found 'float'",
                148, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1f', found 'float'",
                150, 8);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.0f', found 'float'",
                151, 9);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.121f', found 'float'",
                152, 11);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.2e12f', found 'float'",
                153, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0x1p-1f', found 'float'",
                154, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0x.12p12f', found 'decimal'",
                155, 14);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.2e12f', found 'decimal'",
                156, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '2d', found 'decimal'",
                158, 8);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.2d', found 'decimal'",
                159, 10);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.21d', found 'float'",
                160, 11);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0.1219e-1f', " +
                "found 'decimal'", 161, 15);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '12.1d', found 'float'",
                162, 11);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '2d', found 'decimal'",
                165, 9);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1f', found 'float'",
                165, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0.1219e-1f', found 'float'",
                166, 9);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '0x.12p12f', found 'float'",
                166, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '2d', found '1f'",
                178, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '3f', found '2d'",
                179, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1f', found 'float'",
                183, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '2d', found 'string'",
                187, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '2d', found '2f'",
                191, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '3d', found '4f'",
                192, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '2f', found '2d'",
                193, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '3f', found '4d'",
                194, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '3f', found '4f'",
                195, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '3d', found '4d'",
                196, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNull', " +
                "found 'string'", 213, 19);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNull', " +
                "found 'IntOrNullStr'", 215, 19);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNull', " +
                "found '(int|\"null\")'", 217, 19);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNull', " +
                "found '\"null\"'", 219, 19);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNullStr', " +
                "found '()'", 221, 22);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNullStr', " +
                "found '()'", 222, 22);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNullStr', " +
                "found 'IntOrNull'", 224, 22);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNullStr', " +
                "found '(int|null)'", 226, 22);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOrNullStr', " +
                "found 'null'", 228, 22);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'null', " +
                "found 'string'", 231, 14);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '\"null\"', " +
                "found '()'", 232, 16);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'UnaryType2', " +
                "found 'int'", 240, 21);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'UnaryType3', " +
                "found 'int'", 241, 21);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1|5.4f', " +
                "found 'float'", 245, 15);
        validateError(resultNegativeTwo, i++, "'92233720368547758078' is out of range " +
                "for 'int'", 246, 11);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'FloatOne', " +
                "found 'IntOne'", 256, 18);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.0f', " +
                "found 'IntOne'", 257, 13);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'float', " +
                "found 'IntOne'", 258, 15);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'DecimalOne', " +
                "found 'IntOne'", 259, 20);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOne', " +
                "found 'FloatOne'", 262, 16);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1', " +
                "found 'FloatOne'", 263, 11);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'DecimalOne', " +
                "found 'FloatOne'", 264, 20);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'DecimalOne', " +
                "found 'float'", 265, 20);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '(FloatOne|StringA)', " +
                "found 'IntOne'", 266, 26);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'IntOne', " +
                "found 'DecimalOne'", 270, 16);
        validateError(resultNegativeTwo, i++, "incompatible types: expected 'FloatOne', " +
                "found 'DecimalOne'", 271, 18);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '(FloatOne|IntOne)', " +
                "found 'DecimalOne'", 272, 25);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.7976931348623157E309d', " +
                "found 'float'", 276, 34);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1.7976931348623157E309d', " +
                "found 'decimal'", 277, 34);
        validateError(resultNegativeTwo, i++, "'9223372036854775808' is out of range for 'int'",
                280, 21);
        validateError(resultNegativeTwo, i++, "unknown type 'testType'", 282, 5);
        validateError(resultNegativeTwo, i++, "'9223372036854775808' is out of range for 'int'",
                285, 30);
        validateError(resultNegativeTwo, i++, "unknown type 'testType'", 286, 5);
        validateError(resultNegativeTwo, i++, "'9223372036854775808' is out of range for 'int'",
                290, 20);
        validateError(resultNegativeTwo, i++, "unknown type 'InvalidTest1'", 292, 5);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1f', found 'float'",
                296, 12);
        validateError(resultNegativeTwo, i++, "incompatible types: expected '1f', found 'float'",
                297, 12);
        Assert.assertEquals(resultNegativeTwo.getErrorCount(), i);
    }

    @AfterClass
    public void tearDown() {
        resultNegativeOne = null;
        resultNegativeTwo = null;
    }
}
