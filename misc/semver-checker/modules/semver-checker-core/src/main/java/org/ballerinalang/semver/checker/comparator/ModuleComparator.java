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

import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.projects.Module;
import org.ballerinalang.semver.checker.diff.DiffExtractor;
import org.ballerinalang.semver.checker.diff.IDiff;
import org.ballerinalang.semver.checker.diff.ModuleDiff;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ModuleComparator implements IComparator {

    private final Module newModule;
    private final Module oldModule;
    private final Map<String, FunctionDefinitionNode> newFunctions = new HashMap<>();
    private final Map<String, FunctionDefinitionNode> oldFunctions = new HashMap<>();

    public ModuleComparator(Module newModule, Module oldModule) {
        this.newModule = newModule;
        this.oldModule = oldModule;
    }

    @Override
    public Optional<ModuleDiff> computeDiff() {
        ModuleDiff moduleDiff = new ModuleDiff();
        extractModuleLevelDefinitions(newModule, true);
        extractModuleLevelDefinitions(oldModule, false);

        analyzeFunctionDiffs(moduleDiff);
        // Todo: implement analyzers for other module-level definitions
        return Optional.of(moduleDiff);
    }

    private void analyzeFunctionDiffs(ModuleDiff moduleDiff) {
        DiffExtractor<FunctionDefinitionNode> functionDiffExtractor = new DiffExtractor<>(newFunctions, oldFunctions);
        functionDiffExtractor.getAdditions().forEach((name, function) -> moduleDiff.functionAdded(function));
        functionDiffExtractor.getRemovals().forEach((name, function) -> moduleDiff.functionRemoved(function));
        functionDiffExtractor.getCommons().forEach((name, functions) -> moduleDiff.functionChanged(functions.getKey(),
                functions.getValue()));
    }

    private void extractModuleLevelDefinitions(Module module, boolean isNewModule) {
        module.documentIds().forEach(documentId -> {
            SyntaxTree documentST = module.document(documentId).syntaxTree();
            if (documentST.rootNode() == null || (documentST.rootNode().kind() != SyntaxKind.MODULE_PART)) {
                return;
            }

            NodeList<ModuleMemberDeclarationNode> members = ((ModulePartNode) documentST.rootNode()).members();
            for (ModuleMemberDeclarationNode member : members) {
                switch (member.kind()) {
                    case FUNCTION_DEFINITION:
                        FunctionDefinitionNode funcNode = (FunctionDefinitionNode) member;
                        if (isNewModule) {
                            newFunctions.put(funcNode.functionName().text(), funcNode);
                        } else {
                            oldFunctions.put(funcNode.functionName().text(), funcNode);
                        }
                        return;
                    case CLASS_DEFINITION:
                    case TYPE_DEFINITION:
                    case MODULE_VAR_DECL:
                    case CONST_DECLARATION:
                    case LIST_CONSTRUCTOR:
                    case SERVICE_DECLARATION:
                    case ENUM_DECLARATION:
                    default:
                        // Todo: implement
                }
            }
        });

    }
}