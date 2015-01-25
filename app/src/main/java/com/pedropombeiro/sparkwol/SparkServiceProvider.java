package com.pedropombeiro.sparkwol;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;

/**
 * Created by Pedro on 19.01.2015.
 */
public class SparkServiceProvider {
    public static SparkService createSparkService(RequestInterceptor requestInterceptor) {
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint("https://api.spark.io")
                .setRequestInterceptor(requestInterceptor)
                .build();
        return restAdapter.create(SparkService.class);
    }
}
