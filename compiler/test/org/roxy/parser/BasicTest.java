package org.roxy.parser;

import org.junit.Test;

public class BasicTest {

Grammar grammar = new Grammar() {{

    Node("decimal-digit").Def(CharRange('0', '9'));
    Node("alphabetic").Def(CharRange('a', 'z').IncludeRange('A', 'Z'));
    Node("whitespace").Def(AnyChar(" \t\r\n"));

    Node("multiline-comment").Sequence(String("/*"), AnyChar().NoneOrMany(), String("*/"));

    Node("gap").Sequence(
        Any(NodeRef("whitespace").OneOrMany(),
            NodeRef("multiline-comment").OneOrMany()),
        NodeRef("gap").NoneOrMany());

    Node("string-literal").Sequence(
        Char('"'),
        Any(AnyChar().Exclude("\"\\"),
            Sequence(Char('\\'), AnyChar())),
        Char('"'));

    Node("number-literal").Def(NodeRef("decimal-digit").OneOrMany());

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneOrMany());

    Node("statement").Sequence(
        NodeRef("identifier"),
        NodeRef("gap").NoneOrMany(),
        Char('='),
        NodeRef("gap").NoneOrMany(),
        Any(
            NodeRef("string-literal"),
            NodeRef("number-literal")),
        NodeRef("gap").NoneOrMany(),
        Char(';'));

    Node("file").Sequence(
        NodeRef("gap").NoneOrMany(),
        Sequence(
            NodeRef("statement"),
            NodeRef("gap")
        ).NoneOrMany());

    System.out.print(FindNode("file"));
    Compile();
    System.out.print(FindNode("file"));
}};

@Test public void
SomeTest()
{

}

}
