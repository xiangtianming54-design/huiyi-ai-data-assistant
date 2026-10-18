package com.huiyi.assistant.agent;

public class PromptBudgetExceededException extends RuntimeException {

    public PromptBudgetExceededException() {
        super("prompt_budget");
    }
}
