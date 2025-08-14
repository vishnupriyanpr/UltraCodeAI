package com.devoxx.genie.service.prompt.response;

public interface ResponseListener {
    void onTokenReceived(String token);
    void onComplete();
}
