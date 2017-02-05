package kazine.lili.mydownloader.downloader;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.app.FragmentActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static kazine.lili.mydownloader.downloader.Constants.TAG_HOOK;

/**
 * Created by LiLi
 * lilikazine@gmail.com
 */

public class Downloader {

    private Holder holder = null;

    private Map<Integer, Call> taskMap = new ConcurrentHashMap<>();

    private Map<Integer, MessageListener> messageMap = new ConcurrentHashMap<>();

    private Map<Integer, ResponseCallBack> finishMap = new ConcurrentHashMap<>();

    private Map<Integer, FailureCallBack> brokenMap = new ConcurrentHashMap<>();

    public interface MessageListener {
        void handleMessage(@NonNull Message message);
    }

    public interface ResponseCallBack {
        void onResponse(ResponseData response);
    }

    public interface FailureCallBack {
        void onFailure(Exception e);
    }

    public class Register {
        private Integer id;

        public Register(@NonNull Integer id) {
            this.id = id;
        }

        @MainThread
        public Downloader.Builder assign(Call call) {
            taskMap.put(id, call);
            return new Builder(id);
        }

    }

    public class Builder {
        private Integer id;

        public Builder(@NonNull Integer id) {
            this.id = id;
        }

        @MainThread
        public Downloader.Builder handle(@NonNull MessageListener listener) {
            messageMap.put(id, listener);
            return this;
        }

        @MainThread
        public Downloader.Builder finish(@NonNull ResponseCallBack callBack) {
            finishMap.put(id, callBack);
            return this;
        }

        @MainThread
        public Downloader.Builder broken(@NonNull FailureCallBack callBack) {
            brokenMap.put(id, callBack);
            return this;
        }

        public void execute() {
            executeCall(id);
        }

    }

    private class Holder {
        private Integer id;
        private Object object;

        public Holder(@NonNull Integer id, @NonNull Object object) {
            this.id = id;
            this.object = object;
        }
    }

    private static final Integer ID_ACTIVITY = 0x65533;

    private static final Integer ID_FRAGMENT_ACTIVITY = 0x65534;

    private static final Integer ID_FRAGMENT = 0x65535;

    private static final Integer ID_SUPPORT_FRAGMENT = 0x65536;


    private void registerHookToContext(@NonNull Activity activity) {
        FragmentManager manager = activity.getFragmentManager();

        HookFragment hookFragment = (HookFragment) manager.findFragmentByTag(TAG_HOOK);
        if (hookFragment == null) {
            hookFragment = new HookFragment();
            manager.beginTransaction()
                    .add(hookFragment, TAG_HOOK)
                    .commitAllowingStateLoss();
        }
    }

    private void registerHookToContext(@NonNull FragmentActivity activity) {
        android.support.v4.app.FragmentManager manager = activity.getSupportFragmentManager();

        HookSupportFragment hookSupportFragment = (HookSupportFragment) manager.findFragmentByTag(TAG_HOOK);
        if (hookSupportFragment == null) {
            hookSupportFragment = new HookSupportFragment();
            manager.beginTransaction()
                    .add(hookSupportFragment, TAG_HOOK)
                    .commitAllowingStateLoss();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void registerHookToContext(@NonNull android.app.Fragment fragment) {
        FragmentManager manager = fragment.getChildFragmentManager();

        HookFragment hookFragment = (HookFragment) manager.findFragmentByTag(TAG_HOOK);
        if (hookFragment == null) {
            hookFragment = new HookFragment();
            manager.beginTransaction()
                    .add(hookFragment, TAG_HOOK)
                    .commitAllowingStateLoss();
        }
    }

    private void registerHookToContext(@NonNull android.support.v4.app.Fragment fragment) {
        android.support.v4.app.FragmentManager manager = fragment.getChildFragmentManager();

        HookSupportFragment hookSupportFragment = (HookSupportFragment) manager.findFragmentByTag(TAG_HOOK);
        if (hookSupportFragment == null) {
            hookSupportFragment = new HookSupportFragment();
            manager.beginTransaction()
                    .add(hookSupportFragment, TAG_HOOK)
                    .commitAllowingStateLoss();
        }
    }


    /**
     * 获取上下文环境的生命周期
     */
    public static class HookFragment extends Fragment {
        protected boolean postEnable = true;

        @Override
        public void onStop() {
            super.onStop();
            if (postEnable) {
                Message message = new Message();
                message.what = Constants.MESSAGE_STOP;
                post(message);
            }
        }
    }

    public static class HookSupportFragment extends android.support.v4.app.Fragment{
        protected boolean postEnable = true;

        @Override
        public void onStop() {
            super.onStop();
            if (postEnable) {
                Message message = new Message();
                message.what = Constants.MESSAGE_STOP;
                post(message);
            }
        }
    }

    @MainThread
    public static Register with(@NonNull Activity activity) {
        getInstance().registerHookToContext(activity);

        return getInstance().buildRegister(activity);
    }

    @MainThread
    public static Register with(@NonNull FragmentActivity activity) {
        getInstance().registerHookToContext(activity);

        return getInstance().buildRegister(activity);
    }

    @MainThread
    public static Register with(@NonNull Fragment fragment) {
        getInstance().registerHookToContext(fragment);

        return getInstance().buildRegister(fragment);
    }

    @MainThread
    public static Register with(@NonNull android.support.v4.app.Fragment fragment) {
        getInstance().registerHookToContext(fragment);

        return getInstance().buildRegister(fragment);
    }


    @WorkerThread
    public static void post(@NonNull Message message) {
        getInstance().handler.sendMessage(message);
    }

    public static class TaskHolder {
        public static final Downloader INSTANCE = new Downloader();
    }
    private static Downloader getInstance() {
        return TaskHolder.INSTANCE;
    }

    private static AtomicInteger count = new AtomicInteger(0);

    private Handler handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == Constants.MESSAGE_FINISH && message.obj instanceof Holder) {
                final Holder result = (Holder) message.obj;

                taskMap.remove(result.id);
                messageMap.remove(result.id);
                brokenMap.remove(result.id);

                final ResponseCallBack callBack = finishMap.remove(result.id);
                if (callBack != null) {
                    callBack.onResponse((ResponseData) result.object);
                }
                getInstance().dispatchUnregister();
            } else if (message.what == Constants.MESSAGE_BROKEN && message.obj instanceof Holder) {
                final Holder result = (Holder) message.obj;

                taskMap.remove(result.id);
                messageMap.remove(result.id);
                finishMap.remove(result.id);

                final FailureCallBack callBack = brokenMap.remove(result.id);
                if (callBack != null) {
                    callBack.onFailure((Exception) result.object);
                }
                getInstance().dispatchUnregister();
            } else if (message.what == Constants.MESSAGE_STOP) {
                resetHolder();
                taskMap.clear();
                messageMap.clear();
                finishMap.clear();
                brokenMap.clear();
            } else {
                for (MessageListener listener : messageMap.values()) {
                    listener.handleMessage(message);
                }
            }
            return true;
        }
    });

    private Register buildRegister(@NonNull Activity activity) {
        holder = new Holder(ID_ACTIVITY, activity);
        return new Register(count.getAndIncrement());
    }

    private Register buildRegister(@NonNull FragmentActivity activity) {
        holder = new Holder(ID_FRAGMENT_ACTIVITY, activity);
        return new Register(count.getAndIncrement());
    }


    private Register buildRegister(@NonNull android.app.Fragment fragment) {
        holder = new Holder(ID_FRAGMENT, fragment);
        return new Register(count.getAndIncrement());
    }

    private Register buildRegister(@NonNull android.support.v4.app.Fragment fragment) {
        holder = new Holder(ID_SUPPORT_FRAGMENT, fragment);
        return new Register(count.getAndIncrement());
    }

    private void executeCall(final Integer id) {
        if (taskMap.containsKey(id)) {
            final Message message = Message.obtain();
            try {
                Call call = taskMap.get(id);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        message.what = Constants.MESSAGE_BROKEN;
                        message.obj = new Holder(id, e);
                        post(message);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        message.what = Constants.MESSAGE_FINISH;
                        message.obj = new Holder(id, new ResponseData.Builder()
                                .code(response.code())
                                .message(response.message())
                                .body(response.body().string())
                                .build());
                        post(message);
                    }
                });
            } catch (Exception e) {
                message.what = Constants.MESSAGE_BROKEN;
                message.obj = new Holder(id, e);
                post(message);
                e.printStackTrace();
            }
        }
    }

    private void dispatchUnregister() {
        if (holder == null || taskMap.size() > 0) {
            return;
        }

        if (holder.id.equals(ID_ACTIVITY) && holder.object instanceof Activity) {
            unregisterHookToContext((Activity) holder.object);
        }
        else if (holder.id.equals(ID_FRAGMENT_ACTIVITY) && holder.object instanceof FragmentActivity) {
            unregisterHookToContext((FragmentActivity) holder.object);
        }
        else if (holder.id.equals(ID_FRAGMENT) && holder.object instanceof Fragment) {
            unregisterHookToContext((Fragment) holder.object);
        }
        else if (holder.id.equals(ID_SUPPORT_FRAGMENT) && holder.object instanceof android.support.v4.app.Fragment) {
            unregisterHookToContext((android.support.v4.app.Fragment) holder.object);
        }

        resetHolder();
    }

    private void unregisterHookToContext(@NonNull Activity activity) {
        FragmentManager manager = activity.getFragmentManager();

        HookFragment hookFragment = (HookFragment) manager.findFragmentByTag(TAG_HOOK);
        if (hookFragment != null) {
            hookFragment.postEnable = false;
            manager.beginTransaction()
                    .remove(hookFragment)
                    .commitAllowingStateLoss();
        }
    }

    private void unregisterHookToContext(@NonNull FragmentActivity activity) {
        android.support.v4.app.FragmentManager manager = activity.getSupportFragmentManager();

        HookSupportFragment hookSupportFragment = (HookSupportFragment) manager.findFragmentByTag(TAG_HOOK);
        if (hookSupportFragment != null) {
            hookSupportFragment.postEnable = false;
            manager.beginTransaction()
                    .remove(hookSupportFragment)
                    .commitAllowingStateLoss();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void unregisterHookToContext(@NonNull Fragment fragment) {
        FragmentManager manager = fragment.getChildFragmentManager();

        HookFragment hookFragment = (HookFragment) manager.findFragmentByTag(TAG_HOOK);
        if (hookFragment != null) {
            hookFragment.postEnable = false;
            manager.beginTransaction()
                    .remove(hookFragment)
                    .commitAllowingStateLoss();
        }
    }

    private void unregisterHookToContext(@NonNull android.support.v4.app.Fragment fragment) {
        android.support.v4.app.FragmentManager manager = fragment.getChildFragmentManager();

        HookSupportFragment hookSupportFragment = (HookSupportFragment) manager.findFragmentByTag(TAG_HOOK);
        if (hookSupportFragment != null) {
            hookSupportFragment.postEnable = false;
            manager.beginTransaction()
                    .remove(hookSupportFragment)
                    .commitAllowingStateLoss();
        }
    }

    private void resetHolder() {
        if (holder == null) {
            return;
        }
        holder.id = 0;
        holder.object = null;
        holder = null;
    }

}
