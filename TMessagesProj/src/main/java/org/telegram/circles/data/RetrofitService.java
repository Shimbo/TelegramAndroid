package org.telegram.circles.data;

import java.util.ArrayList;

import io.reactivex.Single;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface RetrofitService {
    @GET("/tgfork")
    Single<Response<CirclesList>> getCircles(@Header("Authorization") String token);

    @POST("/tgfork/connection")
    Single<ResponseBody> changeConnection(@Header("Authorization") String token, @Body ChangeConnection body);

    @POST("/tgfork/connections")
    Single<ResponseBody> sendMembers(@Header("Authorization") String token, @Body ArrayList<ConnectionsState> body);
}
