package com.huiyi.assistant.agent;

public interface ToolEventListener {

    ToolEventListener NOOP = new ToolEventListener() {};

    default void onStarted(String toolName) {}

    default void onCompleted(String toolName, String status) {}

    default void onFailed(String toolName, String status) {}
}
