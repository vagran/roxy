package org.roxy.parser;

import java.util.ArrayList;

/** Abstract syntax tree. Result of text parsing. */
public class Ast {

public class Node {

    public StringBuilder str;
    public ArrayList<Node> children;
    public Parser.InputPosition inputPosition;

    void
    AppendChar(int c)
    {
        if (str == null) {
            str = new StringBuilder();
        }
        str.append(c);
    }

    void
    AppendChild(Ast.Node child)
    {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }
}

Node
CreateNode()
{
    return new Node();
}

}
