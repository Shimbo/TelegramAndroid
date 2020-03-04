package org.telegram.circles.data;

import io.reactivex.Single;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface RetrofitService {
    @GET("/tgfork")
    Single<CirclesList> getCircles(@Header("Authorization") String token);

    @POST("/tgfork/connection")
    Single<Object> sendCircleInclusionData(@Header("Authorization") String token, @Body Object body);

    @POST("/tgfork/connections")
    Single<Object> sendMembers(@Header("Authorization") String token, @Body Object body);
}
