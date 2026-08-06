package com.polarcode.browser;

public interface BrowserConnector {
    String status();

    String connectDefault();

    String disconnect();
}
