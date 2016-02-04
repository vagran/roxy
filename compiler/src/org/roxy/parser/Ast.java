package org.roxy.parser;

import java.util.ArrayList;

/** Abstract syntax tree. Result of text parsing. */
public class Ast {

/** Produces tag for a AST node. Invoked when AST node is fully constructed with all its children.
 * The fabric can modify the node according to its needs, e.g. free unnecessary children nodes or
 * stored string after processing.
 */
@FunctionalInterface
public interface TagFabric {

    Object
    Produce(Node node, Parser.Summary summary);
}

public class Node {

    public Grammar.Node grammarNode;
    public StringBuilder str;
    public Node parent;
    public ArrayList<Node> children;
    public Parser.InputPosition startPosition, endPosition;
    public Object tag;

    void
    AppendChar(int c)
    {
        if (str == null) {
            str = new StringBuilder();
        }
        str.append((char)c);
    }

    void
    AppendChild(Ast.Node child)
    {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
        child.parent = this;
    }

    /** Called when all characters or sub-nodes added. */
    void
    Commit(Parser.InputPosition endPosition, Parser.Summary summary)
    {
        assert this.endPosition == null;
        this.endPosition = endPosition;
        if (grammarNode.valTagFabric != null) {
            tag = grammarNode.valTagFabric.Produce(this, summary);
        }
        if (parent == null) {
            root = this;
        }
    }

    /** Check if the specified node is ancestor of this node. Returns also true for the same node. */
    boolean
    IsAncestor(Node ancestor)
    {
        Node node = this;
        while (node != null) {
            if (node == ancestor) {
                return true;
            }
            node = node.parent;
        }
        return false;
    }

    String
    GetName()
    {
        //XXX
        if (tag != null) {
            return tag.toString();
        } else if (grammarNode.name != null) {
            return grammarNode.name;
        } else {
            return "<unnamed>";
        }
    }

    String
    Describe()
    {
        //XXX
        return grammarNode.toString();
    }
}

Node root;

Node
CreateNode()
{
    return new Node();
}

}
