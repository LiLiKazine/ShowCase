package kazine.lili.mydownloader.downloader;

import android.support.annotation.NonNull;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Created by LiLi
 * lilikazine@gmail.com
 */

public class CallBuilder {
    private String url;
    private String params;

    public CallBuilder url(@NonNull String url) {
        this.url = url;
        return this;
    }

    public CallBuilder params(@NonNull String params) {
        this.params = params;
        return this;
    }

    public Call buildPost() {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), params);
        Request.Builder builder = new Request.Builder().url(url).post(body);
        Request request = builder.build();
        return new OkHttpClient().newCall(request);
    }

    public Call buildGet() {
        Request.Builder builder = new Request.Builder().url(url).get();
        Request request = builder.build();
        return new OkHttpClient().newCall(request);
    }
}
