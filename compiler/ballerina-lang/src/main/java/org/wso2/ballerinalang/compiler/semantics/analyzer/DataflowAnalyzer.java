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
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.ballerinalang.compiler.semantics.analyzer;

import io.ballerina.tools.diagnostics.Location;
import io.ballerina.types.PredefinedType;
import org.ballerinalang.compiler.CompilerPhase;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.symbols.SymbolKind;
import org.ballerinalang.model.tree.Node;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.OperatorKind;
import org.ballerinalang.model.tree.TopLevelNode;
import org.ballerinalang.model.tree.expressions.RecordLiteralNode;
import org.ballerinalang.model.tree.statements.VariableDefinitionNode;
import org.ballerinalang.model.tree.types.TypeNode;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.ballerinalang.util.diagnostic.DiagnosticWarningCode;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.semantics.analyzer.cyclefind.GlobalVariableRefAnalyzer;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BConstantSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.semantics.model.types.BArrayType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BErrorType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BField;
import org.wso2.ballerinalang.compiler.semantics.model.types.BMapType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BObjectType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotation;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotationAttachment;
import org.wso2.ballerinalang.compiler.tree.BLangBlockFunctionBody;
import org.wso2.ballerinalang.compiler.tree.BLangClassDefinition;
import org.wso2.ballerinalang.compiler.tree.BLangCompilationUnit;
import org.wso2.ballerinalang.compiler.tree.BLangErrorVariable;
import org.wso2.ballerinalang.compiler.tree.BLangExprFunctionBody;
import org.wso2.ballerinalang.compiler.tree.BLangExternalFunctionBody;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangImportPackage;
import org.wso2.ballerinalang.compiler.tree.BLangMarkdownDocumentation;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangRecordVariable;
import org.wso2.ballerinalang.compiler.tree.BLangResourceFunction;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.BLangTableKeySpecifier;
import org.wso2.ballerinalang.compiler.tree.BLangTestablePackage;
import org.wso2.ballerinalang.compiler.tree.BLangTupleVariable;
import org.wso2.ballerinalang.compiler.tree.BLangTypeDefinition;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.BLangXMLNS;
import org.wso2.ballerinalang.compiler.tree.OCEDynamicEnvironmentData;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangCollectClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangDoClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangFromClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangGroupByClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangGroupingKey;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangJoinClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangLetClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangLimitClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangMatchClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangOnClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangOnConflictClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangOnFailClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangOrderByClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangOrderKey;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangSelectClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangWhereClause;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangAccessExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangAlternateWorkerReceive;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangAnnotAccessExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangArrowFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangBinaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangCheckPanickedExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangCheckedExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangCollectContextInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangCommitExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangConstRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangConstant;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangElvisExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangErrorConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangErrorVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExtendedXMLNavigationAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangFieldBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangGroupExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIndexBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInferredTypedescDefaultNode;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation.BLangActionInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation.BLangResourceAccessInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIsAssignableExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLambdaFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLetExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangListConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMarkdownDocumentationLine;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMarkdownParameterDocumentation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMarkdownReturnParameterDocumentation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMatchGuard;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMultipleWorkerReceive;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNamedArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNaturalExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangObjectConstructorExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangQueryAction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangQueryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRawTemplateLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReAssertion;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReAtomCharOrEscape;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReAtomQuantifier;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReCapturingGroups;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReCharSet;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReCharSetRange;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReCharacterClass;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReFlagExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReQuantifier;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReSequence;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangReTerm;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRegExpTemplateLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRestArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangServiceConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangStringTemplateLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTableConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTernaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTransactionalExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTrapExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTupleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeConversionExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeInit;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeTestExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypedescExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangUnaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangVariableReference;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWaitExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWaitForAllExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWorkerAsyncSendExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWorkerFlushExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWorkerReceive;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWorkerSyncSendExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLAttribute;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLCommentLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLElementAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLElementLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLFilterStepExtend;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLIndexedStepExtend;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLMethodCallStepExtend;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLNavigationAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLProcInsLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLQName;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLQuotedString;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLSequenceLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLTextLiteral;
import org.wso2.ballerinalang.compiler.tree.matchpatterns.BLangMatchPattern;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBreak;
import org.wso2.ballerinalang.compiler.tree.statements.BLangCompoundAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangContinue;
import org.wso2.ballerinalang.compiler.tree.statements.BLangDo;
import org.wso2.ballerinalang.compiler.tree.statements.BLangErrorDestructure;
import org.wso2.ballerinalang.compiler.tree.statements.BLangErrorVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangExpressionStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangFail;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForeach;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForkJoin;
import org.wso2.ballerinalang.compiler.tree.statements.BLangIf;
import org.wso2.ballerinalang.compiler.tree.statements.BLangLock;
import org.wso2.ballerinalang.compiler.tree.statements.BLangMatchStatement;
import org.wso2.ballerinalang.compiler.tree.statements.BLangPanic;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRecordDestructure;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRecordVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRetry;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRetryTransaction;
import org.wso2.ballerinalang.compiler.tree.statements.BLangReturn;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRollback;
import org.wso2.ballerinalang.compiler.tree.statements.BLangSimpleVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStatement;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTransaction;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTupleDestructure;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTupleVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWhile;
import org.wso2.ballerinalang.compiler.tree.statements.BLangXMLNSStatement;
import org.wso2.ballerinalang.compiler.tree.types.BLangArrayType;
import org.wso2.ballerinalang.compiler.tree.types.BLangBuiltInRefTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangConstrainedType;
import org.wso2.ballerinalang.compiler.tree.types.BLangErrorType;
import org.wso2.ballerinalang.compiler.tree.types.BLangFiniteTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangFunctionTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangIntersectionTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangLetVariable;
import org.wso2.ballerinalang.compiler.tree.types.BLangObjectTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangRecordTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangStreamType;
import org.wso2.ballerinalang.compiler.tree.types.BLangTableTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangTupleTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangType;
import org.wso2.ballerinalang.compiler.tree.types.BLangUnionTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;
import org.wso2.ballerinalang.compiler.tree.types.BLangValueType;
import org.wso2.ballerinalang.compiler.util.ClosureVarSymbol;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.util.Flags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * Responsible for performing data flow analysis.
 * <p>
 * The following validations are done here:-
 * <ul>
 * <li>Uninitialized variable referencing validation</li>
 * </ul>
 *
 * @since 0.985.0
 */
public class DataflowAnalyzer extends BLangNodeVisitor {

    private final SymbolResolver symResolver;
    private final Names names;
    private SymbolEnv env;
    private final SymbolTable symTable;
    private final BLangDiagnosticLog dlog;
    private final Types types;
    private Map<BSymbol, InitStatus> uninitializedVars;
    private Map<BSymbol, Location> unusedErrorVarsDeclaredWithVar;
    private Map<BSymbol, Location> unusedLocalVariables;
    private final Map<BSymbol, BSymbol> symbolOwner;
    private final Map<BSymbol, BSymbol> dependsOnLambda;
    private final Map<BSymbol, Set<BSymbol>> invocationToDependent;
    private Map<BSymbol, Set<BSymbol>> globalNodeDependsOn;
    private Map<BSymbol, Set<BSymbol>> functionToDependency;
    private Map<BLangOnFailClause, Map<BSymbol, InitStatus>> possibleFailureUnInitVars;
    private Deque<BLangOnFailClause> enclosingOnFailClause;
    private boolean flowTerminated = false;
    private boolean possibleFailureReached = false;
    private boolean definiteFailureReached = false;

    private static final CompilerContext.Key<DataflowAnalyzer> DATAFLOW_ANALYZER_KEY = new CompilerContext.Key<>();
    private final Deque<BSymbol> currDependentSymbolDeque;
    private final GlobalVariableRefAnalyzer globalVariableRefAnalyzer;

    private DataflowAnalyzer(CompilerContext context) {
        context.put(DATAFLOW_ANALYZER_KEY, this);
        this.symTable = SymbolTable.getInstance(context);
        this.dlog = BLangDiagnosticLog.getInstance(context);
        this.types = Types.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.names = Names.getInstance(context);
        this.currDependentSymbolDeque = new ArrayDeque<>();
        this.globalVariableRefAnalyzer = GlobalVariableRefAnalyzer.getInstance(context);
        this.unusedLocalVariables = new HashMap<>();
        this.symbolOwner = new HashMap<>();
        this.dependsOnLambda = new HashMap<>();
        this.invocationToDependent = new HashMap<>();
    }

    public static DataflowAnalyzer getInstance(CompilerContext context) {
        DataflowAnalyzer dataflowAnalyzer = context.get(DATAFLOW_ANALYZER_KEY);
        if (dataflowAnalyzer == null) {
            dataflowAnalyzer = new DataflowAnalyzer(context);
        }
        return dataflowAnalyzer;
    }

    /**
     * Perform data-flow analysis on a package.
     *
     * @param pkgNode Package to perform data-flow analysis.
     * @return Data-flow analyzed package
     */
    public BLangPackage analyze(BLangPackage pkgNode) {
        this.uninitializedVars = new LinkedHashMap<>();
        this.globalNodeDependsOn = new LinkedHashMap<>();
        this.functionToDependency = new HashMap<>();
        this.possibleFailureUnInitVars = new LinkedHashMap<>();
        this.enclosingOnFailClause = new ArrayDeque<>();
        this.dlog.setCurrentPackageId(pkgNode.packageID);
        SymbolEnv pkgEnv = this.symTable.pkgEnvMap.get(pkgNode.symbol);
        analyzeNode(pkgNode, pkgEnv);
        return pkgNode;
    }

    @Override
    public void visit(BLangPackage pkgNode) {
        if (pkgNode.completedPhases.contains(CompilerPhase.DATAFLOW_ANALYZE)) {
            return;
        }
        Map<BSymbol, Location> prevUnusedErrorVarsDeclaredWithVar = this.unusedErrorVarsDeclaredWithVar;
        this.unusedErrorVarsDeclaredWithVar = new HashMap<>();

        Map<BSymbol, Location> prevUnusedLocalVariables = this.unusedLocalVariables;
        this.unusedLocalVariables = new HashMap<>();

        // Rearrange the top level nodes so that global variables come on top
        List<TopLevelNode> sortedListOfNodes = new ArrayList<>(pkgNode.globalVars);
        addModuleInitToSortedNodeList(pkgNode, sortedListOfNodes);
        addNodesToSortedNodeList(pkgNode, sortedListOfNodes);

        for (TopLevelNode topLevelNode : sortedListOfNodes) {
            if (isModuleInitFunction((BLangNode) topLevelNode)) {
                analyzeModuleInitFunc((BLangFunction) topLevelNode);
            } else {
                if (topLevelNode.getKind() == NodeKind.CLASS_DEFN) {
                    BLangClassDefinition classDef = (BLangClassDefinition) topLevelNode;
                    if (classDef.flagSet.contains(Flag.OBJECT_CTOR)) {
                        continue;
                    }
                }
                analyzeNode((BLangNode) topLevelNode, env);
            }
        }
        checkForUninitializedGlobalVars(pkgNode.globalVars);
        pkgNode.getTestablePkgs().forEach(testablePackage -> visit((BLangPackage) testablePackage));
        updateProvidersForLambdaInvocations();
        this.globalVariableRefAnalyzer.analyzeAndReOrder(pkgNode, this.globalNodeDependsOn, this.symbolOwner);
        this.globalVariableRefAnalyzer.populateFunctionDependencies(this.functionToDependency, pkgNode.globalVars);
        pkgNode.globalVariableDependencies = globalVariableRefAnalyzer.getGlobalVariablesDependsOn();
        checkUnusedImports(pkgNode.imports);

        emitUnusedVariableWarnings(this.unusedLocalVariables);
        this.unusedLocalVariables = prevUnusedLocalVariables;

        checkUnusedErrorVarsDeclaredWithVar();
        this.unusedErrorVarsDeclaredWithVar = prevUnusedErrorVarsDeclaredWithVar;

        pkgNode.completedPhases.add(CompilerPhase.DATAFLOW_ANALYZE);
    }

    /**
     * When seeing a lambda function expression, we do not add it as a provider to current dependent symbol.
     * Instead, we need to add it as a provider when it is invoked. This method is used to update the providers
     * for those invocations.
     */
    private void updateProvidersForLambdaInvocations() {
        // eg:
        // function() returns int a = function() returns int { return 1; };
        // int b = a();
        // int c = a();
        // `a` is already a provider for `b` and `c`. But the lambda function is not added as a provider for `a`. Hence,
        // lambda function will not be a provider of `b` and `c` at this point.
        // This function will add the lambda as a provider for `b` and `c`
        //
        // dependsOnLambda will contain a -> lambda
        // invocationToDependent will contain a -> {b, c}
        // now the lambda should be a provider for `b` and `c`
        for (Map.Entry<BSymbol, BSymbol> entry : dependsOnLambda.entrySet()) {
            BSymbol dependent = entry.getKey();
            BSymbol lambda = entry.getValue();
            if (invocationToDependent.containsKey(dependent)) {
                Set<BSymbol> dependents = invocationToDependent.get(dependent);
                dependents.forEach(d -> {
                    Set<BSymbol> providers = globalNodeDependsOn.computeIfAbsent(d, s -> new LinkedHashSet<>());
                    providers.add(lambda);
                });
            }
        }
    }

    private void addModuleInitToSortedNodeList(BLangPackage pkgNode, List<TopLevelNode> sortedListOfNodes) {
        for (TopLevelNode node : pkgNode.topLevelNodes) {
            if (isModuleInitFunction((BLangNode) node)) {
                sortedListOfNodes.add(node);
                break;
            }
        }
    }

    private void addNodesToSortedNodeList(BLangPackage pkgNode, List<TopLevelNode> sortedListOfNodes) {
        pkgNode.topLevelNodes.forEach(topLevelNode -> {
            if (!sortedListOfNodes.contains(topLevelNode)) {
                sortedListOfNodes.add(topLevelNode);
            }
        });
    }

    private boolean isModuleInitFunction(BLangNode node) {
        return node.getKind() == NodeKind.FUNCTION &&
                Names.USER_DEFINED_INIT_SUFFIX.value.equals(((BLangFunction) node).name.value);
    }

    private void analyzeModuleInitFunc(BLangFunction funcNode) {
        Map<BSymbol, Location> prevUnusedLocalVariables = this.unusedLocalVariables;
        this.unusedLocalVariables = new HashMap<>();

        this.currDependentSymbolDeque.push(funcNode.symbol);
        SymbolEnv moduleInitFuncEnv = SymbolEnv.createModuleInitFunctionEnv(funcNode, funcNode.symbol.scope, env);
        for (BLangAnnotationAttachment bLangAnnotationAttachment : funcNode.annAttachments) {
            analyzeNode(bLangAnnotationAttachment.expr, env);
        }
        analyzeNode(funcNode.body, moduleInitFuncEnv);
        this.currDependentSymbolDeque.pop();

        emitUnusedVariableWarnings(this.unusedLocalVariables);
        this.unusedLocalVariables = prevUnusedLocalVariables;
    }

    private void checkForUninitializedGlobalVars(List<BLangVariable> globalVars) {
        for (BLangVariable globalVar : globalVars) {
            if (globalVar.getKind() == NodeKind.VARIABLE && this.uninitializedVars.containsKey(globalVar.symbol)) {
                this.dlog.error(globalVar.pos, DiagnosticErrorCode.UNINITIALIZED_VARIABLE, globalVar.symbol);
            }
        }
    }

    @Override
    public void visit(BLangResourceFunction funcNode) {
        visit((BLangFunction) funcNode);
    }

    @Override
    public void visit(BLangFunction funcNode) {
        SymbolEnv funcEnv = SymbolEnv.createFunctionEnv(funcNode, funcNode.symbol.scope, env);

        Map<BSymbol, Location> prevUnusedLocalVariables = this.unusedLocalVariables;
        this.unusedLocalVariables = new HashMap<>();
        this.currDependentSymbolDeque.push(funcNode.symbol);

        funcNode.annAttachments.forEach(bLangAnnotationAttachment -> analyzeNode(bLangAnnotationAttachment.expr, env));
        funcNode.requiredParams.forEach(param -> analyzeNode(param, funcEnv));
        analyzeNode(funcNode.restParam, funcEnv);
        analyzeNode(funcNode.returnTypeNode, env);

        if (funcNode.flagSet.contains(Flag.OBJECT_CTOR)) {
            visitFunctionBodyWithDynamicEnv(funcNode, funcEnv);
        } else {
            analyzeBranch(funcNode.body, funcEnv);
        }

        this.currDependentSymbolDeque.pop();

        emitUnusedVariableWarnings(this.unusedLocalVariables);
        this.unusedLocalVariables = prevUnusedLocalVariables;
    }

    private void visitFunctionBodyWithDynamicEnv(BLangFunction funcNode, SymbolEnv funcEnv) {
        Map<BSymbol, Location> prevUnusedLocalVariables = this.unusedLocalVariables;
        this.unusedLocalVariables = new HashMap<>();
        this.unusedLocalVariables.putAll(prevUnusedLocalVariables);
        Map<BSymbol, InitStatus> prevUninitializedVars = this.uninitializedVars;

        // Get a snapshot of the current uninitialized vars before visiting the node.
        // This is done so that the original set of uninitialized vars will not be
        // updated/marked as initialized.
        this.uninitializedVars = copyUninitializedVars();
        this.flowTerminated = false;
        this.possibleFailureReached = false;
        this.definiteFailureReached = false;

        analyzeNode(funcNode.body, funcEnv);

        // Restore the original set of uninitialized vars
        this.uninitializedVars = prevUninitializedVars;

        prevUnusedLocalVariables.keySet().removeIf(bSymbol -> !this.unusedLocalVariables.containsKey(bSymbol));

        // Remove the entries added from the previous context since errors should be logged after the analysis
        // completes for that context.
        this.unusedLocalVariables.keySet().removeAll(prevUnusedLocalVariables.keySet());

        emitUnusedVariableWarnings(this.unusedLocalVariables);
        this.unusedLocalVariables = prevUnusedLocalVariables;
    }

    @Override
    public void visit(BLangBlockFunctionBody body) {
        SymbolEnv bodyEnv = SymbolEnv.createFuncBodyEnv(body, env);
        bodyEnv.isModuleInit = env.isModuleInit;
        for (BLangStatement statement : body.stmts) {
            analyzeNode(statement, bodyEnv);
        }
    }

    @Override
    public void visit(BLangExprFunctionBody body) {
        SymbolEnv bodyEnv = SymbolEnv.createFuncBodyEnv(body, env);
        analyzeNode(body.expr, bodyEnv);
    }

    @Override
    public void visit(BLangExternalFunctionBody body) {
        // do nothing
    }

    @Override
    public void visit(BLangBlockStmt blockNode) {
        SymbolEnv blockEnv = SymbolEnv.createBlockEnv(blockNode, env);
        blockNode.stmts.forEach(statement -> analyzeNode(statement, blockEnv));
    }

    @Override
    public void visit(BLangLetExpression letExpression) {
        for (BLangLetVariable letVarDeclaration : letExpression.letVarDeclarations) {
            analyzeNode((BLangNode) letVarDeclaration.definitionNode, letExpression.env);
        }
        analyzeNode(letExpression.expr, letExpression.env);
    }

    @Override
    public void visit(BLangCompilationUnit compUnit) {
    }

    @Override
    public void visit(BLangXMLNS xmlnsNode) {
    }

    @Override
    public void visit(BLangService service) {
        this.currDependentSymbolDeque.push(service.serviceClass.symbol);
        visit(service.serviceClass);
        for (BLangExpression attachedExpr : service.attachedExprs) {
            analyzeNode(attachedExpr, env);
        }

        service.annAttachments.forEach(bLangAnnotationAttachment -> analyzeNode(bLangAnnotationAttachment.expr, env));
        this.currDependentSymbolDeque.pop();
    }

    @Override
    public void visit(BLangTypeDefinition typeDefinition) {
        BSymbol symbol = typeDefinition.symbol;
        if (typeDefinition.symbol.kind == SymbolKind.TYPE_DEF) {
            symbol = symbol.type.tsymbol;
        }
        this.currDependentSymbolDeque.push(symbol);
        typeDefinition.annAttachments.forEach(attachment -> analyzeNode(attachment.expr, env));
        analyzeNode(typeDefinition.typeNode, env);
        this.currDependentSymbolDeque.pop();
    }

    @Override
    public void visit(BLangClassDefinition classDef) {
        SymbolEnv preEnv = env;
        SymbolEnv env = this.env;
        Map<BSymbol, Location> prevUnusedLocalVariables = null;
        Map<BSymbol, InitStatus> prevUninitializedVars = null;
        boolean visitedOCE = false;
        if (classDef.flagSet.contains(Flag.OBJECT_CTOR) && classDef.oceEnvData.capturedClosureEnv != null) {
            env = classDef.oceEnvData.capturedClosureEnv;
            prevUnusedLocalVariables = this.unusedLocalVariables;
            prevUninitializedVars = this.uninitializedVars;
            this.unusedLocalVariables = new HashMap<>();
            this.unusedLocalVariables.putAll(prevUnusedLocalVariables);
            this.uninitializedVars = copyUninitializedVars();
            this.flowTerminated = false;
            this.possibleFailureReached = false;
            this.definiteFailureReached = false;
            visitedOCE = true;
        }
        this.currDependentSymbolDeque.push(classDef.symbol);

        for (BLangAnnotationAttachment bLangAnnotationAttachment : classDef.annAttachments) {
            analyzeNode(bLangAnnotationAttachment.expr, env);
        }

        classDef.fields.forEach(field -> analyzeNode(field, this.env));
        classDef.referencedFields.forEach(field -> analyzeNode(field, this.env));

        // Visit the constructor with the same scope as the object
        analyzeNode(classDef.initFunction, env);
        if (classDef.initFunction != null) {
            SymbolEnv objectEnv = SymbolEnv.createClassEnv(classDef, classDef.symbol.scope, env);
            if (classDef.initFunction.body == null) {
                // if the init() function is defined as an outside function definition
                Optional<BLangFunction> outerFuncDef =
                        objectEnv.enclPkg.functions.stream()
                                .filter(f -> f.symbol.name.equals((classDef.initFunction).symbol.name))
                                .findFirst();
                outerFuncDef.ifPresent(bLangFunction -> classDef.initFunction = bLangFunction);
            }

            if (classDef.initFunction.body != null) {
                Map<BSymbol, Location> prevUnusedLocalVars = this.unusedLocalVariables;
                this.unusedLocalVariables = new HashMap<>();

                if (classDef.initFunction.body.getKind() == NodeKind.BLOCK_FUNCTION_BODY) {
                    for (BLangStatement statement :
                            ((BLangBlockFunctionBody) classDef.initFunction.body).stmts) {
                        analyzeNode(statement, objectEnv);
                    }
                } else if (classDef.initFunction.body.getKind() == NodeKind.EXPR_FUNCTION_BODY) {
                    analyzeNode(((BLangExprFunctionBody) classDef.initFunction.body).expr, objectEnv);
                }

                emitUnusedVariableWarnings(this.unusedLocalVariables);
                this.unusedLocalVariables = prevUnusedLocalVars;
            }
        }

        Stream.concat(classDef.fields.stream(), classDef.referencedFields.stream())
                .map(field -> {
                    addTypeDependency(classDef.symbol, field.getBType(), new HashSet<>());
                    return field; })
                .filter(field -> !Symbols.isPrivate(field.symbol))
                .forEach(field -> {
                    if (this.uninitializedVars.containsKey(field.symbol)) {
                        this.dlog.error(field.pos, DiagnosticErrorCode.OBJECT_UNINITIALIZED_FIELD, field.symbol);
                    }
                });

        for (BLangFunction function : classDef.functions) {
            analyzeNode(function, env);
        }
        for (BLangType type : classDef.typeRefs) {
            analyzeNode(type, env);
        }
        this.env = preEnv;

        if (visitedOCE) {
            this.uninitializedVars = prevUninitializedVars;
            prevUnusedLocalVariables.keySet().removeIf(bSymbol -> !this.unusedLocalVariables.containsKey(bSymbol));
            this.unusedLocalVariables = prevUnusedLocalVariables;
        }

        this.currDependentSymbolDeque.pop();
    }

    @Override
    public void visit(BLangObjectConstructorExpression objectConstructorExpression) {
        BLangClassDefinition classDef = objectConstructorExpression.classNode;
        if (classDef.flagSet.contains(Flag.OBJECT_CTOR)) {
            OCEDynamicEnvironmentData oceData = classDef.oceEnvData;
            for (BSymbol symbol : oceData.closureFuncSymbols) {
                this.unusedLocalVariables.remove(symbol);
            }
            for (BSymbol symbol : oceData.closureBlockSymbols) {
                this.unusedLocalVariables.remove(symbol);
            }
        }
        visit(objectConstructorExpression.classNode);
        visit(objectConstructorExpression.typeInit);
        addDependency(Types.getImpliedType(objectConstructorExpression.getBType()).tsymbol,
                objectConstructorExpression.classNode.symbol);
    }

    @Override
    public void visit(BLangSimpleVariableDef varDefNode) {
        BLangSimpleVariable var = varDefNode.var;
        if (var.expr == null) {
            addUninitializedVar(var);
            analyzeNode(var.typeNode, env);

            BVarSymbol symbol = var.symbol;

            if (var.getKind() == NodeKind.VARIABLE && isLocalVariableDefinedWithNonWildCardBindingPattern(var)) {
                this.unusedLocalVariables.put(symbol, var.pos);
            }

            return;
        }

        analyzeNode(var, env);
    }

    @Override
    public void visit(BLangSimpleVariable variable) {
        BVarSymbol symbol = variable.symbol;
        boolean isRecordField = variable.flagSet.contains(Flag.FIELD);
        if (!isRecordField) {
            this.currDependentSymbolDeque.push(symbol);
        }

        analyzeNode(variable.typeNode, env);
        if (symbol == null) {
            if (variable.expr != null) {
                analyzeNode(variable.expr, env);
            }
            return;
        }
        if (variable.typeNode != null && variable.typeNode.getBType() != null) {
            BType type = variable.typeNode.getBType();
            recordGlobalVariableReferenceRelationship(Types.getImpliedType(type).tsymbol);
        }
        boolean withInModuleVarLetExpr = symbol.owner.tag == SymTag.LET && isGlobalVarSymbol(env.enclVarSym);
        if (withInModuleVarLetExpr) {
            BVarSymbol dependentVar = env.enclVarSym;
            this.currDependentSymbolDeque.push(dependentVar);
        }
        try {
            boolean varWithInferredTypeIncludingError = false;
            if (variable.isDeclaredWithVar) {
                varWithInferredTypeIncludingError = addVarIfInferredTypeIncludesError(variable);
            }

            if (!varWithInferredTypeIncludingError &&
                    isLocalVariableDefinedWithNonWildCardBindingPattern(variable) &&
                    !isVariableDeclaredForWorkerDeclaration(variable)) {
                this.unusedLocalVariables.put(symbol, variable.pos);
            }

            if (variable.expr != null) {
                analyzeNode(variable.expr, env);
                this.uninitializedVars.remove(symbol);
                return;
            }
            // Required configurations will be initialized at the run time
            long varFlags = symbol.flags;
            if (Symbols.isFlagOn(varFlags, Flags.CONFIGURABLE) && Symbols.isFlagOn(varFlags, Flags.REQUIRED)) {
                return;
            }
            // Handle package/object level variables
            BSymbol owner = symbol.owner;
            if (owner.tag != SymTag.PACKAGE && owner.tag != SymTag.OBJECT) {
                return;
            }

            addUninitializedVar(variable);
        } finally {
            if (withInModuleVarLetExpr) { // double pop
                this.currDependentSymbolDeque.pop();
            }
            if (!isRecordField) {
                this.currDependentSymbolDeque.pop();
            }
        }
    }

    private boolean isVariableDeclaredForWorkerDeclaration(BLangSimpleVariable variable) {
        BLangExpression expr = variable.expr;

        if (expr == null) {
            return false;
        }

        if (Symbols.isFlagOn(variable.symbol.flags, Flags.WORKER)) {
            return true;
        }

        return expr.getKind() == NodeKind.LAMBDA && ((BLangLambdaFunction) expr).function.flagSet.contains(Flag.WORKER);
    }

    @Override
    public void visit(BLangAssignment assignment) {
        analyzeNode(assignment.expr, env);
        checkAssignment(assignment.varRef);
    }

    @Override
    public void visit(BLangCompoundAssignment compoundAssignNode) {
        analyzeNode(compoundAssignNode.expr, env);
        analyzeNode(compoundAssignNode.varRef, env);
        //compound statement can have assignment to itself. eg: x += x;
        //so we need to avoid removing the symbol from possibleFailureUnInitVars list after analyzing the expression.
        boolean resetOnFailInits = false;
        Map<BSymbol, InitStatus> onFailInitStatus = null;
        if (isOnFailEnclosed()) {
            onFailInitStatus = copyOnFailUninitializedVars(this.enclosingOnFailClause.peek());
            resetOnFailInits = onFailInitStatus.containsKey(compoundAssignNode.varRef.symbol);
        }
        checkAssignment(compoundAssignNode.varRef);
        if (resetOnFailInits) {
            updateUnInitVarsForOnFailClause(onFailInitStatus);
        }
        this.uninitializedVars.remove(compoundAssignNode.varRef.symbol);
    }

    @Override
    public void visit(BLangBreak breakNode) {
        terminateFlow();
    }

    @Override
    public void visit(BLangReturn returnNode) {
        analyzeNode(returnNode.expr, env);
        // If the regular block of code within a failure-handling statement
        // ends with a "return" statement, we only care about the outcome of the on-fail branch.
        this.definiteFailureReached = true;
        // return statement will exit from the function.
        terminateFlow();
    }

    @Override
    public void visit(BLangXMLNSStatement xmlnsStmt) {
        analyzeNode(xmlnsStmt.xmlnsDecl, env);
    }

    @Override
    public void visit(BLangIf ifNode) {
        analyzeNode(ifNode.expr, env);
        BranchResult ifResult = analyzeBranch(ifNode.body, env);
        BranchResult elseResult = analyzeBranch(ifNode.elseStmt, env);

        //if both if and else blocks contains uninitialized variables due to possible failure, then merge them.
        if (ifResult.possibleFailureUnInitVars != null && elseResult.possibleFailureUnInitVars != null) {
            updateUnInitVarsForOnFailClause(mergeUninitializedVars(ifResult.possibleFailureUnInitVars,
                    elseResult.possibleFailureUnInitVars));
        }
        if (!isOnFailEnclosed() || !ifResult.definiteFailureReached
                || !elseResult.definiteFailureReached) {
            boolean ifExprConst
                    = ConditionResolver.checkConstCondition(types, symTable, ifNode.expr) == symTable.trueType;
            // If the flow was terminated within 'if' block, then after the if-else block,
            // only the results of the 'else' block matters.
            if (ifResult.flowTerminated) {
                this.uninitializedVars = elseResult.uninitializedVars;
                this.possibleFailureReached = ifResult.possibleFailureReached;
                if (ifExprConst) {
                    this.flowTerminated = true;
                    this.definiteFailureReached = ifResult.definiteFailureReached;
                }
                return;
            }

            // If the flow was terminated within 'else' block, then after the if-else block,
            // only the results of the 'if' block matters.
            if (elseResult.flowTerminated || ifExprConst) {
                this.uninitializedVars = ifResult.uninitializedVars;
                if (ifResult.possibleFailureUnInitVars != null) {
                    updateUnInitVarsForOnFailClause(ifResult.possibleFailureUnInitVars);
                }
                return;
            }
        }
        this.uninitializedVars = mergeUninitializedVars(ifResult.uninitializedVars, elseResult.uninitializedVars);
        this.flowTerminated = ifResult.flowTerminated && elseResult.flowTerminated;
        this.definiteFailureReached = isDefiniteFailureCase(ifResult, elseResult);
    }

    @Override
    public void visit(BLangMatchStatement matchStatement) {

        analyzeNode(matchStatement.expr, env);
        if (matchStatement.onFailClause != null) {
            analyzeNode(matchStatement.onFailClause, env);
        }

        Map<BSymbol, InitStatus> uninitVars = new HashMap<>();
        BranchResult lastPatternResult = null;
        for (int i = 0; i < matchStatement.getMatchClauses().size(); i++) {
            BLangMatchClause matchClause = matchStatement.getMatchClauses().get(i);
            if (isLastPatternContainsIn(matchClause)) {
                lastPatternResult = analyzeBranch(matchClause, env);
            } else {
                BranchResult result = analyzeBranch(matchClause, env);
                // If the flow was terminated within the block, then that branch should not be considered for
                // analyzing the data-flow for the downstream code.
                if (result.flowTerminated) {
                    continue;
                }
                uninitVars = mergeUninitializedVars(uninitVars, result.uninitializedVars);
            }
        }

        if (lastPatternResult != null) {
            // only if last pattern is present, uninitializedVars should be updated
            uninitVars = mergeUninitializedVars(uninitVars, lastPatternResult.uninitializedVars);
            this.uninitializedVars = uninitVars;
            return;
        }
        uninitVars = mergeUninitializedVars(new HashMap<>(), this.uninitializedVars);
        this.uninitializedVars = uninitVars;
    }

    @Override
    public void visit(BLangMatchClause matchClause) {
        Location pos = matchClause.pos;
        for (BVarSymbol symbol : matchClause.declaredVars.values()) {
            if (!isWildCardBindingPattern(symbol)) {
                this.unusedLocalVariables.put(symbol, pos);
            }
        }
        analyzeNode(matchClause.matchGuard, env);
        analyzeNode(matchClause.blockStmt, env);
    }

    @Override
    public void visit(BLangMatchGuard matchGuard) {
        analyzeNode(matchGuard.expr, env);
    }

    private boolean isLastPatternContainsIn(BLangMatchClause matchClause) {

        for (BLangMatchPattern pattern : matchClause.matchPatterns) {
            if (pattern.isLastPattern) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visit(BLangForeach foreach) {
        BLangExpression collection = foreach.collection;

        if (isNotRangeExpr(collection)) {
            populateUnusedVariableMapForMembers(this.unusedLocalVariables,
                                                (BLangVariable) foreach.variableDefinitionNode.getVariable());
        }

        analyzeNode(collection, env);
        analyzeStmtWithOnFail(foreach.body, foreach.onFailClause);
    }

    @Override
    public void visit(BLangQueryAction queryAction) {
        for (BLangNode clause : queryAction.getQueryClauses()) {
            analyzeNode(clause, env);
        }
    }

    @Override
    public void visit(BLangWhile whileNode) {
        Map<BSymbol, InitStatus> prevUninitializedVars = this.uninitializedVars;

        analyzeNode(whileNode.expr, env);

        BType constCondition = ConditionResolver.checkConstCondition(types, symTable, whileNode.expr);
        boolean ifExprConst = constCondition == symTable.trueType;
        boolean failuresSelfHandled = whileNode.onFailClause != null;
        if (ifExprConst) {
            //if the condition is always true, we don't have to consider it as a branch.
            analyzeStmtWithOnFail(whileNode.body, whileNode.onFailClause);
        } else {
            createUninitializedVarsForOnFailClause(whileNode.onFailClause);
            BranchResult whileResult = analyzeBranch(whileNode.body, env);
            if (constCondition == symTable.falseType) {
                //if the condition is always false, we can reset to the previous state.
                this.uninitializedVars = prevUninitializedVars;
                removeEnclosingOnFail(failuresSelfHandled);
                return;
            }
            this.uninitializedVars = mergeUninitializedVars(this.uninitializedVars, whileResult.uninitializedVars);
            if (failuresSelfHandled) {
                BranchResult onfailResult = analyzeOnFailBranch(whileNode.onFailClause, whileResult);
                if (whileResult.definiteFailureReached) {
                    this.uninitializedVars = onfailResult.uninitializedVars;
                } else {
                    this.uninitializedVars
                            = mergeUninitializedVars(this.uninitializedVars, onfailResult.uninitializedVars);
                }
                updateEnclosingOnFailUnInits(this.uninitializedVars);
                removeEnclosingOnFail(true);
            }
        }
    }

    private void createUninitializedVarsForOnFailClause(BLangOnFailClause onFailClause) {
        if (onFailClause != null) {
            this.enclosingOnFailClause.push(onFailClause);
            this.possibleFailureUnInitVars.put(onFailClause, copyUninitializedVars());
        }
    }

    private void updateUnInitVarsForOnFailClause(Map<BSymbol, InitStatus> uninitializedVars) {
        if (isOnFailEnclosed()) {
            this.possibleFailureUnInitVars.put(this.enclosingOnFailClause.peek(), uninitializedVars);
        }
    }

    private void removeEnclosingOnFail(boolean onFailAvailable) {
        if (onFailAvailable) {
            this.possibleFailureUnInitVars.remove(this.enclosingOnFailClause.pop());
        }
    }

    @Override
    public void visit(BLangDo doNode) {
        analyzeStmtWithOnFail(doNode.body, doNode.onFailClause);
    }

    private void analyzeStmtWithOnFail(BLangBlockStmt blockStmt, BLangOnFailClause onFailClause) {
        createUninitializedVarsForOnFailClause(onFailClause);
        BranchResult doResult = analyzeBranch(blockStmt, env);
        this.uninitializedVars = doResult.uninitializedVars;
        if (onFailClause == null) {
            updateUnInitVarsForOnFailClause(doResult.possibleFailureUnInitVars);
            return;
        }

        // Analyze the on-fail block
        BranchResult onFailResult = analyzeOnFailBranch(onFailClause, doResult);
        if (blockStmt.failureBreakMode == BLangBlockStmt.FailureBreakMode.NOT_BREAKABLE) {
            // If the failureBreakMode is NOT_BREAKABLE, then the on-fail block is not reachable
            this.uninitializedVars
                    = mergeUninitializedVars(doResult.uninitializedVars, doResult.possibleFailureUnInitVars);
            removeEnclosingOnFail(true);
            return;
        }

        Map<BSymbol, InitStatus> mergedUninitializedVars =
                mergeUninitializedVars(doResult.uninitializedVars, onFailResult.uninitializedVars);
        // If the control flow is interrupted inside the 'onfail' block,
        // only the results of the 'do' block are relevant after the execution
        // of the entire 'stmt-with-on-fail' statement.
        if (onFailResult.flowTerminated || onFailResult.possibleFailureReached) {
            this.uninitializedVars = doResult.uninitializedVars;
        } else if (doResult.definiteFailureReached) {
            this.uninitializedVars = onFailResult.uninitializedVars;
        } else {
            this.uninitializedVars = mergedUninitializedVars;
        }

        updateEnclosingOnFailUnInits(mergedUninitializedVars);
        removeEnclosingOnFail(true);
    }

    private void updateEnclosingOnFailUnInits(Map<BSymbol, InitStatus> possibleUninitializedVars) {
        // Update the enclosing on-fail clause's possible failure uninitialized variables
        int enclosingOnFailSize = this.enclosingOnFailClause.size();
        if (enclosingOnFailSize > 1) {
            Iterator<BLangOnFailClause> iterator = this.enclosingOnFailClause.iterator();
            // get second top element
            iterator.next();
            BLangOnFailClause enclosingOnFail = iterator.next();
            this.possibleFailureUnInitVars.put(enclosingOnFail, possibleUninitializedVars);
        }
    }

    private BranchResult analyzeOnFailBranch(BLangOnFailClause onFailClause, BranchResult doResult) {
        Map<BSymbol, InitStatus> prevUninitializedVars = this.uninitializedVars;
        if (doResult.possibleFailureUnInitVars != null) {
            this.uninitializedVars = mergeUninitializedVars(this.uninitializedVars, doResult.possibleFailureUnInitVars);
        }
        this.possibleFailureUnInitVars.put(onFailClause, copyUninitializedVars());
        BranchResult onFailResult = analyzeBranch(onFailClause, env);
        if (!onFailResult.possibleFailureUnInitVars.isEmpty()) {
            onFailResult.uninitializedVars = mergeUninitializedVars(onFailResult.uninitializedVars,
                    onFailResult.possibleFailureUnInitVars);
        }
        this.uninitializedVars = prevUninitializedVars;
        return onFailResult;
    }

    private boolean isOnFailEnclosed() {
        return !this.enclosingOnFailClause.isEmpty();
    }

    private boolean isDefiniteFailureCase(BranchResult ifResult, BranchResult elseResult) {
        return ifResult.definiteFailureReached && elseResult.definiteFailureReached;
    }

    @Override
    public void visit(BLangFail failNode) {
        if (isOnFailEnclosed()) {
            this.possibleFailureReached = true;
            this.definiteFailureReached = true;
        }
        terminateFlow();
        analyzeNode(failNode.expr, env);
    }

    @Override
    public void visit(BLangLock lockNode) {
        analyzeStmtWithOnFail(lockNode.body, lockNode.onFailClause);
    }

    @Override
    public void visit(BLangTransaction transactionNode) {
        analyzeStmtWithOnFail(transactionNode.transactionBody, transactionNode.onFailClause);

        // marks the injected import as used
        Name transactionPkgName = Names.fromString(Names.DOT.value + Names.TRANSACTION_PACKAGE.value);
        Name compUnitName = Names.fromString(transactionNode.pos.lineRange().fileName());
        this.symResolver.resolvePrefixSymbol(env, transactionPkgName, compUnitName);
    }

    @Override
    public void visit(BLangTransactionalExpr transactionalExpr) {

    }

    @Override
    public void visit(BLangCommitExpr commitExpr) {

    }

    @Override
    public void visit(BLangRollback rollbackNode) {
        analyzeNode(rollbackNode.expr, env);
    }

    @Override
    public void visit(BLangTupleDestructure stmt) {
        analyzeNode(stmt.expr, env);
        checkAssignment(stmt.varRef);
    }

    @Override
    public void visit(BLangForkJoin forkJoin) {
         /* ignore */
    }

    @Override
    public void visit(BLangWorkerAsyncSendExpr asyncSendExpr) {
        analyzeNode(asyncSendExpr.expr, env);
    }

    @Override
    public void visit(BLangWorkerSyncSendExpr syncSendExpr) {
        analyzeNode(syncSendExpr.expr, env);
    }

    @Override
    public void visit(BLangAlternateWorkerReceive altWorkerReceive) {
        for (BLangWorkerReceive workerReceive : altWorkerReceive.getWorkerReceives()) {
            analyzeNode(workerReceive, env);
        }
    }

    @Override
    public void visit(BLangMultipleWorkerReceive multipleWorkerReceive) {
        for (BLangMultipleWorkerReceive.BLangReceiveField rvField : multipleWorkerReceive.getReceiveFields()) {
            analyzeNode(rvField.getKey(), env);
            analyzeNode(rvField.getWorkerReceive(), env);
        }
    }

    @Override
    public void visit(BLangWorkerReceive workerReceiveNode) {
       // todo
    }

    @Override
    public void visit(BLangLiteral literalExpr) {
    }

    @Override
    public void visit(BLangConstRef constRef) {
    }

    @Override
    public void visit(BLangListConstructorExpr listConstructorExpr) {
        for (BLangExpression expr : listConstructorExpr.exprs) {
            if (expr.getKind() == NodeKind.LIST_CONSTRUCTOR_SPREAD_OP) {
                expr = ((BLangListConstructorExpr.BLangListConstructorSpreadOpExpr) expr).expr;
            }
            analyzeNode(expr, env);
        }
    }

    @Override
    public void visit(BLangTableConstructorExpr tableConstructorExpr) {
        tableConstructorExpr.recordLiteralList.forEach(expr -> analyzeNode(expr, env));
        checkForDuplicateKeys(tableConstructorExpr);
    }

    private void checkForDuplicateKeys(BLangTableConstructorExpr tableConstructorExpr) {
        Set<Integer> keyHashSet = new HashSet<>();
        List<String> fieldNames = getFieldNames(tableConstructorExpr);
        HashMap<Integer, List<BLangExpression>> keyValues = new HashMap<>();
        if (!fieldNames.isEmpty()) {
            for (BLangRecordLiteral literal : tableConstructorExpr.recordLiteralList) {
                List<BLangExpression> keyArray = createKeyArray(literal, fieldNames);
                int hashInt = generateHash(keyArray);
                if (!keyHashSet.add(hashInt) && checkForKeyEquality(keyValues, keyArray, hashInt)) {
                    String fields = String.join(", ", fieldNames);
                    String values = keyArray.stream().map(Object::toString).collect(Collectors.joining(", "));
                    dlog.error(literal.pos, DiagnosticErrorCode.DUPLICATE_KEY_IN_TABLE_LITERAL, fields, values);
                }
                keyValues.put(hashInt, keyArray);
            }
        }
    }

    private boolean checkForKeyEquality(HashMap<Integer, List<BLangExpression>> keyValues,
                                        List<BLangExpression> keyArray, int hash) {
        List<BLangExpression> existingExpList = keyValues.get(hash);
        boolean isEqual = false;
        if (existingExpList.size() == keyArray.size()) {
            isEqual = true;
            for (int i = 0; i < keyArray.size(); i++) {
                isEqual = isEqual && equality(keyArray.get(i), existingExpList.get(i));
            }
        }
        return isEqual;
    }

    private int generateHash(List<BLangExpression> keyArray) {
        int result = 0;
        for (BLangExpression expr : keyArray) {
            result = 31 * result + hash(expr);
        }
        return result;
    }

    public boolean equality(Node nodeA, Node nodeB) {
        if (nodeA == null || nodeB == null) {
            return nodeA == nodeB;
        }

        if (nodeA.getKind() != nodeB.getKind()) {
            return false;
        }

        boolean isEqual = true;
        switch (nodeA.getKind()) {
            case RECORD_LITERAL_EXPR:
                BLangRecordLiteral recordLiteralA = (BLangRecordLiteral) nodeA;
                BLangRecordLiteral recordLiteralB = (BLangRecordLiteral) nodeB;
                for (int i = 0; isEqual && i < recordLiteralA.fields.size(); i++) {
                    RecordLiteralNode.RecordField exprA = recordLiteralA.fields.get(i);
                    RecordLiteralNode.RecordField exprB = recordLiteralB.fields.get(i);
                    isEqual = equality(exprA, exprB);
                }
                return isEqual;
            case RECORD_LITERAL_KEY_VALUE:
                BLangRecordLiteral.BLangRecordKeyValueField fieldA =
                        (BLangRecordLiteral.BLangRecordKeyValueField) nodeA;
                BLangRecordLiteral.BLangRecordKeyValueField fieldB =
                        (BLangRecordLiteral.BLangRecordKeyValueField) nodeB;
                return equality(fieldA.valueExpr, fieldB.valueExpr);
            case LITERAL:
            case NUMERIC_LITERAL:
                BLangLiteral literalA = (BLangLiteral) nodeA;
                BLangLiteral literalB = (BLangLiteral) nodeB;
                return Objects.equals(literalA.value, literalB.value);
            case XML_TEXT_LITERAL:
                BLangXMLTextLiteral textLiteralA = (BLangXMLTextLiteral) nodeA;
                BLangXMLTextLiteral textLiteralB = (BLangXMLTextLiteral) nodeB;
                isEqual = equality(textLiteralA.concatExpr, textLiteralB.concatExpr);
                for (int i = 0; isEqual && i < textLiteralA.textFragments.size(); i++) {
                    BLangExpression exprA = textLiteralA.textFragments.get(i);
                    BLangExpression exprB = textLiteralB.textFragments.get(i);
                    isEqual = equality(exprA, exprB);
                }
                return isEqual;
            case XML_ATTRIBUTE:
                BLangXMLAttribute attributeA = (BLangXMLAttribute) nodeA;
                BLangXMLAttribute attributeB = (BLangXMLAttribute) nodeB;
                return equality(attributeA.name, attributeB.name) && equality(attributeA.value, attributeB.value);
            case XML_QNAME:
                BLangXMLQName xmlqNameA = (BLangXMLQName) nodeA;
                BLangXMLQName xmlqNameB = (BLangXMLQName) nodeA;
                return equality(xmlqNameA.localname, xmlqNameB.localname)
                        && equality(xmlqNameA.prefix, xmlqNameB.prefix);
            case XML_ELEMENT_LITERAL:
                BLangXMLElementLiteral eleLiteralA = (BLangXMLElementLiteral) nodeA;
                BLangXMLElementLiteral eleLiteralB = (BLangXMLElementLiteral) nodeB;
                isEqual = equality(eleLiteralA.startTagName, eleLiteralB.startTagName)
                        && equality(eleLiteralA.endTagName, eleLiteralB.endTagName);
                for (int i = 0; isEqual && i < eleLiteralA.attributes.size(); i++) {
                    BLangExpression exprA = eleLiteralA.attributes.get(i);
                    BLangExpression exprB = eleLiteralB.attributes.get(i);
                    isEqual = equality(exprA, exprB);
                }
                for (int i = 0; isEqual && i < eleLiteralA.children.size(); i++) {
                    BLangExpression exprA = eleLiteralA.children.get(i);
                    BLangExpression exprB = eleLiteralB.children.get(i);
                    isEqual = equality(exprA, exprB);
                }
                return isEqual;
            case XML_COMMENT_LITERAL:
                BLangXMLCommentLiteral commentliteralA = (BLangXMLCommentLiteral) nodeA;
                BLangXMLCommentLiteral commentliteralB = (BLangXMLCommentLiteral) nodeB;
                isEqual = equality(commentliteralA.concatExpr, commentliteralB.concatExpr);
                for (int i = 0; isEqual && i < commentliteralA.textFragments.size(); i++) {
                    BLangExpression exprA = commentliteralA.textFragments.get(i);
                    BLangExpression exprB = commentliteralB.textFragments.get(i);
                    isEqual = equality(exprA, exprB);
                }
                return isEqual;
            case XML_QUOTED_STRING:
                BLangXMLQuotedString quotedLiteralA = (BLangXMLQuotedString) nodeA;
                BLangXMLQuotedString quotedLiteralB = (BLangXMLQuotedString) nodeB;
                isEqual = equality(quotedLiteralA.concatExpr, quotedLiteralB.concatExpr);
                for (int i = 0; isEqual && i < quotedLiteralA.textFragments.size(); i++) {
                    BLangExpression exprA = quotedLiteralA.textFragments.get(i);
                    BLangExpression exprB = quotedLiteralB.textFragments.get(i);
                    isEqual = equality(exprA, exprB);
                }
                return isEqual;
            case XMLNS:
                BLangXMLNS xmlnsA = (BLangXMLNS) nodeA;
                BLangXMLNS xmlnsB = (BLangXMLNS) nodeB;
                return equality(xmlnsA.prefix, xmlnsB.prefix) && equality(xmlnsA.namespaceURI, xmlnsB.namespaceURI);
            case XML_PI_LITERAL:
                BLangXMLProcInsLiteral insLiteralA = (BLangXMLProcInsLiteral) nodeA;
                BLangXMLProcInsLiteral insLiteralB = (BLangXMLProcInsLiteral) nodeB;
                isEqual = equality(insLiteralA.target, insLiteralB.target)
                        && equality(insLiteralA.dataConcatExpr, insLiteralB.dataConcatExpr);
                for (int i = 0; isEqual && i < insLiteralA.dataFragments.size(); i++) {
                    BLangExpression exprA = insLiteralA.dataFragments.get(i);
                    BLangExpression exprB = insLiteralB.dataFragments.get(i);
                    isEqual = equality(exprA, exprB);
                }
                return isEqual;
            case IDENTIFIER:
                BLangIdentifier identifierA = (BLangIdentifier) nodeA;
                BLangIdentifier identifierB = (BLangIdentifier) nodeB;
                return identifierA.value.equals(identifierB.value);
            case SIMPLE_VARIABLE_REF:
                BLangSimpleVarRef simpleVarRefA = (BLangSimpleVarRef) nodeA;
                BLangSimpleVarRef simpleVarRefB = (BLangSimpleVarRef) nodeB;
                BSymbol symbolA = simpleVarRefA.symbol;
                BSymbol symbolB = simpleVarRefB.symbol;
                if (symbolA != null && symbolB != null
                        && (Symbols.isFlagOn(symbolA.flags, Flags.CONSTANT)
                        && Symbols.isFlagOn(symbolB.flags, Flags.CONSTANT))) {
                    return (((BConstantSymbol) symbolA).value).value
                            .equals((((BConstantSymbol) symbolB).value).value);
                } else {
                    return simpleVarRefA.variableName.equals(simpleVarRefB.variableName);
                }
            case STRING_TEMPLATE_LITERAL:
                BLangStringTemplateLiteral stringTemplateLiteralA = (BLangStringTemplateLiteral) nodeA;
                BLangStringTemplateLiteral stringTemplateLiteralB = (BLangStringTemplateLiteral) nodeB;
                for (int i = 0; isEqual && i < stringTemplateLiteralA.exprs.size(); i++) {
                    BLangExpression exprA = stringTemplateLiteralA.exprs.get(i);
                    BLangExpression exprB = stringTemplateLiteralB.exprs.get(i);
                    isEqual = getTypeEquality(exprA.getBType(), exprB.getBType()) && equality(exprA, exprB);
                }
                return isEqual;
            case LIST_CONSTRUCTOR_EXPR:
                BLangListConstructorExpr listConstructorExprA = (BLangListConstructorExpr) nodeA;
                BLangListConstructorExpr listConstructorExprB = (BLangListConstructorExpr) nodeB;
                for (int i = 0; isEqual && i < listConstructorExprA.exprs.size(); i++) {
                    BLangExpression exprA = listConstructorExprA.exprs.get(i);
                    BLangExpression exprB = listConstructorExprB.exprs.get(i);
                    isEqual = getTypeEquality(exprA.getBType(), exprB.getBType()) && equality(exprA, exprB);
                }
                return isEqual;
            case TABLE_CONSTRUCTOR_EXPR:
                BLangTableConstructorExpr tableConstructorExprA = (BLangTableConstructorExpr) nodeA;
                BLangTableConstructorExpr tableConstructorExprB = (BLangTableConstructorExpr) nodeB;
                for (int i = 0; isEqual && i < tableConstructorExprA.recordLiteralList.size(); i++) {
                    BLangExpression exprA = tableConstructorExprA.recordLiteralList.get(i);
                    BLangExpression exprB = tableConstructorExprB.recordLiteralList.get(i);
                    isEqual = getTypeEquality(exprA.getBType(), exprB.getBType()) && equality(exprA, exprB);
                }
                return isEqual;
            case TYPE_CONVERSION_EXPR:
                BLangTypeConversionExpr typeConversionExprA = (BLangTypeConversionExpr) nodeA;
                BLangTypeConversionExpr typeConversionExprB = (BLangTypeConversionExpr) nodeB;
                return equality(typeConversionExprA.expr, typeConversionExprB.expr);
            case BINARY_EXPR:
                BLangBinaryExpr binaryExprA = (BLangBinaryExpr) nodeA;
                BLangBinaryExpr binaryExprB = (BLangBinaryExpr) nodeB;
                return equality(binaryExprA.lhsExpr, binaryExprB.lhsExpr)
                        && equality(binaryExprA.rhsExpr, binaryExprB.rhsExpr);
            case UNARY_EXPR:
                BLangUnaryExpr unaryExprA = (BLangUnaryExpr) nodeA;
                BLangUnaryExpr unaryExprB = (BLangUnaryExpr) nodeB;
                return equality(unaryExprA.expr, unaryExprB.expr);
            case TYPE_TEST_EXPR:
                BLangTypeTestExpr typeTestExprA = (BLangTypeTestExpr) nodeA;
                BLangTypeTestExpr typeTestExprB = (BLangTypeTestExpr) nodeB;
                return equality(typeTestExprA.expr, typeTestExprB.expr);
            case TERNARY_EXPR:
                BLangTernaryExpr ternaryExprA = (BLangTernaryExpr) nodeA;
                BLangTernaryExpr ternaryExprB = (BLangTernaryExpr) nodeB;
                return equality(ternaryExprA.expr, ternaryExprB.expr)
                        && equality(ternaryExprA.thenExpr, ternaryExprB.thenExpr)
                        && equality(ternaryExprA.elseExpr, ternaryExprB.elseExpr);
            case GROUP_EXPR:
                BLangGroupExpr groupExprA = (BLangGroupExpr) nodeA;
                BLangGroupExpr groupExprB = (BLangGroupExpr) nodeA;
                return equality(groupExprA.expression, groupExprB.expression);
            default:
                return false;
        }
    }

    public Integer hash(Node node) {
        int result = 0;

        if (node == null) {
            return result;
        }

        if (node.getKind() == NodeKind.RECORD_LITERAL_EXPR) {
            BLangRecordLiteral recordLiteral = (BLangRecordLiteral) node;
            for (RecordLiteralNode.RecordField entry : recordLiteral.fields) {
                result = 31 * result + hash(entry);
            }
        } else if (node.getKind() == NodeKind.RECORD_LITERAL_KEY_VALUE) {
            BLangRecordLiteral.BLangRecordKeyValueField field = (BLangRecordLiteral.BLangRecordKeyValueField) node;
            result = hash(field.key.expr) + hash(field.valueExpr);
        } else if (node.getKind() == NodeKind.ARRAY_LITERAL_EXPR) {
            BLangListConstructorExpr.BLangArrayLiteral arrayLiteral =
                    (BLangListConstructorExpr.BLangArrayLiteral) node;
            for (BLangExpression expr : arrayLiteral.exprs) {
                result = 31 * result + hash(expr);
            }
        } else if (node.getKind() == NodeKind.LITERAL | node.getKind() == NodeKind.NUMERIC_LITERAL) {
            BLangLiteral literal = (BLangLiteral) node;
            result = Objects.hash(literal.value);
        } else if (node.getKind() == NodeKind.XML_TEXT_LITERAL) {
            BLangXMLTextLiteral literal = (BLangXMLTextLiteral) node;
            result = hash(literal.concatExpr);
            for (BLangExpression expr : literal.textFragments) {
                result = result * 31 + hash(expr);
            }
        } else if (node.getKind() == NodeKind.XML_ATTRIBUTE) {
            BLangXMLAttribute attribute = (BLangXMLAttribute) node;
            result = hash(attribute.name) + hash(attribute.value);
        } else if (node.getKind() == NodeKind.XML_QNAME) {
            BLangXMLQName xmlqName = (BLangXMLQName) node;
            result = hash(xmlqName.localname) + hash(xmlqName.prefix);
        } else if (node.getKind() == NodeKind.XML_COMMENT_LITERAL) {
            BLangXMLCommentLiteral literal = (BLangXMLCommentLiteral) node;
            result = hash(literal.concatExpr);
            for (BLangExpression expr : literal.textFragments) {
                result = result * 31 + hash(expr);
            }
        } else if (node.getKind() == NodeKind.XML_ELEMENT_LITERAL) {
            BLangXMLElementLiteral literal = (BLangXMLElementLiteral) node;
            result = hash(literal.startTagName) + hash(literal.endTagName);
            for (BLangExpression expr : literal.attributes) {
                result = 31 * result + hash(expr);
            }
            for (BLangExpression expr : literal.children) {
                result = 31 * result + hash(expr);
            }
        } else if (node.getKind() == NodeKind.XML_QUOTED_STRING) {
            BLangXMLQuotedString literal = (BLangXMLQuotedString) node;
            result = hash(literal.concatExpr);
            for (BLangExpression expr : literal.textFragments) {
                result = result * 31 + hash(expr);
            }
        } else if (node.getKind() == NodeKind.XMLNS) {
            BLangXMLNS xmlns = (BLangXMLNS) node;
            result = hash(xmlns.prefix) + hash(xmlns.namespaceURI);
        } else if (node.getKind() == NodeKind.XML_PI_LITERAL) {
            BLangXMLProcInsLiteral literal = (BLangXMLProcInsLiteral) node;
            result = hash(literal.target) + hash(literal.dataConcatExpr);
            for (BLangExpression expr : literal.dataFragments) {
                result = result * 31 + hash(expr);
            }
        } else if (node.getKind() == NodeKind.IDENTIFIER) {
            BLangIdentifier identifier = (BLangIdentifier) node;
            result = identifier.value.hashCode();
        } else if (node.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
            BLangSimpleVarRef simpleVarRef = (BLangSimpleVarRef) node;
            BSymbol symbol = simpleVarRef.symbol;
            if (symbol != null && Symbols.isFlagOn(symbol.flags, Flags.CONSTANT)) {
                BConstantSymbol constantSymbol = (BConstantSymbol) symbol;
                result = Objects.hash(constantSymbol.value.value);
            } else {
                result = simpleVarRef.variableName.hashCode();
            }
        } else if (node.getKind() == NodeKind.STRING_TEMPLATE_LITERAL) {
            BLangStringTemplateLiteral stringTemplateLiteral = (BLangStringTemplateLiteral) node;
            for (BLangExpression expr : stringTemplateLiteral.exprs) {
                result = result * 31 + getTypeHash(stringTemplateLiteral.getBType()) + hash(expr);
            }
        } else if (node.getKind() == NodeKind.LIST_CONSTRUCTOR_EXPR) {
            BLangListConstructorExpr listConstructorExpr = (BLangListConstructorExpr) node;
            for (BLangExpression expr : listConstructorExpr.exprs) {
                result = result * 31 + getTypeHash(listConstructorExpr.getBType()) + hash(expr);
            }
        } else if (node.getKind() == NodeKind.TABLE_CONSTRUCTOR_EXPR) {
            BLangTableConstructorExpr tableConstructorExpr = (BLangTableConstructorExpr) node;
            for (BLangRecordLiteral recordLiteral : tableConstructorExpr.recordLiteralList) {
                result = result * 31 + getTypeHash(tableConstructorExpr.getBType()) + hash(recordLiteral);
            }
        } else if (node.getKind() == NodeKind.TYPE_CONVERSION_EXPR) {
            BLangTypeConversionExpr typeConversionExpr = (BLangTypeConversionExpr) node;
            result = hash(typeConversionExpr.expr);
        } else if (node.getKind() == NodeKind.BINARY_EXPR) {
            BLangBinaryExpr binaryExpr = (BLangBinaryExpr) node;
            result = hash(binaryExpr.lhsExpr) + hash(binaryExpr.rhsExpr);
        } else if (node.getKind() == NodeKind.UNARY_EXPR) {
            BLangUnaryExpr unaryExpr = (BLangUnaryExpr) node;
            result = hash(unaryExpr.expr);
        } else if (node.getKind() == NodeKind.TYPE_TEST_EXPR) {
            BLangTypeTestExpr typeTestExpr = (BLangTypeTestExpr) node;
            result = hash(typeTestExpr.expr);
        } else if (node.getKind() == NodeKind.TERNARY_EXPR) {
            BLangTernaryExpr ternaryExpr = (BLangTernaryExpr) node;
            result = hash(ternaryExpr.expr) + hash(ternaryExpr.thenExpr) + hash(ternaryExpr.elseExpr);
        } else if (node.getKind() == NodeKind.GROUP_EXPR) {
            BLangGroupExpr groupExpr = (BLangGroupExpr) node;
            result = hash(groupExpr.expression);
        } else if (node.getKind() == NodeKind.REG_EXP_TEMPLATE_LITERAL) {
            result = generateHashForRegExp(((BLangRegExpTemplateLiteral) node).reDisjunction.sequenceList);
        } else {
            dlog.error(((BLangExpression) node).pos, DiagnosticErrorCode.EXPRESSION_IS_NOT_A_CONSTANT_EXPRESSION);
        }
        return result;
    }

    private Integer generateHashForRegExp(List<BLangExpression> exprs) {
        int regexpHash = 0;
        for (BLangExpression expr : exprs) {
            if (expr.getKind() == NodeKind.REG_EXP_SEQUENCE) {
                BLangReSequence reSequence = (BLangReSequence) expr;
                for (BLangReTerm term : reSequence.termList) {
                    if (term.getKind() == NodeKind.REG_EXP_ASSERTION) {
                        regexpHash = regexpHash + hash(((BLangReAssertion) term).assertion);
                    } else {
                        regexpHash = regexpHash + generateHashForReAtomQuantifier((BLangReAtomQuantifier) term);
                    }
                }
            } else {
                regexpHash = regexpHash + hash(expr);
            }
        }
        return regexpHash;
    }

    private Integer generateHashForReAtomQuantifier(BLangReAtomQuantifier reAtomQuantifier) {
        int regexpHash = 0;
        BLangExpression reAtom = reAtomQuantifier.atom;
        if (reAtom.getKind() == NodeKind.REG_EXP_ATOM_CHAR_ESCAPE) {
            regexpHash = regexpHash + hash(((BLangReAtomCharOrEscape) reAtom).charOrEscape);
        } else if (reAtom.getKind() == NodeKind.REG_EXP_CHARACTER_CLASS) {
            regexpHash = regexpHash + generateHashForReCharacterClass((BLangReCharacterClass) reAtom);
        } else if (reAtom.getKind() == NodeKind.REG_EXP_CAPTURING_GROUP) {
            regexpHash = regexpHash + generateHashForReCapturingGroup((BLangReCapturingGroups) reAtom);
        }
        if (reAtomQuantifier.quantifier != null) {
            BLangReQuantifier reQuantifier = reAtomQuantifier.quantifier;
            regexpHash = regexpHash + hash(reQuantifier.quantifier);
            if (reQuantifier.nonGreedyChar != null) {
                return regexpHash + hash(reQuantifier.nonGreedyChar);
            }
        }
        return regexpHash;
    }

    private Integer generateHashForReCharacterClass(BLangReCharacterClass reCharacterClass) {
        int regexpHash = hash(reCharacterClass.characterClassStart) + hash(reCharacterClass.characterClassEnd);
        if (reCharacterClass.negation != null) {
            regexpHash = regexpHash + hash(reCharacterClass.negation);
        }
        if (reCharacterClass.charSet == null) {
            return regexpHash;
        }
        BLangReCharSet charSet = reCharacterClass.charSet;
        for (BLangExpression charSetAtom : charSet.charSetAtoms) {
            if (charSetAtom.getKind() == NodeKind.REG_EXP_CHAR_SET_RANGE) {
                BLangReCharSetRange charSetRange = ((BLangReCharSetRange) charSetAtom);
                regexpHash = regexpHash + hash(charSetRange.lhsCharSetAtom) +
                        hash(charSetRange.dash) + hash(charSetRange.rhsCharSetAtom);
            } else {
                regexpHash = regexpHash + hash(charSetAtom);
            }
        }
        return regexpHash;
    }

    private Integer generateHashForReCapturingGroup(BLangReCapturingGroups reCapturingGroup) {
        int regexpHash = hash(reCapturingGroup.openParen) +
                generateHashForRegExp(reCapturingGroup.disjunction.sequenceList) + hash(reCapturingGroup.closeParen);
        if (reCapturingGroup.flagExpr == null) {
            return regexpHash;
        }
        BLangReFlagExpression flagExpr = (BLangReFlagExpression) reCapturingGroup.flagExpr;
        regexpHash = regexpHash + hash(flagExpr.questionMark) + hash(flagExpr.colon);
        return regexpHash + hash(flagExpr.flagsOnOff.flags);
    }

    private Integer getTypeHash(BType type) {
        return Objects.hash(type.tag, type.name);
    }

    private boolean getTypeEquality(BType typeA, BType typeB) {
        return types.isAssignable(typeA, typeB) || types.isAssignable(typeB, typeA);
    }

    private List<BLangExpression> createKeyArray(BLangRecordLiteral literal, List<String> fieldNames) {
        //fieldNames have to be literals in table constructor's record literal list
        Map<String, BLangExpression> fieldMap = new HashMap<>();

        for (RecordLiteralNode.RecordField recordField : literal.fields) {
            if (recordField.isKeyValueField()) {
                BLangRecordLiteral.BLangRecordKeyValueField keyVal =
                        (BLangRecordLiteral.BLangRecordKeyValueField) recordField;
                fieldMap.put(keyVal.key.expr.toString(), keyVal.valueExpr);
            } else if (recordField.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                BLangRecordLiteral.BLangRecordVarNameField recordVarNameField =
                        (BLangRecordLiteral.BLangRecordVarNameField) recordField;
                fieldMap.put(recordVarNameField.getVariableName().value, recordVarNameField);
            }
        }
        return fieldNames.stream().map(fieldMap::get).toList();
    }

    private List<String> getFieldNames(BLangTableConstructorExpr constructorExpr) {
        List<String> fieldNames = null;
        if (Types.getImpliedType(constructorExpr.getBType()).tag == TypeTags.TABLE) {
            fieldNames = ((BTableType) Types.getImpliedType(constructorExpr.getBType())).fieldNameList;
            if (fieldNames != null) {
                return fieldNames;
            }
        }
        if (constructorExpr.tableKeySpecifier != null &&
                !constructorExpr.tableKeySpecifier.fieldNameIdentifierList.isEmpty()) {
            BLangTableKeySpecifier tableKeySpecifier = constructorExpr.tableKeySpecifier;
            return tableKeySpecifier.fieldNameIdentifierList.stream().map(identifier ->
                    ((BLangIdentifier) identifier).value).toList();
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public void visit(BLangRecordLiteral recordLiteral) {
        BType type = recordLiteral.getBType();
        if (type != null) {
            addDependency(this.currDependentSymbolDeque.peek(), Types.getImpliedType(type).tsymbol);
        }
        for (RecordLiteralNode.RecordField field : recordLiteral.fields) {
            if (field.isKeyValueField()) {
                BLangRecordLiteral.BLangRecordKeyValueField keyValuePair =
                        (BLangRecordLiteral.BLangRecordKeyValueField) field;
                if (keyValuePair.key.computedKey) {
                    analyzeNode(keyValuePair.key.expr, env);
                }
                analyzeNode(keyValuePair.valueExpr, env);
            } else if (field.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                analyzeNode((BLangRecordLiteral.BLangRecordVarNameField) field, env);
            } else {
                analyzeNode(((BLangRecordLiteral.BLangRecordSpreadOperatorField) field).expr, env);
            }
        }
    }

    @Override
    public void visit(BLangSimpleVarRef varRefExpr) {
        this.unusedErrorVarsDeclaredWithVar.remove(varRefExpr.symbol);

        if (isNotVariableReferenceLVExpr(varRefExpr)) {
            this.unusedLocalVariables.remove(varRefExpr.symbol);
        }
        BSymbol varRefSymbol = varRefExpr.symbol;
        if (varRefSymbol != null && (varRefSymbol.tag & SymTag.FUNCTION) != SymTag.FUNCTION) {
            checkVarRef(varRefSymbol, varRefExpr.pos);
        }
    }

    @Override
    public void visit(BLangFieldBasedAccess fieldAccessExpr) {
        if (!fieldAccessExpr.isLValue && isObjectMemberAccessWithSelf(fieldAccessExpr)) {
            checkVarRef(fieldAccessExpr.symbol, fieldAccessExpr.pos);
        }
        analyzeNode(fieldAccessExpr.expr, env);
    }

    @Override
    public void visit(BLangFieldBasedAccess.BLangPrefixedFieldBasedAccess prefixedFieldBasedAccess) {
        if (!prefixedFieldBasedAccess.isLValue && isObjectMemberAccessWithSelf(prefixedFieldBasedAccess)) {
            checkVarRef(prefixedFieldBasedAccess.symbol, prefixedFieldBasedAccess.pos);
        }
        analyzeNode(prefixedFieldBasedAccess.expr, env);
    }

    @Override
    public void visit(BLangIndexBasedAccess indexAccessExpr) {
        analyzeNode(indexAccessExpr.expr, env);
        analyzeNode(indexAccessExpr.indexExpr, env);
    }

    @Override
    public void visit(BLangXMLElementAccess xmlElementAccess) {
        analyzeNode(xmlElementAccess.expr, env);
    }

    @Override
    public void visit(BLangXMLNavigationAccess xmlNavigation) {
        analyzeNode(xmlNavigation.expr, env);
    }

    @Override
    public void visit(BLangExtendedXMLNavigationAccess extendedXmlNavigationAccess) {
        analyzeNode(extendedXmlNavigationAccess.stepExpr, env);
        extendedXmlNavigationAccess.extensions.forEach(extension -> analyzeNode(extension, env));
    }

    @Override
    public void visit(BLangXMLIndexedStepExtend xmlIndexedStepExtend) {
        analyzeNode(xmlIndexedStepExtend.indexExpr, env);
    }

    @Override
    public void visit(BLangXMLFilterStepExtend xmlFilterStepExtend) {
        /* ignore */
    }

    @Override
    public void visit(BLangXMLMethodCallStepExtend xmlMethodCallStepExtend) {
        analyzeNode(xmlMethodCallStepExtend.invocation, env);
    }

    @Override
    public void visit(BLangInvocation invocationExpr) {
        analyzeNode(invocationExpr.expr, env);

        BSymbol symbol = invocationExpr.symbol;
        this.unusedLocalVariables.remove(symbol);

        Set<BSymbol> dependents = invocationToDependent.computeIfAbsent(symbol, s -> new LinkedHashSet<>());
        dependents.add(this.currDependentSymbolDeque.peek());

        if (isFunctionOrMethodDefinedInCurrentModule(symbol.owner, env) &&
                !isGlobalVarsInitialized(invocationExpr.pos, invocationExpr)) {
            checkVarRef(symbol, invocationExpr.pos);
            return;
        }
        if (!isFieldsInitializedForSelfArgument(invocationExpr)) {
            return;
        }
        if (!isFieldsInitializedForSelfInvocation(invocationExpr.requiredArgs, invocationExpr.pos)) {
            return;
        }
        if (!isFieldsInitializedForSelfInvocation(invocationExpr.restArgs, invocationExpr.pos)) {
            return;
        }

        checkVarRef(symbol, invocationExpr.pos);

        invocationExpr.requiredArgs.forEach(expr -> analyzeNode(expr, env));
        invocationExpr.restArgs.forEach(expr -> analyzeNode(expr, env));
        BSymbol owner = this.env.scope.owner;
        if (owner.kind == SymbolKind.FUNCTION) {
            BInvokableSymbol invokableOwnerSymbol = (BInvokableSymbol) owner;
            // Todo: we need to handle function pointer referring global variable, passed into a function.
            // i.e 'foo' is a function pointer pointing to a function referring a global variable G1, then we pass
            // that pointer into 'bar', now 'bar' may have a dependency on G1.

            // Todo: test lambdas and function arguments

            if (symbol != symTable.notFoundSymbol) {
                addDependency(invokableOwnerSymbol, symbol);
            }
        } else if (symbol.kind == SymbolKind.FUNCTION) {
            BInvokableSymbol invokableProviderSymbol = (BInvokableSymbol) symbol;
            BSymbol curDependent = this.currDependentSymbolDeque.peek();
            if (isGlobalVarSymbol(curDependent)) {
                addDependency(curDependent, invokableProviderSymbol);
            }
        }
    }

    @Override
    public void visit(BLangCollectContextInvocation collectContextInvocation) {
        analyzeNode(collectContextInvocation.invocation, env);
    }

    @Override
    public void visit(BLangErrorConstructorExpr errorConstructorExpr) {
        for (BLangExpression positionalArg : errorConstructorExpr.positionalArgs) {
            analyzeNode(positionalArg, env);
        }
        for (BLangNamedArgsExpression namedArg : errorConstructorExpr.namedArgs) {
            analyzeNode(namedArg, env);
        }
        BType detailType = ((BErrorType) Types.getImpliedType(errorConstructorExpr.getBType())).detailType;
        addDependency(this.currDependentSymbolDeque.peek(), Types.getImpliedType(detailType).tsymbol);
    }

    @Override
    public void visit(BLangActionInvocation actionInvocation) {
        this.visit((BLangInvocation) actionInvocation);
    }

    @Override
    public void visit(BLangResourceAccessInvocation resourceAccessInvocation) {
        analyzeNode(resourceAccessInvocation.resourceAccessPathSegments, env);
        this.visit((BLangInvocation) resourceAccessInvocation);
    }

    @Override
    public void visit(BLangQueryExpr queryExpr) {
        for (BLangNode clause : queryExpr.getQueryClauses()) {
            analyzeNode(clause, env);
        }
    }

    @Override
    public void visit(BLangFromClause fromClause) {
        BLangExpression collection = fromClause.collection;

        if (isNotRangeExpr(collection)) {
            populateUnusedVariableMapForMembers(this.unusedLocalVariables,
                                                (BLangVariable) fromClause.variableDefinitionNode.getVariable());
        }

        analyzeNode(collection, env);
    }

    @Override
    public void visit(BLangJoinClause joinClause) {
        populateUnusedVariableMapForMembers(this.unusedLocalVariables,
                                            (BLangVariable) joinClause.variableDefinitionNode.getVariable());
        analyzeNode(joinClause.collection, env);
        if (joinClause.onClause != null) {
            analyzeNode(joinClause.onClause, env);
        }
    }

    @Override
    public void visit(BLangLetClause letClause) {
        for (BLangLetVariable letVariable : letClause.letVarDeclarations) {
            analyzeNode((BLangNode) letVariable.definitionNode, env);
        }
    }

    @Override
    public void visit(BLangWhereClause whereClause) {
        analyzeNode(whereClause.expression, env);
    }

    @Override
    public void visit(BLangOnClause onClause) {
        analyzeNode(onClause.lhsExpr, env);
        analyzeNode(onClause.rhsExpr, env);
    }

    @Override
    public void visit(BLangOrderKey orderKeyClause) {
        analyzeNode(orderKeyClause.expression, env);
    }

    @Override
    public void visit(BLangOrderByClause orderByClause) {
        orderByClause.orderByKeyList.forEach(value -> analyzeNode((BLangNode) value, env));
    }

    @Override
    public void visit(BLangGroupByClause groupByClause) {
        groupByClause.groupingKeyList.forEach(value -> analyzeNode(value, env));
        replaceWithSeqSymbol(groupByClause.env);
    }

    private void replaceWithSeqSymbol(SymbolEnv env) {
        for (Map.Entry<Name, Scope.ScopeEntry> symbolEntry : env.scope.entries.entrySet()) {
            BSymbol sym = null;
            Location loc = null;
            for (Map.Entry<BSymbol, Location> unusedLocalVariable : this.unusedLocalVariables.entrySet()) {
                BSymbol unusedVarSymbol = unusedLocalVariable.getKey();
                if (unusedVarSymbol.name.equals(symbolEntry.getKey())) {
                    sym = unusedLocalVariable.getKey();
                    loc = unusedLocalVariable.getValue();
                    break;
                }
            }
            if (sym != null) {
                this.unusedLocalVariables.remove(sym);
                this.unusedLocalVariables.put(symbolEntry.getValue().symbol, loc);
            }
        }
    }

    @Override
    public void visit(BLangGroupingKey groupingKey) {
        analyzeNode((BLangNode) groupingKey.getGroupingKey(), env);
    }

    @Override
    public void visit(BLangSelectClause selectClause) {
        analyzeNode(selectClause.expression, env);
    }

    @Override
    public void visit(BLangCollectClause bLangCollectClause) {
        replaceWithSeqSymbol(bLangCollectClause.env);
        analyzeNode(bLangCollectClause.expression, env);
    }

    @Override
    public void visit(BLangOnConflictClause onConflictClause) {
        analyzeNode(onConflictClause.expression, env);
    }

    @Override
    public void visit(BLangLimitClause limitClause) {
        analyzeNode(limitClause.expression, env);
    }

    @Override
    public void visit(BLangDoClause doClause) {
        analyzeNode(doClause.body, env);
    }

    @Override
    public void visit(BLangOnFailClause onFailClause) {
        VariableDefinitionNode onFailVarDefNode = onFailClause.variableDefinitionNode;
        if (onFailVarDefNode != null) {
            analyzeNode((BLangVariable) onFailVarDefNode.getVariable(), env);
        }
        analyzeNode(onFailClause.body, env);
    }

    private boolean isFieldsInitializedForSelfArgument(BLangInvocation invocationExpr) {

        if (invocationExpr.expr == null || !isSelfKeyWordExpr(invocationExpr.expr)) {
            return true;
        }
        StringBuilder uninitializedFields =
                getUninitializedFieldsForSelfKeyword((BObjectType) ((BLangSimpleVarRef)
                        invocationExpr.expr).symbol.type);
        if (!uninitializedFields.isEmpty()) {
            this.dlog.error(invocationExpr.pos, DiagnosticErrorCode.CONTAINS_UNINITIALIZED_FIELDS,
                    uninitializedFields.toString());
            return false;
        }
        return true;
    }

    private boolean isFieldsInitializedForSelfInvocation(List<BLangExpression> argExpressions,
                                                         Location location) {

        for (BLangExpression expr : argExpressions) {
            if (isSelfKeyWordExpr(expr)) {
                StringBuilder uninitializedFields =
                        getUninitializedFieldsForSelfKeyword((BObjectType) ((BLangSimpleVarRef) expr).symbol.type);
                if (!uninitializedFields.isEmpty()) {
                    this.dlog.error(location, DiagnosticErrorCode.CONTAINS_UNINITIALIZED_FIELDS,
                            uninitializedFields.toString());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isGlobalVarsInitialized(Location pos, BLangInvocation invocation) {
        if (env.isModuleInit) {
            boolean isFirstUninitializedField = true;
            StringBuilder uninitializedFields = new StringBuilder();

            BLangExpression expr = invocation.expr;
            boolean methodCallOnVarRef = expr != null && expr.getKind() == NodeKind.SIMPLE_VARIABLE_REF;

            for (BSymbol symbol : this.uninitializedVars.keySet()) {
                if (symbol.owner.getKind() != SymbolKind.PACKAGE || symbol == invocation.symbol ||
                        (methodCallOnVarRef && ((BLangSimpleVarRef) expr).symbol == symbol)) {
                    continue;
                }

                if (isFirstUninitializedField) {
                    uninitializedFields = new StringBuilder(symbol.getName().value);
                    isFirstUninitializedField = false;
                } else {
                    uninitializedFields.append(", ").append(symbol.getName().value);
                }
            }
            if (!uninitializedFields.isEmpty()) {
                this.dlog.error(pos, DiagnosticErrorCode.INVALID_FUNCTION_CALL_WITH_UNINITIALIZED_VARIABLES,
                        uninitializedFields.toString());
                return false;
            }
        }
        return true;
    }

    private boolean isSelfKeyWordExpr(BLangExpression expr) {

        return expr.getKind() == NodeKind.SIMPLE_VARIABLE_REF &&
                Names.SELF.value.equals(((BLangSimpleVarRef) expr).getVariableName().getValue());
    }

    private StringBuilder getUninitializedFieldsForSelfKeyword(BObjectType objType) {

        boolean isFirstUninitializedField = true;
        StringBuilder uninitializedFields = new StringBuilder();
        for (BField field : objType.fields.values()) {
            if (this.uninitializedVars.containsKey(field.symbol)) {
                if (isFirstUninitializedField) {
                    uninitializedFields = new StringBuilder(field.symbol.getName().value);
                    isFirstUninitializedField = false;
                } else {
                    uninitializedFields.append(", ").append(field.symbol.getName().value);
                }
            }
        }
        return uninitializedFields;
    }

    private boolean isGlobalVarSymbol(BSymbol symbol) {
        if (symbol == null) {
            return false;
        } else if (symbol.owner == null) {
            return false;
        } else if (symbol.owner.tag != SymTag.PACKAGE) {
            return false;
        }

        return isVariableOrConstant(symbol);
    }

    private boolean isVariableOrConstant(BSymbol symbol) {
        if (symbol == null) {
            return false;
        }
        return ((symbol.tag & SymTag.VARIABLE) == SymTag.VARIABLE) ||
                ((symbol.tag & SymTag.CONSTANT) == SymTag.CONSTANT);
    }

    /**
     * Register dependent symbol to the provider symbol.
     * Let global int a = b, a depend on b.
     * Let func foo() { returns b + 1; }, where b is a global var, then foo depends on b.
     *
     * @param dependent dependent.
     * @param provider object which provides a value.
     */
    private void addDependency(BSymbol dependent, BSymbol provider) {
        if (provider == null || dependent == null || dependent.pkgID != provider.pkgID) {
            return;
        }
        Set<BSymbol> providers = globalNodeDependsOn.computeIfAbsent(dependent, s -> new LinkedHashSet<>());
        providers.add(provider);

        // Store the dependencies of functions separately for lock optimization in later stage.
        addFunctionToGlobalVarDependency(dependent, provider);
    }

    private void addFunctionToGlobalVarDependency(BSymbol dependent, BSymbol provider) {
        if (dependent.kind != SymbolKind.FUNCTION && !isGlobalVarSymbol(dependent)) {
            return;
        }
        if (isVariableOrConstant(provider) && !isGlobalVarSymbol(provider)) {
            return;
        }

        Set<BSymbol> providers = this.functionToDependency.computeIfAbsent(dependent, s -> new HashSet<>());
        providers.add(provider);
    }

    @Override
    public void visit(BLangTypeInit typeInitExpr) {
        typeInitExpr.argsExpr.forEach(argExpr -> analyzeNode(argExpr, env));
        if (this.currDependentSymbolDeque.peek() != null) {
            addDependency(this.currDependentSymbolDeque.peek(),
                    Types.getImpliedType(typeInitExpr.getBType()).tsymbol);
        }
    }

    @Override
    public void visit(BLangTernaryExpr ternaryExpr) {
        analyzeNode(ternaryExpr.expr, env);
        analyzeNode(ternaryExpr.thenExpr, env);
        analyzeNode(ternaryExpr.elseExpr, env);
    }

    @Override
    public void visit(BLangWaitExpr waitExpr) {
        analyzeNode(waitExpr.getExpression(), env);
    }

    @Override
    public void visit(BLangWorkerFlushExpr workerFlushExpr) {
        // todo
    }

    @Override
    public void visit(BLangWaitForAllExpr waitForAllExpr) {
        waitForAllExpr.keyValuePairs.forEach(keyValue -> {
            BLangExpression expr = keyValue.valueExpr != null ? keyValue.valueExpr : keyValue.keyExpr;
            analyzeNode(expr, env);
        });
    }

    @Override
    public void visit(BLangBinaryExpr binaryExpr) {
        analyzeNode(binaryExpr.lhsExpr, env);
        analyzeNode(binaryExpr.rhsExpr, env);
    }

    @Override
    public void visit(BLangElvisExpr elvisExpr) {
        analyzeNode(elvisExpr.lhsExpr, env);
        analyzeNode(elvisExpr.rhsExpr, env);
    }

    @Override
    public void visit(BLangGroupExpr groupExpr) {
        analyzeNode(groupExpr.expression, env);
    }

    @Override
    public void visit(BLangUnaryExpr unaryExpr) {
        analyzeNode(unaryExpr.expr, env);
    }

    @Override
    public void visit(BLangTypeConversionExpr conversionExpr) {
        analyzeNode(conversionExpr.expr, env);
    }

    @Override
    public void visit(BLangXMLAttribute xmlAttribute) {
        analyzeNode(xmlAttribute.value, env);
    }

    @Override
    public void visit(BLangXMLElementLiteral xmlElementLiteral) {
        xmlElementLiteral.children.forEach(expr -> analyzeNode(expr, env));
        xmlElementLiteral.attributes.forEach(expr -> analyzeNode(expr, env));
        xmlElementLiteral.inlineNamespaces.forEach(expr -> analyzeNode(expr, env));
    }

    @Override
    public void visit(BLangXMLTextLiteral xmlTextLiteral) {
        xmlTextLiteral.textFragments.forEach(expr -> analyzeNode(expr, env));
    }

    @Override
    public void visit(BLangXMLCommentLiteral xmlCommentLiteral) {
        xmlCommentLiteral.textFragments.forEach(expr -> analyzeNode(expr, env));
    }

    @Override
    public void visit(BLangXMLProcInsLiteral xmlProcInsLiteral) {
        xmlProcInsLiteral.dataFragments.forEach(expr -> analyzeNode(expr, env));
    }

    @Override
    public void visit(BLangXMLQuotedString xmlQuotedString) {
        xmlQuotedString.textFragments.forEach(expr -> analyzeNode(expr, env));
    }

    @Override
    public void visit(BLangStringTemplateLiteral stringTemplateLiteral) {
        stringTemplateLiteral.exprs.forEach(expr -> analyzeNode(expr, env));
    }

    @Override
    public void visit(BLangRawTemplateLiteral rawTemplateLiteral) {
        for (BLangLiteral string : rawTemplateLiteral.strings) {
            analyzeNode(string, env);
        }

        for (BLangExpression expr : rawTemplateLiteral.insertions) {
            analyzeNode(expr, env);
        }
    }

    @Override
    public void visit(BLangLambdaFunction bLangLambdaFunction) {
        BLangFunction funcNode = bLangLambdaFunction.function;
        SymbolEnv funcEnv = SymbolEnv.createFunctionEnv(funcNode, funcNode.symbol.scope, env);
        funcNode.requiredParams.forEach(param -> analyzeNode(param, funcEnv));
        analyzeNode(funcNode.restParam, funcEnv);
        analyzeNode(funcNode.returnTypeNode, env);
        visitFunctionBodyWithDynamicEnv(funcNode, funcEnv);
        if (isGlobalVarSymbol(funcNode.symbol)) {
            dependsOnLambda.put(this.currDependentSymbolDeque.peek(), funcNode.symbol);
        }
    }

    @Override
    public void visit(BLangRestArgsExpression bLangVarArgsExpression) {
        analyzeNode(bLangVarArgsExpression.expr, env);
    }

    @Override
    public void visit(BLangNamedArgsExpression bLangNamedArgsExpression) {
        analyzeNode(bLangNamedArgsExpression.expr, env);
    }

    @Override
    public void visit(BLangIsAssignableExpr assignableExpr) {
    }

    @Override
    public void visit(BLangCheckedExpr checkedExpr) {
        if (isOnFailEnclosed()) {
            this.possibleFailureReached = true;
        }
        analyzeNode(checkedExpr.expr, env);
    }

    @Override
    public void visit(BLangCheckPanickedExpr checkPanicExpr) {
        analyzeNode(checkPanicExpr.expr, env);
    }

    @Override
    public void visit(BLangXMLSequenceLiteral bLangXMLSequenceLiteral) {
        bLangXMLSequenceLiteral.xmlItems.forEach(xml -> analyzeNode(xml, env));
    }

    @Override
    public void visit(BLangExpressionStmt exprStmtNode) {
        analyzeNode(exprStmtNode.expr, env);
    }

    @Override
    public void visit(BLangAnnotation annotationNode) {
    }

    @Override
    public void visit(BLangAnnotationAttachment annAttachmentNode) {
    }

    @Override
    public void visit(BLangRetry retryNode) {
        analyzeStmtWithOnFail(retryNode.retryBody, retryNode.onFailClause);
    }

    @Override
    public void visit(BLangRetryTransaction retryTransaction) {
        analyzeNode(retryTransaction.transaction, env);
    }

    @Override
    public void visit(BLangContinue continueNode) {
        terminateFlow();
    }

    @Override
    public void visit(BLangTypedescExpr accessExpr) {
    }

    @Override
    public void visit(BLangXMLQName xmlQName) {
    }

    @Override
    public void visit(BLangArrowFunction bLangArrowFunction) {
        for (ClosureVarSymbol closureVarSymbol : bLangArrowFunction.closureVarSymbols) {
            BSymbol symbol = closureVarSymbol.bSymbol;
            if (this.uninitializedVars.containsKey(symbol)) {
                this.dlog.error(closureVarSymbol.diagnosticLocation,
                                DiagnosticErrorCode.USAGE_OF_UNINITIALIZED_VARIABLE, symbol);
            }

            this.unusedErrorVarsDeclaredWithVar.remove(symbol);
            this.unusedLocalVariables.remove(symbol);
        }
    }

    @Override
    public void visit(BLangValueType valueType) {
    }

    @Override
    public void visit(BLangConstant constant) {
        boolean validVariable = constant.symbol != null;
        if (validVariable) {
            this.currDependentSymbolDeque.push(constant.symbol);
        }
        try {
            analyzeNode(constant.expr, env);
        } finally {
            if (validVariable) {
                this.currDependentSymbolDeque.pop();
            }
        }
    }

    @Override
    public void visit(BLangArrayType arrayType) {
        analyzeNode(arrayType.getElementType(), env);
    }

    @Override
    public void visit(BLangBuiltInRefTypeNode builtInRefType) {
    }

    @Override
    public void visit(BLangConstrainedType constrainedType) {
        analyzeNode(constrainedType.constraint, env);
    }

    @Override
    public void visit(BLangStreamType streamType) {
        analyzeNode(streamType.constraint, env);
        analyzeNode(streamType.error, env);
    }

    @Override
    public void visit(BLangTableTypeNode tableType) {
        analyzeNode(tableType.constraint, env);

        if (tableType.tableKeyTypeConstraint != null) {
            analyzeNode(tableType.tableKeyTypeConstraint.keyType, env);
        }
    }

    @Override
    public void visit(BLangUserDefinedType userDefinedType) {
        if (this.currDependentSymbolDeque.isEmpty()) {
            return;
        }
        BType resolvedType = userDefinedType.getBType();
        if (resolvedType == symTable.semanticError) {
            return;
        }
        BTypeSymbol tsymbol = Types.getImpliedType(resolvedType).tsymbol;
        recordGlobalVariableReferenceRelationship(tsymbol);
    }

    @Override
    public void visit(BLangFunctionTypeNode functionTypeNode) {
        if (functionTypeNode.flagSet.contains(Flag.ANY_FUNCTION)) {
            return;
        }
        functionTypeNode.params.forEach(param -> analyzeNode(param.typeNode, env));
        analyzeNode(functionTypeNode.returnTypeNode, env);
    }

    @Override
    public void visit(BLangUnionTypeNode unionTypeNode) {
        unionTypeNode.memberTypeNodes.forEach(typeNode -> analyzeNode(typeNode, env));
    }

    @Override
    public void visit(BLangIntersectionTypeNode intersectionTypeNode) {
        for (BLangType constituentTypeNode : intersectionTypeNode.constituentTypeNodes) {
            analyzeNode(constituentTypeNode, env);
        }
    }

    @Override
    public void visit(BLangObjectTypeNode objectTypeNode) {
    }

    @Override
    public void visit(BLangRecordTypeNode recordTypeNode) {
        BTypeSymbol tsymbol = Types.getImpliedType(recordTypeNode.getBType()).tsymbol;
        for (TypeNode type : recordTypeNode.getTypeReferences()) {
            BLangType bLangType = (BLangType) type;
            analyzeNode(bLangType, env);
            recordGlobalVariableReferenceRelationship(
                    Types.getImpliedType(bLangType.getBType()).tsymbol);
        }
        for (BLangSimpleVariable field : recordTypeNode.fields) {
            addTypeDependency(tsymbol, Types.getImpliedType(field.getBType()), new HashSet<>());
            analyzeNode(field, env);
            for (BLangAnnotationAttachment annotationAttachment : field.annAttachments) {
                analyzeNode(annotationAttachment.expr, env);
            }
            recordGlobalVariableReferenceRelationship(field.symbol);
        }
    }

    private void addTypeDependency(BTypeSymbol dependentTypeSymbol, BType providerType, Set<BType> unresolvedTypes) {
        providerType = Types.getImpliedType(providerType);
        if (unresolvedTypes.contains(providerType)) {
            return;
        }
        unresolvedTypes.add(providerType);
        switch (providerType.tag) {
            case TypeTags.UNION:
                for (BType memberType : ((BUnionType) providerType).getMemberTypes()) {
                    BType effectiveType = types.getTypeWithEffectiveIntersectionTypes(memberType);
                    addTypeDependency(dependentTypeSymbol, effectiveType, unresolvedTypes);
                }
                break;
            case TypeTags.ARRAY:
                addTypeDependency(dependentTypeSymbol,
                        types.getTypeWithEffectiveIntersectionTypes(((BArrayType) providerType).getElementType()),
                        unresolvedTypes);
                break;
            case TypeTags.MAP:
                addTypeDependency(dependentTypeSymbol,
                        types.getTypeWithEffectiveIntersectionTypes(((BMapType) providerType).getConstraint()),
                        unresolvedTypes);
                break;
            default:
                addDependency(dependentTypeSymbol, providerType.tsymbol);
        }
    }

    @Override
    public void visit(BLangFiniteTypeNode finiteTypeNode) {
        finiteTypeNode.valueSpace.forEach(value -> analyzeNode(value, env));
    }

    @Override
    public void visit(BLangTupleTypeNode tupleTypeNode) {
        for (BLangSimpleVariable member : tupleTypeNode.members) {
            analyzeNode(member, env);
            for (BLangAnnotationAttachment annotationAttachment : member.annAttachments) {
                analyzeNode(annotationAttachment.expr, env);
            }
        }
    }

    @Override
    public void visit(BLangMarkdownDocumentationLine bLangMarkdownDocumentationLine) {
    }

    @Override
    public void visit(BLangMarkdownParameterDocumentation bLangDocumentationParameter) {
    }

    @Override
    public void visit(BLangMarkdownReturnParameterDocumentation bLangMarkdownReturnParameterDocumentation) {
    }

    @Override
    public void visit(BLangMarkdownDocumentation bLangMarkdownDocumentation) {
    }

    @Override
    public void visit(BLangTestablePackage testablePkgNode) {
    }

    @Override
    public void visit(BLangImportPackage importPkgNode) {
    }

    @Override
    public void visit(BLangIdentifier identifierNode) {
    }

    @Override
    public void visit(BLangPanic panicNode) {
        analyzeNode(panicNode.expr, env);

        // panic statement will terminate the flow. There will be no uninitialized
        // variables left after the panic statement.
        terminateFlow();
    }

    @Override
    public void visit(BLangTrapExpr trapExpr) {
        analyzeNode(trapExpr.expr, env);
    }

    @Override
    public void visit(BLangServiceConstructorExpr serviceConstructorExpr) {
        if (this.currDependentSymbolDeque.peek() != null) {
            addDependency(this.currDependentSymbolDeque.peek(),
                    Types.getImpliedType(serviceConstructorExpr.getBType()).tsymbol);
        }

        addDependency(Types.getImpliedType(serviceConstructorExpr.getBType()).tsymbol,
                serviceConstructorExpr.serviceNode.symbol);
        analyzeNode(serviceConstructorExpr.serviceNode, env);
    }

    @Override
    public void visit(BLangTypeTestExpr typeTestExpr) {
        analyzeNode(typeTestExpr.expr, env);
        analyzeNode(typeTestExpr.typeNode, env);
    }

    @Override
    public void visit(BLangAnnotAccessExpr annotAccessExpr) {
        analyzeNode(annotAccessExpr.expr, env);
    }

    @Override
    public void visit(BLangInferredTypedescDefaultNode inferTypedescExpr) {
    }

    @Override
    public void visit(BLangErrorType errorType) {
        BLangType detailType = errorType.detailType;
        if (detailType != null && detailType.getBType() != null) {
            addDependency(this.currDependentSymbolDeque.peek(), Types.getImpliedType(detailType.getBType()).tsymbol);
        }
    }

    @Override
    public void visit(BLangRecordDestructure recordDestructure) {
        analyzeNode(recordDestructure.expr, env);
        checkAssignment(recordDestructure.varRef);
    }

    @Override
    public void visit(BLangErrorDestructure errorDestructure) {
        analyzeNode(errorDestructure.expr, env);
        checkAssignment(errorDestructure.varRef);
    }

    @Override
    public void visit(BLangTupleVarRef tupleVarRefExpr) {
        tupleVarRefExpr.expressions.forEach(expr -> analyzeNode(expr, env));
    }

    @Override
    public void visit(BLangRecordVarRef varRefExpr) {
        varRefExpr.recordRefFields.forEach(expr -> analyzeNode(expr.variableReference, env));
    }

    @Override
    public void visit(BLangErrorVarRef varRefExpr) {
        analyzeNode(varRefExpr.message, env);
        if (varRefExpr.cause != null) {
            analyzeNode(varRefExpr.cause, env);
        }
        for (BLangNamedArgsExpression args : varRefExpr.detail) {
            analyzeNode(args.expr, env);
        }
        analyzeNode(varRefExpr.restVar, env);
    }

    @Override
    public void visit(BLangTupleVariable bLangTupleVariable) {
        analyzeNode(bLangTupleVariable.typeNode, env);
        populateUnusedVariableMapForNonSimpleBindingPatternVariables(this.unusedLocalVariables, bLangTupleVariable);
        this.currDependentSymbolDeque.push(bLangTupleVariable.symbol);
        for (BLangVariable memberVariable : bLangTupleVariable.memberVariables) {
            symbolOwner.put(memberVariable.symbol, bLangTupleVariable.symbol);
        }
        analyzeNode(bLangTupleVariable.expr, env);
        this.currDependentSymbolDeque.pop();
    }

    @Override
    public void visit(BLangTupleVariableDef bLangTupleVariableDef) {
        analyzeNode(bLangTupleVariableDef.var, env);
    }

    @Override
    public void visit(BLangRecordVariable bLangRecordVariable) {
        analyzeNode(bLangRecordVariable.typeNode, env);
        populateUnusedVariableMapForNonSimpleBindingPatternVariables(this.unusedLocalVariables, bLangRecordVariable);
        this.currDependentSymbolDeque.push(bLangRecordVariable.symbol);
        for (BLangRecordVariable.BLangRecordVariableKeyValue recordVariableKeyValue :
                                                                                bLangRecordVariable.variableList) {
            symbolOwner.put(recordVariableKeyValue.valueBindingPattern.symbol, bLangRecordVariable.symbol);
        }
        analyzeNode(bLangRecordVariable.expr, env);
        this.currDependentSymbolDeque.pop();
    }

    @Override
    public void visit(BLangRecordVariableDef bLangRecordVariableDef) {
        analyzeNode(bLangRecordVariableDef.var, env);
    }

    @Override
    public void visit(BLangErrorVariable bLangErrorVariable) {
        analyzeNode(bLangErrorVariable.typeNode, env);
        populateUnusedVariableMapForNonSimpleBindingPatternVariables(this.unusedLocalVariables, bLangErrorVariable);
        this.currDependentSymbolDeque.push(bLangErrorVariable.symbol);
        BSymbol symbol = bLangErrorVariable.symbol;
        if (bLangErrorVariable.message != null) {
            symbolOwner.put(bLangErrorVariable.message.symbol, symbol);
        }
        if (bLangErrorVariable.cause != null) {
            symbolOwner.put(bLangErrorVariable.cause.symbol, symbol);
        }
        if (bLangErrorVariable.restDetail != null) {
            symbolOwner.put(bLangErrorVariable.restDetail.symbol, symbol);
        }

        for (BLangErrorVariable.BLangErrorDetailEntry errorDetailEntry : bLangErrorVariable.detail) {
            symbolOwner.put(errorDetailEntry.valueBindingPattern.symbol, bLangErrorVariable.symbol);
        }

        analyzeNode(bLangErrorVariable.expr, env);
        this.currDependentSymbolDeque.pop();
    }

    @Override
    public void visit(BLangErrorVariableDef bLangErrorVariableDef) {
        analyzeNode(bLangErrorVariableDef.errorVariable, env);
    }

    @Override
    public void visit(BLangNaturalExpression naturalExpression) {
        for (BLangExpression argumentExpr : naturalExpression.arguments) {
            analyzeNode(argumentExpr, env);
        }

        for (BLangLiteral string : naturalExpression.strings) {
            analyzeNode(string, env);
        }

        for (BLangExpression expr : naturalExpression.insertions) {
            analyzeNode(expr, env);
        }
    }

    private void addUninitializedVar(BLangVariable variable) {
        if (!this.uninitializedVars.containsKey(variable.symbol)) {
            this.uninitializedVars.put(variable.symbol, InitStatus.UN_INIT);
        }
    }

    @Override
    public void visit(BLangRegExpTemplateLiteral regExpTemplateLiteral) {
        List<BLangExpression> interpolationsList =
                symResolver.getListOfInterpolations(regExpTemplateLiteral.reDisjunction.sequenceList);
        interpolationsList.forEach(interpolation -> analyzeNode(interpolation, env));
    }

    /**
     * Analyze a branch and returns the set of uninitialized variables for that branch.
     * This method will not update the current uninitialized variables set.
     *
     * @param node Branch node to be analyzed
     * @param env Symbol environment
     * @return Result of the branch.
     */
    private BranchResult analyzeBranch(BLangNode node, SymbolEnv env) {
        Map<BSymbol, InitStatus> prevUninitializedVars = this.uninitializedVars;
        Map<BSymbol, InitStatus> prevOnFailUninitializedVars = getPossibleFailureUnInitVars();
        if (node != null && isOnFailEnclosed()) {
            BLangOnFailClause onFailClause = this.enclosingOnFailClause.peek();
            prevOnFailUninitializedVars = this.possibleFailureUnInitVars.get(onFailClause);
            this.possibleFailureUnInitVars.put(onFailClause, copyOnFailUninitializedVars(onFailClause));
        }
        boolean prevFlowTerminated = this.flowTerminated;
        boolean prevFailureReached = this.possibleFailureReached;
        boolean prevDefiniteFailureReached = this.definiteFailureReached;

        // Get a snapshot of the current uninitialized vars before visiting the node.
        // This is done so that the original set of uninitialized vars will not be
        // updated/marked as initialized.
        this.uninitializedVars = copyUninitializedVars();
        this.flowTerminated = false;
        this.possibleFailureReached = false;
        this.definiteFailureReached = false;

        analyzeNode(node, env);
        BranchResult branchResult = new BranchResult(this.uninitializedVars, getPossibleFailureUnInitVars(),
                this.flowTerminated, this.possibleFailureReached, this.definiteFailureReached);

        // Restore the original set of uninitialized vars
        this.uninitializedVars = prevUninitializedVars;
        this.flowTerminated = prevFlowTerminated;
        this.possibleFailureReached = prevFailureReached;
        this.definiteFailureReached = prevDefiniteFailureReached;
        updateUnInitVarsForOnFailClause(prevOnFailUninitializedVars);
        return branchResult;
    }

    private Map<BSymbol, InitStatus> copyUninitializedVars() {
        return new LinkedHashMap<>(this.uninitializedVars);
    }

    private Map<BSymbol, InitStatus> copyOnFailUninitializedVars(BLangOnFailClause onFailClause) {
        return new LinkedHashMap<>(this.possibleFailureUnInitVars.get(onFailClause));
    }

    private Map<BSymbol, InitStatus> getPossibleFailureUnInitVars() {
        if (isOnFailEnclosed()) {
            return this.possibleFailureUnInitVars.get(this.enclosingOnFailClause.peek());
        }
        return null;
    }

    private void analyzeNode(BLangNode node, SymbolEnv env) {
        SymbolEnv prevEnv = this.env;
        this.env = env;
        if (node != null) {
            node.accept(this);
        }
        this.env = prevEnv;
    }

    private Map<BSymbol, InitStatus> mergeUninitializedVars(Map<BSymbol, InitStatus> firstUninitVars,
                                                            Map<BSymbol, InitStatus> secondUninitVars) {
        List<BSymbol> intersection = new ArrayList<>(firstUninitVars.keySet());
        intersection.retainAll(secondUninitVars.keySet());

        return Stream.concat(firstUninitVars.entrySet().stream(), secondUninitVars.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        // If only one branch have uninitialized the var, then it's a partial initialization
                        entry -> intersection.contains(entry.getKey()) ? entry.getValue() : InitStatus.PARTIAL_INIT,
                        (a, b) -> {
                            // If atleast one of the branches have partially initialized the var,
                            // then merged result is also a partially initialized var
                            if (a == InitStatus.PARTIAL_INIT || b == InitStatus.PARTIAL_INIT) {
                                return InitStatus.PARTIAL_INIT;
                            }

                            return InitStatus.UN_INIT;
                        }, LinkedHashMap::new));
    }

    private void checkVarRef(BSymbol symbol, Location pos) {
        recordGlobalVariableReferenceRelationship(symbol);

        InitStatus initStatus = this.uninitializedVars.get(symbol);
        if (initStatus == null) {
            return;
        }

        if (initStatus == InitStatus.UN_INIT) {
            this.dlog.error(pos, DiagnosticErrorCode.USAGE_OF_UNINITIALIZED_VARIABLE, symbol);
            return;
        }

        this.dlog.error(pos, DiagnosticErrorCode.PARTIALLY_INITIALIZED_VARIABLE, symbol);
    }

    private void recordGlobalVariableReferenceRelationship(BSymbol symbol) {
        if (this.env.scope == null || symbol == null) {
            return;
        }

        boolean globalVarSymbol = isGlobalVarSymbol(symbol);
        BSymbol ownerSymbol = this.env.scope.owner;
        switch (ownerSymbol.getKind()) {
            case FUNCTION :
                // Global variable ref from non package level.
            case OBJECT:
                // Global variable reference from a field assignment of an object or a service.
                // Or global variable reference from an init function of an object or a service.
            case RECORD:
                // Global variable reference from a field assignment of a record type declaration.
                if (globalVarSymbol) {
                    addDependency(ownerSymbol, symbol);
                    break;
                }
                // fall through
            default:
                if (symbol.kind == SymbolKind.TYPE_DEF || globalVarSymbol || symbol instanceof BTypeSymbol ||
                        ownerSymbol.tag == SymTag.LET) {
                    BSymbol dependent = this.currDependentSymbolDeque.peek();
                    addDependency(dependent, symbol.kind == SymbolKind.TYPE_DEF
                            ? Types.getImpliedType(symbol.type).tsymbol : symbol);
                }
        }
    }

    private boolean isObjectMemberAccessWithSelf(BLangAccessExpression fieldAccessExpr) {
        if (fieldAccessExpr.expr.getKind() != NodeKind.SIMPLE_VARIABLE_REF) {
            return false;
        }
        return Names.SELF.value.equals(((BLangSimpleVarRef) fieldAccessExpr.expr).variableName.value);
    }

    private void checkAssignment(BLangExpression varRef) {
        NodeKind kind = varRef.getKind();
        switch (kind) {
            case RECORD_VARIABLE_REF:
                BLangRecordVarRef recordVarRef = (BLangRecordVarRef) varRef;
                recordVarRef.recordRefFields.forEach(field -> checkAssignment(field.variableReference));
                if (recordVarRef.restParam != null) {
                    checkAssignment(recordVarRef.restParam);
                }
                return;
            case TUPLE_VARIABLE_REF:
                BLangTupleVarRef tupleVarRef = (BLangTupleVarRef) varRef;
                tupleVarRef.expressions.forEach(this::checkAssignment);
                if (tupleVarRef.restParam != null) {
                    checkAssignment(tupleVarRef.restParam);
                }
                return;
            case ERROR_VARIABLE_REF:
                BLangErrorVarRef errorVarRef = (BLangErrorVarRef) varRef;
                if (errorVarRef.message != null) {
                    checkAssignment(errorVarRef.message);
                }
                if (errorVarRef.cause != null) {
                    checkAssignment(errorVarRef.cause);
                }
                for (BLangNamedArgsExpression expression : errorVarRef.detail) {
                    checkAssignment(expression);
                    this.uninitializedVars.remove(((BLangVariableReference) expression.expr).symbol);
                }
                if (errorVarRef.restVar != null) {
                    checkAssignment(errorVarRef.restVar);
                }
                return;
            case INDEX_BASED_ACCESS_EXPR:
            case FIELD_BASED_ACCESS_EXPR:
                BLangAccessExpression accessExpr = (BLangAccessExpression) varRef;

                BLangExpression expr = accessExpr.expr;
                BType type = Types.getImpliedType(expr.getBType());
                if (isObjectMemberAccessWithSelf(accessExpr)) {
                    BObjectType objectType = (BObjectType) type;

                    BSymbol symbol = accessExpr.symbol;
                    if (this.uninitializedVars.containsKey(symbol)) {
                        this.uninitializedVars.remove(symbol);
                        return;
                    }

                    String fieldName = ((BLangFieldBasedAccess) varRef).field.value;
                    checkFinalEntityUpdate(varRef.pos, fieldName, objectType.fields.get(fieldName).symbol);
                    return;
                }

                if (accessExpr.getKind() == NodeKind.FIELD_BASED_ACCESS_EXPR) {
                    checkFinalObjectFieldUpdate((BLangFieldBasedAccess) accessExpr);
                }

                analyzeNode(expr, env);

                if (kind == NodeKind.INDEX_BASED_ACCESS_EXPR) {
                    analyzeNode(((BLangIndexBasedAccess) varRef).indexExpr, env);
                }

                return;
            default:
                break;
        }

        if (kind != NodeKind.SIMPLE_VARIABLE_REF && kind != NodeKind.XML_ATTRIBUTE_ACCESS_EXPR) {
            return;
        }

        // So global variable assignments happen in functions.
        if (kind == NodeKind.SIMPLE_VARIABLE_REF) {
            BSymbol symbol = ((BLangSimpleVarRef) varRef).symbol;
            checkFinalEntityUpdate(varRef.pos, varRef, symbol);

            BSymbol owner = this.currDependentSymbolDeque.peek();
            addFunctionToGlobalVarDependency(owner, ((BLangSimpleVarRef) varRef).symbol);
        }

        BSymbol symbol = ((BLangVariableReference) varRef).symbol;
        if (this.possibleFailureReached && this.uninitializedVars.containsKey(symbol)) {
            getPossibleFailureUnInitVars().put(symbol, InitStatus.PARTIAL_INIT);
        } else if (!this.possibleFailureUnInitVars.isEmpty() && !this.possibleFailureReached) {
            getPossibleFailureUnInitVars().remove(symbol);
        }
        this.uninitializedVars.remove(symbol);
    }

    private void checkFinalObjectFieldUpdate(BLangFieldBasedAccess fieldAccess) {
        BLangExpression expr = fieldAccess.expr;

        BType exprType = Types.getImpliedType(expr.getBType());

        if (types.isSubTypeOfBaseType(exprType, PredefinedType.OBJECT) &&
                isFinalFieldInAllObjects(fieldAccess.pos, exprType, fieldAccess.field.value)) {
            dlog.error(fieldAccess.pos, DiagnosticErrorCode.CANNOT_UPDATE_FINAL_OBJECT_FIELD,
                    fieldAccess.symbol.originalName);
        }
    }

    private boolean isFinalFieldInAllObjects(Location pos, BType btype, String fieldName) {
        BType type = Types.getImpliedType(btype);
        if (type.tag == TypeTags.OBJECT) {

            BField field = ((BObjectType) type).fields.get(fieldName);
            if (field != null) {
                return Symbols.isFlagOn(field.symbol.flags, Flags.FINAL);
            }

            BObjectTypeSymbol objTypeSymbol = (BObjectTypeSymbol) type.tsymbol;
            Name funcName = Names.fromString(Symbols.getAttachedFuncSymbolName(objTypeSymbol.name.value, fieldName));
            BSymbol funcSymbol = symResolver.resolveObjectMethod(pos, env, funcName, objTypeSymbol);

            // Object member functions are inherently final
            return funcSymbol != null;
        }

        for (BType memberType : ((BUnionType) type).getMemberTypes()) {
            if (!isFinalFieldInAllObjects(pos, memberType, fieldName)) {
                return false;
            }
        }
        return true;
    }

    private void checkFinalEntityUpdate(Location pos, Object field, BSymbol symbol) {
        if (symbol == null || !Symbols.isFlagOn(symbol.flags, Flags.FINAL)) {
            return;
        }

        if (!this.uninitializedVars.containsKey(symbol)) {
            dlog.error(pos, DiagnosticErrorCode.CANNOT_ASSIGN_VALUE_FINAL, symbol);
            return;
        }

        InitStatus initStatus = this.uninitializedVars.get(symbol);
        if (initStatus == InitStatus.PARTIAL_INIT) {
            dlog.error(pos, DiagnosticErrorCode.CANNOT_ASSIGN_VALUE_TO_POTENTIALLY_INITIALIZED_FINAL, symbol);
        }
    }

    private void terminateFlow() {
        this.flowTerminated = true;
    }

    private void checkUnusedImports(List<BLangImportPackage> imports) {
        for (BLangImportPackage importStmt : imports) {
            BLangIdentifier prefix = importStmt.alias;
            String prefixValue = prefix.value;
            Location location = prefix.pos;
            BPackageSymbol symbol = importStmt.symbol;
            if (symbol != null && !symbol.isUsed && !Names.IGNORE.value.equals(prefixValue)) {
                dlog.error(location, DiagnosticErrorCode.UNUSED_MODULE_PREFIX, prefixValue);
            }
        }
    }

    private void checkUnusedErrorVarsDeclaredWithVar() {
        for (Map.Entry<BSymbol, Location> entry : this.unusedErrorVarsDeclaredWithVar.entrySet()) {
            this.dlog.error(entry.getValue(), DiagnosticErrorCode.UNUSED_VARIABLE_WITH_INFERRED_TYPE_INCLUDING_ERROR,
                            entry.getKey().name);
        }
    }

    private void emitUnusedVariableWarnings(Map<BSymbol, Location> unusedLocalVariables) {
        for (Map.Entry<BSymbol, Location> entry : unusedLocalVariables.entrySet()) {
            this.dlog.warning(entry.getValue(), DiagnosticWarningCode.UNUSED_LOCAL_VARIABLE, entry.getKey().name);
        }
    }

    private boolean addVarIfInferredTypeIncludesError(BLangSimpleVariable variable) {
        if (types.containsErrorType(variable.getBType().semType())) {
            unusedErrorVarsDeclaredWithVar.put(variable.symbol, variable.pos);
            return true;
        }
        return false;
    }

    private boolean isLocalVariableDefinedWithNonWildCardBindingPattern(BLangSimpleVariable variable) {
        if (isWildCardBindingPattern(variable)) {
            return false;
        }

        return isLocalVariable(variable.symbol);
    }

    private boolean isWildCardBindingPattern(BLangSimpleVariable variable) {
        return Names.IGNORE.value.equals(variable.name.value);
    }

    private boolean isWildCardBindingPattern(BVarSymbol symbol) {
        return Names.IGNORE == symbol.name;
    }

    private boolean isLocalVariable(BVarSymbol symbol) {
        if (symbol == null) {
            return false;
        }

        BSymbol owner = symbol.owner;

        if (owner == null || owner.tag == SymTag.PACKAGE) {
            return false;
        }

        if (owner.tag == SymTag.LET) {
            return true;
        }

        if (owner.tag != SymTag.FUNCTION) {
            return false;
        }

        long flags = symbol.flags;

        SymbolKind kind = symbol.kind;
        if (kind == SymbolKind.PATH_PARAMETER || kind == SymbolKind.PATH_REST_PARAMETER) {
            return false;
        }

        return !Symbols.isFlagOn(flags, Flags.REQUIRED_PARAM)
                && !Symbols.isFlagOn(flags, Flags.DEFAULTABLE_PARAM)
                && !Symbols.isFlagOn(flags, Flags.INCLUDED)
                && !Symbols.isFlagOn(flags, Flags.REST_PARAM);
    }

    private void populateUnusedVariableMapForNonSimpleBindingPatternVariables(
            Map<BSymbol, Location> unusedLocalVariables, BLangVariable variable) {
        if (!isLocalVariable(variable.symbol)) {
            return;
        }

        populateUnusedVariableMapForMembers(unusedLocalVariables, variable);
    }

    private void populateUnusedVariableMapForMembers(Map<BSymbol, Location> unusedLocalVariables,
                                                     BLangVariable variable) {
        if (variable == null) {
            return;
        }

        switch (variable.getKind()) {
            case VARIABLE:
                BLangSimpleVariable simpleVariable = (BLangSimpleVariable) variable;
                if (!isWildCardBindingPattern(simpleVariable)) {
                    unusedLocalVariables.put(simpleVariable.symbol, simpleVariable.pos);
                }
                break;
            case RECORD_VARIABLE:
                BLangRecordVariable recordVariable = (BLangRecordVariable) variable;
                for (BLangRecordVariable.BLangRecordVariableKeyValue member : recordVariable.variableList) {
                    populateUnusedVariableMapForMembers(unusedLocalVariables, member.valueBindingPattern);
                }

                populateUnusedVariableMapForMembers(unusedLocalVariables, recordVariable.restParam);
                break;
            case TUPLE_VARIABLE:
                BLangTupleVariable tupleVariable = (BLangTupleVariable) variable;
                for (BLangVariable memberVariable : tupleVariable.memberVariables) {
                    populateUnusedVariableMapForMembers(unusedLocalVariables, memberVariable);
                }

                populateUnusedVariableMapForMembers(unusedLocalVariables, tupleVariable.restVariable);
                break;
            case ERROR_VARIABLE:
                BLangErrorVariable errorVariable = (BLangErrorVariable) variable;
                populateUnusedVariableMapForMembers(unusedLocalVariables, errorVariable.message);
                populateUnusedVariableMapForMembers(unusedLocalVariables, errorVariable.cause);
                for (BLangErrorVariable.BLangErrorDetailEntry member : errorVariable.detail) {
                    populateUnusedVariableMapForMembers(unusedLocalVariables, member.valueBindingPattern);
                }
                populateUnusedVariableMapForMembers(unusedLocalVariables, errorVariable.restDetail);
                break;
        }
    }

    private boolean isNotVariableReferenceLVExpr(BLangSimpleVarRef varRefExpr) {
        if (!varRefExpr.isLValue) {
            return true;
        }

        BLangNode parent = varRefExpr.parent;
        return parent != null && parent.getKind() != NodeKind.ASSIGNMENT;
    }

    private boolean isNotRangeExpr(BLangExpression collection) {
        if (collection.getKind() != NodeKind.BINARY_EXPR) {
            return true;
        }

        OperatorKind opKind = ((BLangBinaryExpr) collection).opKind;
        return opKind != OperatorKind.HALF_OPEN_RANGE && opKind != OperatorKind.CLOSED_RANGE;
    }

    private boolean isFunctionOrMethodDefinedInCurrentModule(BSymbol owner, SymbolEnv env) {
        if (Symbols.isFlagOn(owner.flags, Flags.CLASS)) {
            return owner.owner == getEnclPkgSymbol(env);
        }

        return owner == getEnclPkgSymbol(env);
    }

    private BPackageSymbol getEnclPkgSymbol(SymbolEnv env) {
        BLangPackage enclPkg = env.enclPkg;

        if (enclPkg != null) {
            return enclPkg.symbol;
        }

        SymbolEnv enclEnv = env.enclEnv;
        if (enclEnv == null) {
            return null;
        }

        return getEnclPkgSymbol(enclEnv);
    }

    private enum InitStatus {
        UN_INIT, PARTIAL_INIT
    }

    private static class BranchResult {

        Map<BSymbol, InitStatus> uninitializedVars;
        Map<BSymbol, InitStatus> possibleFailureUnInitVars;
        boolean flowTerminated;
        boolean definiteFailureReached;
        boolean possibleFailureReached;


        BranchResult(Map<BSymbol, InitStatus> uninitializedVars, Map<BSymbol, InitStatus> possibleFailureUnInitVars,
                     boolean flowTerminated, boolean possibleFailureReached, boolean definiteFailureReached) {
            this.uninitializedVars = uninitializedVars;
            this.possibleFailureUnInitVars = possibleFailureUnInitVars;
            this.flowTerminated = flowTerminated;
            this.possibleFailureReached = possibleFailureReached;
            this.definiteFailureReached = definiteFailureReached;
        }
    }
}
