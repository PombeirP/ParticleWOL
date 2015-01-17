package com.pedropombeiro.sparkwol;

import java.util.List;

import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.Header;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.mime.TypedOutput;

/**
 * Created by Pedro on 16.01.2015.
 */
public interface SparkService {
    @GET("/v1/devices")
    void getDevices(Callback<List<SparkDevice>> callback);

    @GET("/v1/devices/{deviceId}")
    void getDevice(@Path("deviceId") String deviceId, Callback<SparkDevice> callback);

    @GET("/v1/devices/{deviceId}/{variable}")
    void getVariable(@Path("variable") String variable, @Path("deviceId") String deviceId, Callback<SparkVariable> callback);

    @FormUrlEncoded
    @POST("/v1/devices/{deviceId}/{function}")
    void invokeFunction(@Path("deviceId") String deviceId, @Path("function") String function, @Field("args") String args, Callback<Response> callback);

    @Multipart
    @PUT("/v1/devices/{deviceId}")
    void flashFirmware(@Part("file") TypedOutput firmware, @Path("deviceId") String deviceId, Callback<UploadSparkFirmwareResponse> callback);
}

