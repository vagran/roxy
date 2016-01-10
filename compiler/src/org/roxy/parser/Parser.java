package org.roxy.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.LinkedList;

/** Parses the text into AST using the provided grammar. */
public class Parser {

public
Parser(Grammar.Node grammar, Reader reader)
{
    this.grammar = grammar;
    this.reader = reader;
}

public
Parser(Grammar.Node grammar, InputStream stream)
{
    this(grammar, new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
}

public
Parser(Grammar.Node grammar, String str)
{
    this(grammar, new StringReader(str));
}

public void/*XXX*/
Parse()
{
    InitializeState();
    //XXX

}

// /////////////////////////////////////////////////////////////////////////////////////////////////

private class ParserNode {
    /** Parent node in stack/tree. Next free node when in free list. */
    public ParserNode parent;
    /** Previous character node. Used for character nodes only. */
    public ParserNode prev;
    /** Number of references from both next character nodes (which reference this node via "prev"
     * member) and child nodes (via "parent" member).
     */
    public int refCount;
    /** Corresponding grammar node. */
    public Grammar.Node grammarNode;
    /** Assigned AST node if committed and valuable. */
    public Ast.Node astNode;
    /** Number the corresponding grammar node has been repeated before this node (number of spent
     * quantification points so far).
     */
    public int numRepeated;

    public
    ParserNode(Grammar.Node grammarNode)
    {
        Initialize(grammarNode);
    }

    public void
    Initialize(Grammar.Node grammarNode)
    {
        parent = null;
        prev = null;
        this.grammarNode = grammarNode;
        astNode = null;
        numRepeated = 0;
    }

    public void
    AddRef()
    {
        refCount++;
    }

    public void
    Release()
    {
        assert refCount > 0;
        refCount--;
        if (refCount == 0) {
            FreeNode(this);
        }
    }

    public void
    SetParent(ParserNode parent)
    {
        assert this.parent == null;
        this.parent = parent;
        AddRef();
    }
}

private final Grammar.Node grammar;
private final Reader reader;

/** Free nodes pool. */
private ParserNode freeNodes;
/** Tips of current parsing branches. */
private final LinkedList<ParserNode> curBranches = new LinkedList<>();

private ParserNode
AllocateNode(Grammar.Node grammarNode)
{
    if (freeNodes != null) {
        ParserNode node = freeNodes;
        freeNodes = node.parent;
        node.Initialize(grammarNode);
        return node;
    }
    return new ParserNode(grammarNode);
}

private void
FreeNode(ParserNode node)
{
    assert node.refCount == 0;
    node.parent = freeNodes;
    freeNodes = node;
}

/** Prepare parser for the first character processing. Creates initial parsing branches. */
private void
InitializeState()
{
    ArrayDeque<Grammar.Node> stack = new ArrayDeque<>();
    InitializeState(AllocateNode(grammar), stack);
}

/**
 *
 * @param node
 * @param grammarStack Current stack of grammar nodes. Used to prevent from recursion. The recursion
 *                     can be used in grammar definition but not all recursion case are acceptable.
 * @return True to add also next sibling node (propagated to parent if last child node).
 */
private boolean
InitializeState(ParserNode node, ArrayDeque<Grammar.Node> grammarStack)
{
    if (grammarStack.contains(node.grammarNode)) {
        throw new IllegalStateException("Invalid grammar recursion detected (empty element recursion)");
    }
    grammarStack.push(node.grammarNode);
    boolean addNext = false;

    if (node.grammarNode instanceof Grammar.SequenceNode) {
        boolean pendingAdd = false;
        for (Grammar.Node childGrammarNode: node.grammarNode) {
            ParserNode childNode = AllocateNode(childGrammarNode);
            childNode.SetParent(node);
            pendingAdd = InitializeState(childNode, grammarStack);
            if (!pendingAdd) {
                break;
            }
        }
        if (pendingAdd) {
            addNext = true;
        }

    } else if (node.grammarNode instanceof Grammar.VariantsNode) {
        for (Grammar.Node childGrammarNode: node.grammarNode) {
            ParserNode childNode = AllocateNode(childGrammarNode);
            childNode.SetParent(node);
            if (InitializeState(childNode, grammarStack)) {
                addNext = true;
            }
        }

    } else if (node.grammarNode instanceof Grammar.CharNode) {
        curBranches.add(node);
    }

    if (node.grammarNode.GetMinQuantity() == 0) {
        addNext = true;
    }
    grammarStack.pop();
    return addNext;
}

}
