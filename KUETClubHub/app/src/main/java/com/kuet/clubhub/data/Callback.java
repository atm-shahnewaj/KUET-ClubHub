package com.kuet.clubhub.data;

public interface Callback<T> {
    void onSuccess(T result);

    void onError(String message);
}
