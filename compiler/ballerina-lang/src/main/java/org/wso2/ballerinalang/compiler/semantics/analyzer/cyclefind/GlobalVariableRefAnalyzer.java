/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
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
package org.wso2.ballerinalang.compiler.semantics.analyzer.cyclefind;

import org.ballerinalang.model.symbols.SymbolKind;
import org.ballerinalang.model.tree.Node;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.TopLevelNode;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.semantics.analyzer.Types;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.tree.BLangClassDefinition;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.BLangTypeDefinition;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangConstant;
import org.wso2.ballerinalang.compiler.tree.types.BLangStructureTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analyze global variable reference patterns and reorder them in reverse dependency order.
 */
public class GlobalVariableRefAnalyzer {
    private static final CompilerContext.Key<GlobalVariableRefAnalyzer> REF_ANALYZER_KEY = new CompilerContext.Key<>();

    private final BLangDiagnosticLog dlog;
    private BLangPackage pkgNode;
    private Map<BSymbol, Set<BSymbol>> globalNodeDependsOn;
    Map<BSymbol, BSymbol> symbolOwner;
    private Map<BSymbol, Set<BVarSymbol>> globalVariablesDependsOn;
    private final Map<BSymbol, NodeInfo> dependencyNodes;
    private final Deque<NodeInfo> nodeInfoStack;
    private final List<List<NodeInfo>> cycles;
    private final List<NodeInfo> dependencyOrder;
    private int curNodeId;
    private boolean cyclicErrorFound;

    public static GlobalVariableRefAnalyzer getInstance(CompilerContext context) {
        GlobalVariableRefAnalyzer refAnalyzer = context.get(REF_ANALYZER_KEY);
        if (refAnalyzer == null) {
            refAnalyzer = new GlobalVariableRefAnalyzer(context);
        }
        return refAnalyzer;
    }

    private GlobalVariableRefAnalyzer(CompilerContext context) {
        context.put(REF_ANALYZER_KEY, this);
        this.dlog = BLangDiagnosticLog.getInstance(context);

        this.dependencyNodes = new HashMap<>();
        this.cycles = new ArrayList<>();
        this.nodeInfoStack = new ArrayDeque<>();
        this.dependencyOrder = new ArrayList<>();
        this.globalVariablesDependsOn = new HashMap<>();
    }

    private void resetAnalyzer() {
        this.dependencyNodes.clear();
        this.cycles.clear();
        this.nodeInfoStack.clear();
        this.dependencyOrder.clear();
        this.curNodeId = 0;
        this.globalVariablesDependsOn = new HashMap<>();
        this.cyclicErrorFound = false;
    }

    /**
     * Populate the InvokableSymbols with the dependent global variables.
     * @param globalNodeDependsOn symbol dependency relationship.
     * @param globalVars
     */
    public void populateFunctionDependencies(Map<BSymbol, Set<BSymbol>> globalNodeDependsOn,
                                             List<BLangVariable> globalVars) {
        resetAnalyzer();
        this.globalNodeDependsOn = globalNodeDependsOn;

        Set<BSymbol> dependentSet = this.globalNodeDependsOn.keySet();
        for (BSymbol dependent : dependentSet) {
            if (dependent.kind != SymbolKind.FUNCTION) {
                continue;
            }

            analyzeDependenciesRecursively(dependent, globalVars.stream()
                    .map(v -> v.symbol).collect(Collectors.toCollection(HashSet::new)));
        }
    }

    /**
     * Get the global variable dependency map.
     * @return global variable dependency map.
     */
    public Map<BSymbol, Set<BVarSymbol>> getGlobalVariablesDependsOn() {
        return this.globalVariablesDependsOn;
    }

    private void analyzeDependenciesRecursively(BSymbol dependent, Set<BSymbol> globalVars) {
        // Only analyze unvisited nodes.
        // Do DFS into dependency providers to detect cycles.
        if (!dependencyNodes.containsKey(dependent)) {
            NodeInfo node = new NodeInfo(curNodeId++, dependent);
            dependencyNodes.put(dependent, node);
            analyzeDependenciesRecursively(node, globalVars);
        }
    }

    private Set<BVarSymbol> analyzeDependenciesRecursively(NodeInfo node, Set<BSymbol> globalVars) {
        if (node.onStack) {
            return getGlobalVarFromCurrentNode(node, globalVars);
        }
        if (node.visited) {
            return getDependentsFromSymbol(node.symbol, globalVars);
        }

        node.visited = true;

        node.onStack = true;

        Set<BSymbol> providers = this.globalNodeDependsOn.getOrDefault(node.symbol, new LinkedHashSet<>());

        // Means no dependencies for this node.
        if (providers.isEmpty()) {
            return new HashSet<>(0);
        }

        // Means the current node has dependencies. Lets analyze its dependencies further.
        Set<BVarSymbol> currentDependencies = new HashSet<>();
        for (BSymbol providerSym : providers) {
            NodeInfo providerNode =
                    this.dependencyNodes.computeIfAbsent(providerSym, s -> new NodeInfo(curNodeId++, providerSym));
            if (isGlobalVarSymbol(providerSym, globalVars)) {
                currentDependencies.add((BVarSymbol) providerSym);
            }
            currentDependencies.addAll(analyzeDependenciesRecursively(providerNode, globalVars));
        }

        node.onStack = false;

        Set<BVarSymbol> dependentGlobalVars;
        if (node.symbol.kind == SymbolKind.FUNCTION) {
            dependentGlobalVars = ((BInvokableSymbol) node.symbol).dependentGlobalVars;
        } else {
            dependentGlobalVars = this.globalVariablesDependsOn.computeIfAbsent(node.symbol, s -> new HashSet<>());
        }

        dependentGlobalVars.addAll(currentDependencies);
        return dependentGlobalVars;
    }

    private Set<BVarSymbol> getGlobalVarFromCurrentNode(NodeInfo node, Set<BSymbol> globalVars) {
        Set<BVarSymbol> globalVarsForCurrentNode = new HashSet<>();
        Set<BSymbol> providers = this.globalNodeDependsOn.getOrDefault(node.symbol, new LinkedHashSet<>());

        for (BSymbol provider : providers) {
            if (isGlobalVarSymbol(provider, globalVars)) {
                globalVarsForCurrentNode.add((BVarSymbol) provider);
            }
        }

        return globalVarsForCurrentNode;
    }

    private Set<BVarSymbol> getDependentsFromSymbol(BSymbol symbol, Set<BSymbol> globalVars) {
        if (isFunction(symbol)) {
            return ((BInvokableSymbol) symbol).dependentGlobalVars;
        } else if (isGlobalVarSymbol(symbol, globalVars)) {
            return this.globalVariablesDependsOn.getOrDefault(symbol, new HashSet<>());
        }

        return new HashSet<>(0);
    }

    private boolean isFunction(BSymbol symbol) {
        return (symbol.tag & SymTag.FUNCTION) == SymTag.FUNCTION;
    }

    private boolean isGlobalVarSymbol(BSymbol symbol, Set<BSymbol> globalVars) {
        if (symbol == null) {
            return false;
        }
        if (symbol.owner == null) {
            return false;
        }
        if (symbol.owner.tag != SymTag.PACKAGE) {
            return false;
        }
        if ((symbol.tag & SymTag.FUNCTION) == SymTag.FUNCTION) {
            return false;
        }

        return ((symbol.tag & SymTag.VARIABLE) == SymTag.VARIABLE) && globalVars.contains(symbol);
    }

    /**
     * Analyze the global references and reorder them or emit error if they contain cyclic references.
     *
     * @param pkgNode package to be analyzed.
     * @param globalNodeDependsOn symbol dependency relationship.
     * @param symbolOwner symbol owner relationship.
     */
    public void analyzeAndReOrder(BLangPackage pkgNode, Map<BSymbol, Set<BSymbol>> globalNodeDependsOn,
                                  Map<BSymbol, BSymbol> symbolOwner) {
        this.dlog.setCurrentPackageId(pkgNode.packageID);
        this.pkgNode = pkgNode;
        this.globalNodeDependsOn = globalNodeDependsOn;
        this.symbolOwner = symbolOwner;
        resetAnalyzer();

        reOrderTopLevelNodeList();
    }

    private List<BSymbol> analyzeDependenciesStartingFrom(BSymbol symbol) {
        // Only analyze unvisited nodes.
        // Do DFS into dependency providers to detect cycles.
        if (!dependencyNodes.containsKey(symbol)) {
            NodeInfo node = new NodeInfo(curNodeId++, symbol);
            dependencyNodes.put(symbol, node);
            analyzeProvidersRecursively(node);
        }

        // Extract all the dependencies found in last call to analyzeProvidersRecursively
        if (!dependencyOrder.isEmpty()) {
            List<BSymbol> symbolsProvidersOrdered = this.dependencyOrder.stream()
                    .map(nodeInfo -> nodeInfo.symbol)
                    .toList();
            this.dependencyOrder.clear();
            return symbolsProvidersOrdered;
        }
        return new ArrayList<>();
    }

    private void reOrderTopLevelNodeList() {
        Map<BSymbol, TopLevelNode> varMap = collectAssociateSymbolsWithTopLevelNodes();

        Set<BSymbol> sorted = new LinkedHashSet<>();
        for (BSymbol symbol : varMap.keySet()) {
            sorted.addAll(analyzeDependenciesStartingFrom(symbol));
        }

        // Cyclic error found no need to sort.
        if (cyclicErrorFound) {
            return;
        }

        Set<TopLevelNode> sortedTopLevelNodes = new LinkedHashSet<>();
        for (BSymbol symbol : sorted) {
            if (varMap.containsKey(symbol)) {
                sortedTopLevelNodes.add(varMap.get(symbol));
            }
        }

        sortedTopLevelNodes.addAll(pkgNode.topLevelNodes);
        this.pkgNode.topLevelNodes.clear();
        this.pkgNode.topLevelNodes.addAll(sortedTopLevelNodes);
    }

    private Map<BSymbol, TopLevelNode> collectAssociateSymbolsWithTopLevelNodes() {
        // Create a single map to hold dependent functions first, then other variables
        Map<BSymbol, TopLevelNode> resultMap = new LinkedHashMap<>();
        // Temporary collection to hold other top level nodes except dependent functions
        Map<BSymbol, TopLevelNode> tempVarMap = new LinkedHashMap<>();
        for (TopLevelNode topLevelNode : this.pkgNode.topLevelNodes) {
            BSymbol symbol = getSymbolFromTopLevelNode(topLevelNode);
            if (symbol == null) {
                continue;
            }

            boolean isDependentFunction = (symbol.tag & SymTag.FUNCTION) == SymTag.FUNCTION &&
                    globalNodeDependsOn.containsKey(symbol);
            if (isDependentFunction) {
                resultMap.put(symbol, topLevelNode);
            } else {
                tempVarMap.put(symbol, topLevelNode);
            }
        }
        // Add all non-dependent symbols after dependent functions.
        // This will bring dependent functions to the top of the order.
        resultMap.putAll(tempVarMap);
        return resultMap;
    }

    private BSymbol getSymbolFromTopLevelNode(TopLevelNode topLevelNode) {
        return switch (topLevelNode.getKind()) {
            case VARIABLE, RECORD_VARIABLE, TUPLE_VARIABLE, ERROR_VARIABLE -> ((BLangVariable) topLevelNode).symbol;
            case TYPE_DEFINITION -> Types.getImpliedType(((BLangTypeDefinition) topLevelNode).symbol.type).tsymbol;
            case CONSTANT -> ((BLangConstant) topLevelNode).symbol;
            case FUNCTION -> ((BLangFunction) topLevelNode).symbol;
            default -> null;
        };
    }

    private int analyzeProvidersRecursively(NodeInfo node) {
        if (node.visited) {
            return node.lowLink;
        }

        node.visited = true;
        node.lowLink = node.id;
        node.onStack = true;
        nodeInfoStack.push(node);

        Set<BSymbol> providers = globalNodeDependsOn.getOrDefault(node.symbol, new LinkedHashSet<>());
        for (BSymbol providerSym : providers) {
            BSymbol symbol = symbolOwner.getOrDefault(providerSym, providerSym);
            NodeInfo providerNode =
                    dependencyNodes.computeIfAbsent(providerSym, s -> new NodeInfo(curNodeId++, symbol));
            int lastLowLink = analyzeProvidersRecursively(providerNode);
            if (providerNode.onStack) {
                node.lowLink = Math.min(node.lowLink, lastLowLink);
            }
        }
        // Cycle detected.
        if (node.id == node.lowLink) {
            handleCyclicReferenceError(node);
        }
        dependencyOrder.add(node);
        return node.lowLink;
    }

    private void handleCyclicReferenceError(NodeInfo node) {
        List<NodeInfo> cycle = new ArrayList<>();

        while (!nodeInfoStack.isEmpty()) {
            NodeInfo cNode = nodeInfoStack.pop();
            cNode.onStack = false;
            cNode.lowLink = node.id;
            cycle.add(cNode);
            if (cNode.id == node.id) {
                break;
            }
        }
        cycles.add(cycle);
        if (cycle.size() > 1) {
            cycle = new ArrayList<>(cycle);
            Collections.reverse(cycle);
            List<BSymbol> symbolsOfCycle = cycle.stream()
                    .map(n -> n.symbol)
                    .toList();

            if (doesContainAGlobalVar(symbolsOfCycle)) {
                emitErrorMessage(symbolsOfCycle);
                this.cyclicErrorFound = true;
            }
        }
    }

    private void emitErrorMessage(List<BSymbol> symbolsOfCycle) {
        List<TopLevelNode> nodesInCycle = new ArrayList<>();
        for (TopLevelNode topLevelNode : pkgNode.topLevelNodes) {
            BSymbol topLevelSymbol = getSymbol(topLevelNode);
            for (BSymbol symbol : symbolsOfCycle) {
                if (topLevelSymbol == symbol) {
                    nodesInCycle.add(topLevelNode);
                }
            }
        }

        Optional<TopLevelNode> firstNode = nodesInCycle.stream()
                .filter(node -> node.getKind() == NodeKind.VARIABLE)
                .min(Comparator.comparingInt(o -> o.getPosition().lineRange().startLine().line()));

        BSymbol firstNodeSymbol = getSymbol(firstNode.get());

        int splitFrom = symbolsOfCycle.indexOf(firstNodeSymbol);
        int len = symbolsOfCycle.size();
        List<BSymbol> firstSubList = new ArrayList<>(symbolsOfCycle.subList(0, splitFrom));
        List<BSymbol> secondSubList = new ArrayList<>(symbolsOfCycle.subList(splitFrom, len));
        secondSubList.addAll(firstSubList);

        List<BLangIdentifier> names = secondSubList.stream()
                .map(this::getNodeName).filter(Objects::nonNull).toList();
        dlog.error(firstNode.get().getPosition(), DiagnosticErrorCode.GLOBAL_VARIABLE_CYCLIC_DEFINITION, names);
    }

    private boolean doesContainAGlobalVar(List<BSymbol> symbolsOfCycle) {
        return pkgNode.globalVars.stream()
                .map(v -> v.symbol)
                .anyMatch(symbolsOfCycle::contains);
    }

    private BLangIdentifier getNodeName(BSymbol symbol) {
        for (TopLevelNode node : pkgNode.topLevelNodes) {
            if (getSymbol(node) == symbol) {
                if (node.getKind() == NodeKind.VARIABLE) {
                    return ((BLangSimpleVariable) node).name;
                } else if (node.getKind() == NodeKind.FUNCTION) {
                    return ((BLangFunction) node).name;
                } else if (node.getKind() == NodeKind.CLASS_DEFN) {
                    return ((BLangClassDefinition) node).name;
                } else if (node.getKind() == NodeKind.TYPE_DEFINITION) {
                    BLangType typeNode = ((BLangTypeDefinition) node).typeNode;
                    if (typeNode.getKind() == NodeKind.OBJECT_TYPE || typeNode.getKind() == NodeKind.RECORD_TYPE) {
                        return ((BLangTypeDefinition) node).name;
                    }
                }
            }
        }
        return null;
    }

    private BSymbol getSymbol(Node node) {
        if (node.getKind() == NodeKind.VARIABLE) {
            return ((BLangVariable) node).symbol;
        } else if (node.getKind() == NodeKind.FUNCTION) {
            return ((BLangFunction) node).symbol;
        } else if (node.getKind() == NodeKind.CLASS_DEFN) {
            return ((BLangClassDefinition) node).symbol;
        } else if (node.getKind() == NodeKind.TYPE_DEFINITION) {
            BLangType typeNode = ((BLangTypeDefinition) node).typeNode;
            if (typeNode.getKind() == NodeKind.OBJECT_TYPE || typeNode.getKind() == NodeKind.RECORD_TYPE) {
                return ((BLangStructureTypeNode) typeNode).symbol;
            }
        }
        return null;
    }

    private static class NodeInfo {
        final int id;
        final BSymbol symbol;
        int lowLink;
        boolean visited;
        boolean onStack;

        NodeInfo(int id, BSymbol symbol) {
            this.id = id;
            this.symbol = symbol;
        }

        @Override
        public String toString() {
            return "NodeInfo{" +
                    "id=" + id +
                    ", lowLink=" + lowLink +
                    ", visited=" + visited +
                    ", onStack=" + onStack +
                    ", symbol=" + symbol +
                    '}';
        }
    }
}
