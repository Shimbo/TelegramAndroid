package org.telegram.circles.data;

import org.telegram.circles.CirclesConstants;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.R;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
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
                    final String httpAgent = ApplicationLoader.applicationContext.getString(BuildConfig.DEBUG ? R.string.AppNameBeta : R.string.AppName) +
                            " v"+BuildConfig.VERSION_NAME+"("+BuildConfig.VERSION_CODE+") Android "+System.getProperty("http.agent");
                    HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
                    logger.setLevel(HttpLoggingInterceptor.Level.HEADERS);
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

                                return chain.proceed(request);
                            })
                            .addInterceptor(logger);

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
