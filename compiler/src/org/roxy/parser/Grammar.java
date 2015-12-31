package org.roxy.parser;

/** Language grammar description. */
public class Grammar {

public class Node {

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

}

public class CharRangeNode extends Node {

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

public CharRangeNode
CharRange(int cMin, int cMax)
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
