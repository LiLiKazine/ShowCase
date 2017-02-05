package kazine.lili.makesms;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * Created by LiLi
 * lilikazine@gmail.com
 */

public class SMSWatcher implements TextWatcher {

    public interface TextChangeListener {
        void onChange(Editable editable);
    }

    private TextChangeListener mChangeListener;

    public SMSWatcher(TextChangeListener mChangeListener) {
        this.mChangeListener = mChangeListener;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        mChangeListener.onChange(editable);
    }
}
