package org.roxy.parser;

import org.junit.Test;

public class BasicTest {

Grammar grammar = new Grammar() {{

    Node("decimal-digit").Def(CharRange('0', '9'));

}};

@Test public void
SomeTest()
{

}

}
