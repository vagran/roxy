package org.roxy.parser;

import java.util.ArrayList;

/** Abstract syntax tree. Result of text parsing. */
public class Ast {

public class Node {

    public Grammar.Node grammarNode;
    public StringBuilder str;
    public Node parent;
    public ArrayList<Node> children;
    public Parser.InputPosition startPosition, endPosition;

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
    Commit(Parser.InputPosition endPosition)
    {
        assert this.endPosition == null;
        this.endPosition = endPosition;
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
        if (grammarNode.name != null) {
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

Node
CreateNode()
{
    return new Node();
}

}
