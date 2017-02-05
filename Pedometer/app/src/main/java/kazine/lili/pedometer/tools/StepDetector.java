package kazine.lili.pedometer.tools;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import kazine.lili.pedometer.CountDownTimer;

import java.util.Timer;
import java.util.TimerTask;

import kazine.lili.devutils.LogUtils;

/**
 * Created by LiLi
 * lilikazine@gmail.com
 */
public class StepDetector implements SensorEventListener {
    public StepDetector(Context context) {
        super();
    }
    private final String TAG = getClass().getSimpleName();

    //三轴数据
    final int valueNum = 5;
    //计算与植物的波峰波谷差值
    float[] tempValue = new float[valueNum];
    int tempCount = 0;
    boolean isDirectionUp = false;
    //持续上升次数
    int constantUpCount = 0;
    //上一点的持续上升次数，记录波峰的上升次数
    int formerConstantUpCount = 0;
    //上一点上升true下降false
    boolean previousStatus = false;
    float peak = 0;
    float through = 0;
    long timeOfPeak = 0;
    long formerTimeOfPeak = 0;
    long currentTime = 0;

    float sensorValueNew = 0;
    float sensorValueOld = 0;

    //动态阈值需要动态的数据，这个值用于这些动态数据的阈值
    final float initialValue = (float) 1.7;
    //初始阈值
    float threshold = (float) 2.0;

    //初始范围
    float minValue = 11f;
    float maxValue = 19.6f;

    /*
    * 0-准备计时
    * 1-计时中
    * 2-正常计步中*/
    private int CountTimeState = 0;

    public static int CURRENT_STEP = 0;
    public static int TEMP_STEP = 0;
    private int lastStep = -1;

    //用x y z轴三个维度算出的平均值
    public static float average = 0;
    private Timer timer;

    //3.5s内不显示计步，屏蔽细微波动
    private long duration = 3500;
    private TimeCount time;

    OnSensorChangeListener onSensorChangeListener;

    public interface OnSensorChangeListener {
        void onChange();
    }

    public OnSensorChangeListener getOnSensorChangeListener() {
        return onSensorChangeListener;
    }

    public void setOnSensorChangeListener(OnSensorChangeListener listener) {
        this.onSensorChangeListener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;
        synchronized (this) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                countStep(sensorEvent);
            }
        }
    }

    synchronized private void countStep(SensorEvent event) {
        average = (float) Math.sqrt(Math.pow(event.values[0], 2)
                + Math.pow(event.values[1], 2) + Math.pow(event.values[2], 2));
        detectSteps(average);
    }

    /*
    * 传入sensor中的数据
    * 如果检测到波峰，且符合时间差及阈值条件，则判定为一步
    * 符合时间差条件，波峰波谷差值大于initialValue,则将差值纳入阈值的计算中*/
    private void detectSteps(float value) {
        if (sensorValueOld == 0) {
            sensorValueOld = value;
        } else {
            if (DetectorPeak(value, sensorValueOld)) {
                formerTimeOfPeak = timeOfPeak;
                currentTime = System.currentTimeMillis();

                if (currentTime - formerTimeOfPeak >= 200
                        && (peak - through >= threshold) && (currentTime - formerTimeOfPeak) <= 2000) {
                    timeOfPeak = currentTime;
                    //更新界面
                    promoteStep();
                }
                if (currentTime - formerTimeOfPeak >= 200
                        && (peak - through >= initialValue)) {
                    timeOfPeak = currentTime;
                    threshold = ReCalThreshold(peak - through);
                }
            }
        }
        sensorValueOld = value;
    }

    /*
    * 计算阈值
    * 通过波峰波谷差值计算
    * 记录4个值，存入temp数组中
    * 将数组传入函数averageValue中计算阈值
    * */
    private float ReCalThreshold(float value) {
        float tempThread = threshold;
        if (tempCount < valueNum) {
            tempValue[tempCount] = value;
            tempCount++;
        } else {
            tempThread = averageValue(tempValue, valueNum);
            for (int i=1;i<valueNum;i++) {
                tempValue[i - 1] = tempValue[i];
            }
            tempValue[valueNum - 1] = value;
        }
        return tempThread;
    }

    /*
    * 梯度化阈值
    * 计算数组均值
    * 通过均值将阈值梯度化在一个范围里*/
    public float averageValue(float value[], int n) {
        float average = 0;
        for (int i = 0; i < n; i++) {
            average += value[i];
        }
        average = average / valueNum;
        if (average >= 8) {
            LogUtils.d(TAG, "above 8.");
            average = (float) 4.3;
        } else if ((average >= 7 && average < 8)) {
            LogUtils.d(TAG, "7-8");
            average = (float) 3.3;
        } else if (average >= 4 && average < 7) {
            LogUtils.d(TAG, "4-7");
            average = (float) 2.3;
        } else if (average >= 3 && average < 4) {
            LogUtils.d(TAG, "3-4");
            average = (float) 2.0;
        } else {
            LogUtils.d(TAG, "else");
            average = (float) 1.7;
        }
        return average;
    }

    private void promoteStep() {
        if (CountTimeState == 0) {
            //开启计时器
            time = new TimeCount(duration, 700);
            time.start();
            CountTimeState = 1;
            LogUtils.d(TAG, "start timer.");
        } else if (CountTimeState == 1) {
            TEMP_STEP++;
            LogUtils.d(TAG, "counting steps, TEMP_STEP: " + TEMP_STEP);
        } else if (CountTimeState == 2) {
            CURRENT_STEP++;
            if (onSensorChangeListener != null) {
                onSensorChangeListener.onChange();
            }
        }

    }

    /*
    * 检测波峰，条件
    * 目前点为下降趋势，isDirectionUp为false
    * 之前的点为上升趋势，previousStatus
    * 到波峰为止，持续上升大于等于2次
    * 波峰值大于1.2g，小于2g
    *
    * 记录波谷值
    * 出现步子的地方，波谷的下一个就是波峰，有比较明显的特征以及 差值
    * 记录每次的波谷值，有助于和下次波峰做对比*/
    private boolean DetectorPeak(float newValue, float oldValue) {
        previousStatus = isDirectionUp;
        if (newValue >= oldValue) {
            isDirectionUp = true;
            constantUpCount++;
        } else {
            formerConstantUpCount = constantUpCount;
            constantUpCount = 0;
            isDirectionUp = false;
        }
        LogUtils.d(TAG, "old value: " + oldValue);
        if (!isDirectionUp && previousStatus && (formerConstantUpCount >= 2
                && (oldValue >= minValue && oldValue < maxValue))) {
            peak = oldValue;
            return true;
        } else if (!previousStatus && isDirectionUp) {
            through = oldValue;
            return false;
        } else {
            return false;
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    class TimeCount extends CountDownTimer{
        public TimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }


        @Override
        public void onTick(long l) {
            if (lastStep == TEMP_STEP) {
                LogUtils.d(TAG, "onTick timer out.");
                time.cancel();
                CountTimeState = 0;
                lastStep = -1;
                TEMP_STEP = 0;
            } else {
                lastStep = TEMP_STEP;
            }
        }

        @Override
        public void onFinish() {
            time.cancel();
            CURRENT_STEP += TEMP_STEP;
            lastStep = -1;
            LogUtils.d(TAG, "timer closed normally.");
            timer = new Timer(true);
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    if (lastStep == CURRENT_STEP) {
                        timer.cancel();
                        CountTimeState = 0;
                        lastStep = -1;
                        TEMP_STEP = 0;
                        LogUtils.d(TAG, "stop count steps: " + CURRENT_STEP);
                    } else {
                        lastStep = CURRENT_STEP;
                    }
                }
            };
            timer.schedule(task, 0, 2000);
            CountTimeState = 2;
        }
    }
}
