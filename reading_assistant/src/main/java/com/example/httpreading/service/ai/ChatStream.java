package com.example.httpreading.service.ai;

import okhttp3.Call;

public interface ChatStream {
    void stage(String stage);
    void delta(String text);
    void reset();
    void checkActive();
    void attach(Call call);
    void detach(Call call);
}
