package com.calendar.mcp.domain.models;

/**
 * One of the caller's friends. The handle -- userName#hashtag, which Wely keeps unique -- is what
 * travels back and forth with a model: a UUID means nothing to it, and inventing a handle that
 * happens to exist is far harder than inventing a plausible id.
 */
public record Friend(String userId, String userName, Integer hashtag, String name, String closeness) {

    public String handle() {
        return hashtag == null ? userName : userName + "#" + hashtag;
    }

    public Friend named(String resolved) {
        return new Friend(userId, userName, hashtag, resolved, closeness);
    }
}
