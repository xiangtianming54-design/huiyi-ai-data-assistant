package com.huiyi.assistant.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalCase {

    private String id;
    private String entrypoint;
    private String question;
    private String sessionId;
    private String workspace = "default";
    private boolean spacesSpecified;
    private List<String> spaces;
    private Stubs stubs = new Stubs();
    private Expect expect = new Expect();

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String entrypoint() {
        return entrypoint;
    }

    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public String question() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String sessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String workspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public List<String> spaces() {
        return spacesSpecified ? spaces : null;
    }

    @JsonSetter("spaces")
    public void setSpaces(List<String> spaces) {
        this.spacesSpecified = true;
        this.spaces = spaces == null ? List.of() : spaces;
    }

    public Stubs stubs() {
        return stubs;
    }

    public void setStubs(Stubs stubs) {
        this.stubs = stubs == null ? new Stubs() : stubs;
    }

    public Expect expect() {
        return expect;
    }

    public void setExpect(Expect expect) {
        this.expect = expect == null ? new Expect() : expect;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Stubs {
        private String rewrite;
        private String intent;
        private boolean deadlineExpired;
        private boolean budgetExceeded;
        private String dirtyAnswer;
        private List<ToolStub> tools = new ArrayList<>();

        public String rewrite() {
            return rewrite;
        }

        public void setRewrite(String rewrite) {
            this.rewrite = rewrite;
        }

        public String intent() {
            return intent;
        }

        public void setIntent(String intent) {
            this.intent = intent;
        }

        public boolean deadlineExpired() {
            return deadlineExpired;
        }

        public void setDeadlineExpired(boolean deadlineExpired) {
            this.deadlineExpired = deadlineExpired;
        }

        public boolean budgetExceeded() {
            return budgetExceeded;
        }

        public void setBudgetExceeded(boolean budgetExceeded) {
            this.budgetExceeded = budgetExceeded;
        }

        public String dirtyAnswer() {
            return dirtyAnswer;
        }

        public void setDirtyAnswer(String dirtyAnswer) {
            this.dirtyAnswer = dirtyAnswer;
        }

        public List<ToolStub> tools() {
            return tools;
        }

        public void setTools(List<ToolStub> tools) {
            this.tools = tools == null ? new ArrayList<>() : tools;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolStub {
        private String name;
        private boolean ok;
        private String error;
        private Map<String, Object> data;

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean ok() {
            return ok;
        }

        public void setOk(boolean ok) {
            this.ok = ok;
        }

        public String error() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public Map<String, Object> data() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Expect {
        private String intent;
        private String answerEquals;
        private List<String> answerContains = new ArrayList<>();
        private boolean errorCodeSpecified;
        private String errorCode;
        private Boolean analysisHasToolCalls;
        private boolean toolEventsSpecified;
        private List<ToolEventExpect> toolEvents = new ArrayList<>();
        private List<String> forbiddenSubstrings = new ArrayList<>();

        public String intent() {
            return intent;
        }

        public void setIntent(String intent) {
            this.intent = intent;
        }

        public String answerEquals() {
            return answerEquals;
        }

        public void setAnswerEquals(String answerEquals) {
            this.answerEquals = answerEquals;
        }

        public List<String> answerContains() {
            return answerContains;
        }

        public void setAnswerContains(List<String> answerContains) {
            this.answerContains = answerContains == null ? new ArrayList<>() : answerContains;
        }

        public boolean errorCodeSpecified() {
            return errorCodeSpecified;
        }

        public String errorCode() {
            return errorCode;
        }

        @JsonSetter("errorCode")
        public void setErrorCode(String errorCode) {
            this.errorCodeSpecified = true;
            this.errorCode = errorCode;
        }

        public Boolean analysisHasToolCalls() {
            return analysisHasToolCalls;
        }

        public void setAnalysisHasToolCalls(Boolean analysisHasToolCalls) {
            this.analysisHasToolCalls = analysisHasToolCalls;
        }

        public boolean toolEventsSpecified() {
            return toolEventsSpecified;
        }

        public List<ToolEventExpect> toolEvents() {
            return toolEvents;
        }

        @JsonSetter("toolEvents")
        public void setToolEvents(List<ToolEventExpect> toolEvents) {
            this.toolEventsSpecified = true;
            this.toolEvents = toolEvents == null ? new ArrayList<>() : toolEvents;
        }

        public List<String> forbiddenSubstrings() {
            return forbiddenSubstrings;
        }

        public void setForbiddenSubstrings(List<String> forbiddenSubstrings) {
            this.forbiddenSubstrings = forbiddenSubstrings == null ? new ArrayList<>() : forbiddenSubstrings;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolEventExpect {
        private String name;
        private String phase;
        private String status;

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String phase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public String status() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
