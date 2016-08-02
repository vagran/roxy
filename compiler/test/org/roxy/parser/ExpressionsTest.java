package org.roxy.parser;

import org.junit.Test;

import java.io.IOException;

public class ExpressionsTest {

Grammar grammar = new Grammar() {{

    Node("decimal-digit").Def(CharRange('0', '9'));
    Node("alphabetic").Def(CharRange('a', 'z').IncludeRange('A', 'Z'));
    Node("whitespace").Def(AnyChar(" \t\r\n"));

    Node("gap").Any(
        NodeRef("whitespace")).OneToMany();

    Node("number-literal").Sequence(Char('-').NoneToOne(), NodeRef("decimal-digit").OneToMany()).
        Val(TestNodeTag.GetFabric(TestNodeTag.Type.NUM_LITERAL), true);

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneToMany())
        .Val(TestNodeTag.GetFabric(TestNodeTag.Type.IDENTIFIER), true);

    Node("expression").Any(
        NodeRef("identifier"),
        NodeRef("number-literal"),
        Sequence(NodeRef("expression"), NodeRef("gap"), Char('+'),
                 NodeRef("gap"), NodeRef("expression")),
        Sequence(NodeRef("expression"), NodeRef("gap"), Char('*'),
                 NodeRef("gap"), NodeRef("expression"))
    );

    Node("statement").Sequence(
        NodeRef("identifier"),
        NodeRef("gap").NoneToOne(),
        Char('='),
        NodeRef("gap").NoneToOne(),
        NodeRef("expression"),
        NodeRef("gap").NoneToOne(),
        Char(';')).Val(TestNodeTag.GetFabric(TestNodeTag.Type.STATEMENT));

    Node("file").Sequence(
        NodeRef("gap").NoneToOne(),
        Sequence(
            NodeRef("statement"),
            NodeRef("gap").NoneToOne()
        ).NoneToMany()).Val(TestNodeTag.GetFabric(TestNodeTag.Type.FILE));

    //System.out.print(FindNode("file"));
    Compile();
    //System.out.print(FindNode("file"));
}};

Grammar.Node fileNode = grammar.FindNode("file");

@Test
public void
Basic()
    throws IOException
{
    Parser parser = ParserUtil.TestParser(fileNode, "");
}

}
