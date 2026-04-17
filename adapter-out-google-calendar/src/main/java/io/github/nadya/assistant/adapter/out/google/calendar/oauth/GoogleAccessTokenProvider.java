package io.github.nadya.assistant.adapter.out.google.calendar.oauth;

public interface GoogleAccessTokenProvider {

    boolean isReady();

    String getAccessToken();

    void invalidate();
}
