package kazine.lili.makesms.exceptions;

import android.util.Log;

/**
 * Created by LiLi
 * lilikazine@gmail.com
 */

public class EmptyContentException extends Exception {
    public EmptyContentException() {
    }

    public EmptyContentException(String message) {
        System.out.println(message);
    }
}
