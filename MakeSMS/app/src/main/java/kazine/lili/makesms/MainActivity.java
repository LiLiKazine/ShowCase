package kazine.lili.makesms;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import butterknife.BindView;
import butterknife.ButterKnife;
import kazine.lili.makesms.exceptions.EmptyContentException;

public class MainActivity extends AppCompatActivity {
    private final String TAG = getClass().getSimpleName();
    @BindView(R.id.et_phone)
    TextInputEditText mEtPhone;
    @BindView(R.id.et_time)
    TextInputEditText mEtTime;
    @BindView(R.id.et_context)
    TextInputEditText mEtContext;
    @BindView(R.id.insert_sms)
    Button mInsertSms;
    @BindView(R.id.til_time)
    TextInputLayout mTilTime;
    @BindView(R.id.til_content)
    TextInputLayout mTilContent;
    @BindView(R.id.btn)
    Button mBtn;
    @BindView(R.id.tv_hint)
    TextView mTvHint;

    private SharedPreferences mIndex;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mTilTime.setCounterEnabled(true);
        mTilTime.setError("时间格式不正确");

        mTilContent.setCounterEnabled(true);
        mTilContent.setError("短信内容不超过140字符");

        initListener();

        mIndex = getSharedPreferences("index", MODE_PRIVATE);
    }

    private void initListener() {
        mEtTime.addTextChangedListener(new SMSWatcher(new SMSWatcher.TextChangeListener() {
            @Override
            public void onChange(Editable editable) {
                SimpleDateFormat format = new SimpleDateFormat("yyy-MM-dd HH:mm:ss");
                String time = editable.toString();
                try {
                    long timeStart = format.parse(time).getTime();
                    Log.v(TAG, "time start: " + timeStart);
                    mTilTime.setErrorEnabled(false);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }));

        mEtContext.addTextChangedListener(new SMSWatcher(new SMSWatcher.TextChangeListener() {
            @Override
            public void onChange(Editable editable) {
                if (editable.toString().length() < 140) {
                    mTilContent.setErrorEnabled(false);
                }
            }
        }));
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public String getSystemDefaultSMS() {
        return Telephony.Sms.getDefaultSmsPackage(this);
    }

    public void setDefaultSMS(String packageName) {
        //TODO
    }

    public void insertSMS(View view) {
        if (Build.VERSION.SDK_INT >= 20) {
            //TODO
            if (!getPackageName().equals(getSystemDefaultSMS())) {
                setDefaultSMS(getPackageName());
            }
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat("yyy-MM-dd HH:mm:ss");
            String time = mEtTime.getText().toString();

            ContentValues values = new ContentValues();
            long timeStart = format.parse(time).getTime();
            values.put("date", new Long(timeStart));
            values.put("address", mEtPhone.getText().toString());
            values.put("body", mEtContext.getText().toString());
            values.put("type", "2");
            values.put("read", "1");//"1"means read already
            System.out.println(mEtPhone.getText().toString() + "----------------");

            if (mEtPhone.getText().toString().isEmpty()) {
                Toast.makeText(this, "请输入电话号码", Toast.LENGTH_SHORT).show();
                throw new EmptyContentException("Phone Number Empty!");
            }

            if (mEtContext.getText().toString().isEmpty()) {
                Toast.makeText(MainActivity.this, "请输入内容", Toast.LENGTH_SHORT).show();
                throw new EmptyContentException("Text Content Empty!");
            }

            getContentResolver().insert(Uri.parse("content://sms/inbox"), values);
            Toast.makeText(this, "短信插入成功，部分手机的收件箱有延迟，请等候", Toast.LENGTH_SHORT).show();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (EmptyContentException e) {
            e.printStackTrace();
        }
    }


}
