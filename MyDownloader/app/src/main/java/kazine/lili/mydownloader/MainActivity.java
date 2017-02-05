package kazine.lili.mydownloader;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import kazine.lili.mydownloader.downloader.CallBuilder;
import kazine.lili.mydownloader.downloader.Downloader;
import kazine.lili.mydownloader.downloader.ResponseData;

public class MainActivity extends AppCompatActivity {

    private TextView show_case;
    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        show_case = (TextView) findViewById(R.id.showcase);
        input = (EditText) findViewById(R.id.input);
    }

    public void send_request(View view) {
        String s = input.getText().toString();
        if (s.isEmpty()) {
            Toast.makeText(this, "没有输入地址", Toast.LENGTH_LONG);
        } else {
            request(s);
        }
    }

    public void test(View view) {
        request("https://github.com");
    }

    private void request(String s) {
        Downloader.with(this)
                .assign(new CallBuilder()
                        .url(s)
                        .buildGet())
                .finish(new Downloader.ResponseCallBack() {
                    @Override
                    public void onResponse(ResponseData response) {
                        show_case.setText(response.getBody());
                    }
                })
                .broken(new Downloader.FailureCallBack() {
                    @Override
                    public void onFailure(Exception e) {
                        show_case.setText(e.getMessage());
                    }
                })
                .execute();
    }
    @Override
    public void onBackPressed() {
        finish();
    }
}
