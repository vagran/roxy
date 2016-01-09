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
        Char('"')).Val();

    Node("number-literal").Def(NodeRef("decimal-digit").OneOrMany()).Val();

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneOrMany()).Val();

    Node("statement").Sequence(
        NodeRef("identifier"),
        NodeRef("gap").NoneOrMany(),
        Char('='),
        NodeRef("gap").NoneOrMany(),
        Any(
            NodeRef("string-literal"),
            NodeRef("number-literal")),
        NodeRef("gap").NoneOrMany(),
        Char(';')).Val();

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

Grammar.Node fileNode = grammar.FindNode("file");

String testFile1 =
    "someIdent = \"some value \\aa\"bb\"\n" +
    "\n" +
    "   \t;a=1;b=2;/* Some comment a*b/*/\n" +
    "/* multiline\r\n" +
    "comment*/\r" +
    "  c =   3;";

@Test public void
SomeTest()
{
    Parser parser = new Parser(fileNode, testFile1);
    parser.Parse();
}

}
