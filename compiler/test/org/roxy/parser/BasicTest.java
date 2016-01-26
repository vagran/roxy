package org.roxy.parser;

import org.junit.Test;

import java.io.IOException;

import static utils.Utils.AssertThrows;

public class BasicTest {

Grammar grammar = new Grammar() {{

    Node("decimal-digit").Def(CharRange('0', '9'));
    Node("alphabetic").Def(CharRange('a', 'z').IncludeRange('A', 'Z'));
    Node("whitespace").Def(AnyChar(" \t\r\n"));

    Node("multiline-comment").Sequence(
        String("/*"),
        Any(
            AnyChar().Exclude('*').NoneToMany(),
            Sequence(Char('*'), AnyChar().Exclude('/'))),
        String("*/"));

    Node("gap").Any(
        NodeRef("whitespace"),
        NodeRef("multiline-comment")).OneToMany();

    Node("string-literal").Sequence(
        Char('"'),
        Any(AnyChar().Exclude("\"\\"),
            Sequence(Char('\\'), AnyChar())).NoneToMany(),
        Char('"')).Val();

    Node("number-literal").Def(NodeRef("decimal-digit").OneToMany()).Val();

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneToMany()).Val();

    Node("statement").Sequence(
        NodeRef("identifier"),
        NodeRef("gap").NoneToOne(),
        Char('='),
        NodeRef("gap").NoneToOne(),
        Any(
            NodeRef("string-literal"),
            NodeRef("number-literal")),
        NodeRef("gap").NoneToOne(),
        Char(';')).Val();

    Node("file").Sequence(
        NodeRef("gap").NoneToOne(),
        Sequence(
            NodeRef("statement"),
            NodeRef("gap").NoneToOne()
        ).NoneToMany()).Val();

    System.out.print(FindNode("file"));
    Compile();
    System.out.print(FindNode("file"));
}};

Grammar.Node fileNode = grammar.FindNode("file");

String testFile1 =
    "someIdent = \"some value \\\\aa\\\"bb\"\n" +
    "\n" +
    "   \t;a=1;b=2;/* Some comment a*b/*/\n" +
    "/* multiline\r\n" +
    "comment*/\r" +
    "  c =   3;";

@Test public void
SomeTest()
    throws IOException
{
    Parser parser = new Parser(fileNode, /*testFile1*/"                                         ");
    parser.Parse();
}

@Test public void
InvalidGrammar()
{
    Grammar invGrammar = new Grammar() {{
        // Recursive definition with minimal size of zero characters.
        Node("gap").Sequence(
            Any(Char('q').NoneToMany(),
                Char('w').NoneToMany()),
            NodeRef("gap").NoneToMany());

        Compile();
        System.out.print(FindNode("gap"));
    }};
    Parser parser = new Parser(invGrammar.FindNode("gap"), "some string");
    AssertThrows(IllegalStateException.class, parser::Parse);
}

@Test public void
UnclosedStringLiteral()
    throws IOException
{
    Parser parser = new Parser(fileNode, "a = \"some value");
    AssertThrows(RuntimeException.class, parser::Parse);//XXX change error reporting
}

@Test public void
Finalization()
    throws IOException
{
    Grammar grammar = new Grammar() {{
        Any(
            Char('a').Quantity(3),
            Char('a').Quantity(5)
        ).Name("file");
        Compile();
        System.out.print(FindNode("file"));
    }};
    Parser parser = new Parser(grammar.FindNode("file"), "aa");
    AssertThrows(RuntimeException.class, parser::Parse);//XXX change error reporting

    new Parser(grammar.FindNode("file"), "aaa").Parse();

    parser = new Parser(grammar.FindNode("file"), "aaaa");
    AssertThrows(RuntimeException.class, parser::Parse);//XXX change error reporting

    new Parser(grammar.FindNode("file"), "aaaaa").Parse();

    parser = new Parser(grammar.FindNode("file"), "aaaaaa");
    AssertThrows(RuntimeException.class, parser::Parse);//XXX change error reporting
}

@Test public void
Finalization2()
    throws IOException
{
    Grammar grammar = new Grammar() {{
        Any(
            Char('a').Quantity(3, 5),
            Char('a').Quantity(7)
        ).Name("file");
        Compile();
        System.out.print(FindNode("file"));
    }};
    Parser parser = new Parser(grammar.FindNode("file"), "aa");
    AssertThrows(RuntimeException.class, parser::Parse);//XXX change error reporting

    new Parser(grammar.FindNode("file"), "aaa").Parse();
    new Parser(grammar.FindNode("file"), "aaaa").Parse();
    new Parser(grammar.FindNode("file"), "aaaaa").Parse();

    parser = new Parser(grammar.FindNode("file"), "aaaaaa");
    AssertThrows(RuntimeException.class, parser::Parse);//XXX change error reporting

    new Parser(grammar.FindNode("file"), "aaaaaaa").Parse();

    parser = new Parser(grammar.FindNode("file"), "aaaaaaaa");
    AssertThrows(RuntimeException.class, parser::Parse);//XXX change error reporting
}

}
