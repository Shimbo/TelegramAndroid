package org.telegram.circles.data;

import org.json.JSONObject;
import org.telegram.circles.CirclesConstants;
import org.telegram.circles.utils.Logger;
import org.telegram.messenger.BuildConfig;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitHelper {
    private static final Object   lockObject = new Object();
    private static       RetrofitService service   = null;

    private RetrofitHelper() {

    }

    public static RetrofitService service() {
        if (service == null) {
            synchronized (lockObject) {
                if (service == null) {
                    final String httpAgent = "android circles v"+BuildConfig.VERSION_NAME+"("+BuildConfig.VERSION_CODE+") "+System.getProperty("http.agent");
                    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                            .connectTimeout(CirclesConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                            .readTimeout(CirclesConstants.READ_TIMEOUT, TimeUnit.SECONDS)
                            .writeTimeout(CirclesConstants.WRITE_TIMEOUT, TimeUnit.SECONDS)
                            .addInterceptor((Interceptor.Chain chain) -> {
                                Request original = chain.request();
                                Request request = original.newBuilder()
                                        .header("User-Agent", httpAgent)
                                        .build();

                                Buffer buffer = new Buffer();
                                if (request.body() != null) {
                                    request.body().writeTo(buffer);
                                }
                                Logger.d("Request " + request.method() + " to " + request.url() + "\n" + buffer.readUtf8());

                                long t1 = System.nanoTime();
                                Response response = chain.proceed(request);
                                long t2 = System.nanoTime();

                                Logger.d(String.format(Locale.US, "Response %s from %s in %.1fms",
                                        response.code(), response.request().url(), (t2 - t1) / 1e6d));

                                if (BuildConfig.DEBUG) {
                                    String data = response.peekBody(1024000).string();
                                    try {
                                        JSONObject object = new JSONObject(data);
                                        String formatted = object.toString(4);
                                        if (formatted.split("\n").length < 100) {
                                            data = formatted;
                                        }
                                    } catch (Exception e) {}
                                    Logger.d("Data: " + data);
                                }

                                return response;
                            });

                    service = new Retrofit.Builder()
                            .baseUrl(CirclesConstants.BASE_URL)
                            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(clientBuilder.build())
                            .build().create(RetrofitService.class);
                }
            }
        }
        return service;
    }
}
