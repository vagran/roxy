package org.roxy.parser;

import org.junit.Test;

public class BasicTest {

Grammar grammar = new Grammar() {{

    Node("decimal-digit").Def(CharRange('0', '9'));
    Node("alphabetic").Def(CharRange('a', 'z').IncludeRange('A', 'Z'));

    Node("multiline-comment").Sequence(String("/*"), AnyChar().NoneOrMany(), String("*/"));

    Node("string-literal").Sequence(
        Char('"'),
        Any(AnyChar().Exclude("\"\\"),
            Sequence(Char('\\'), AnyChar())),
        Char('"'));

    Node("identifier-first-char").Any(NodeRef("alphabetic"), Char('_'));
    Node("identifier-char").Any(NodeRef("identifier-first-char"), NodeRef("decimal-digit"));
    Node("identifier").Sequence(
        NodeRef("identifier-first-char"),
        NodeRef("identifier-char").NoneOrMany());

    Compile("identifier");
}};

@Test public void
SomeTest()
{

}

}
