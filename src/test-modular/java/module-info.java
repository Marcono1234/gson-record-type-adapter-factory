module test {
    requires marcono1234.gson.recordadapter;
    exports test;
    opens test.open;

    requires org.junit.jupiter.api;
    // Only open packages to JUnit, but don't open complete module so that tested library
    // does not have access to internal classes of this test module
    opens test to org.junit.platform.commons;
}
