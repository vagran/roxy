package org.roxy.parser;

/** Language grammar description. */
public class Grammar {

public class Node {

    /** Set node name if not yet done. */
    public Node
    Name(String name)
    {
        //XXX
        return this;
    }

    public Node
    Optional()
    {
        return NoneOrOne();
    }

    public Node
    Quantity(int num)
    {
        return Quantity(num, num);
    }

    public Node
    Quantity(int numMin, int numMax)
    {
        //XXX
        return this;
    }

    public Node
    OneOrMany()
    {
        return Quantity(1, -1);
    }

    public Node
    NoneOrOne()
    {
        return Quantity(0, 1);
    }

    public Node
    NoneOrMany()
    {
        return Quantity(0, -1);
    }
}

public class NodeBuilder {

    public NodeBuilder
    Def(Node node)
    {
        //XXX
        return this;
    }

    public NodeBuilder
    Any(Node... nodes)
    {
        return Def(Grammar.this.Any(nodes));
    }

    public NodeBuilder
    Sequence(Node... nodes)
    {
        return Def(Grammar.this.Sequence(nodes));
    }
}

public class NodeRef extends Node {

}

public class CharNode extends Node {

    public CharNode
    Exclude(String chars)
    {
        //XXX
        return this;
    }

    public CharNode
    ExcludeRange(int cMin, int cMax)
    {
        //XXX

        return this;
    }
}

public class NodeGroup extends Node {

}

public NodeBuilder
Node(String name)
{
    //XXX
    return null;
}

public NodeRef
NodeRef(String name)
{
    //XXX

    return null;
}

public CharNode
Char(int c)
{
    //XXX
    return null;
}

public CharNode
CharRange(int cMin, int cMax)
{
    //XXX
    return null;
}

/** Matches any character. Exclusions cah be added in the returned node. */
public CharNode
AnyChar()
{
    //XXX
    return null;
}

/** Matches any character from the provided characters set. */
public CharNode
AnyChar(String chars)
{
    //XXX
    return null;
}

/** Characters sequence.
 * @return Last character node.
 */
public CharNode
String(String string)
{
    return String(string, true);
}

/** Characters sequence.
 * @param caseSensitive Case sensitive match if true, case insensitive match otherwise.
 * @return Last character node.
 */
public CharNode
String(String string, boolean caseSensitive)
{
    //XXX
    return null;
}

public NodeGroup
Sequence(Node... nodes)
{
    //XXX
    return null;
}

public NodeGroup
Any(Node... nodes)
{
    //XXX
    return null;
}

//XXX
public void
Compile()
{
    //XXX
}

}
