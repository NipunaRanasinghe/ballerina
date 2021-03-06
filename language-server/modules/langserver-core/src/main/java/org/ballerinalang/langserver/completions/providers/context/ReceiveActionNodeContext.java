/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.completions.providers.context;

import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.syntax.tree.ReceiveActionNode;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.commons.CompletionContext;
import org.ballerinalang.langserver.commons.completion.LSCompletionException;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.completions.SnippetCompletionItem;
import org.ballerinalang.langserver.completions.providers.AbstractCompletionProvider;
import org.ballerinalang.langserver.completions.util.Snippet;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Completion provider for {@link ReceiveActionNode}.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.completion.spi.CompletionProvider")
public class ReceiveActionNodeContext extends AbstractCompletionProvider<ReceiveActionNode> {

    public ReceiveActionNodeContext() {
        super(ReceiveActionNode.class);
    }

    @Override
    public List<LSCompletionItem> getCompletions(CompletionContext context, ReceiveActionNode node)
            throws LSCompletionException {
        List<Symbol> visibleSymbols = context.visibleSymbols(context.getCursorPosition());
        List<Symbol> filteredWorkers = visibleSymbols.stream()
                .filter(symbol -> symbol.kind() == SymbolKind.WORKER)
                .collect(Collectors.toList());
        List<LSCompletionItem> completionItems = this.getCompletionItemList(filteredWorkers, context);
        completionItems.add(new SnippetCompletionItem(context, Snippet.KW_DEFAULT.get()));

        return completionItems;
    }
}
