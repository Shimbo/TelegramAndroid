package org.telegram.circles.data;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface RetrofitService {
    @GET("/tgfork")
    Call<CirclesList> getCircles(@Header("Authorization") String token);

    @POST("/tgfork/connection")
    Call<Object> sendCircleInclusionData(@Header("Authorization") String token, @Body Object body);

    @POST("/tgfork/connections")
    Call<Object> sendMembers(@Header("Authorization") String token, @Body Object body);
}
