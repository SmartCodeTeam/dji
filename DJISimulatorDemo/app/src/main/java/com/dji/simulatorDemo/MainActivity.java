package com.dji.simulatorDemo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getName();

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    //以下のパラメータでスピードを変更
    private static final float PITCH_CONTROLL_SPEED = 0.3f;
    private static final float ROLL_CONTROLL_SPEED = 0.3f;
    private static final float YAW_CONTROLL_SPEED = 2.5f;
    private static final float VERTICAL_THROTTLE_SPEED = 0.2f;


    private FlightController mFlightController;
    protected TextView mConnectStatusTextView;
    private Button mBtnEnableVirtualStick;
    private Button mBtnDisableVirtualStick;
    private ToggleButton mBtnSimulator;
    private Button mBtnTakeOff;
    private Button mBtnLand;
    private ImageButton mBtnRight;
    private ImageButton mBtnLeft;
    private ImageButton mBtnForward;
    private ImageButton mBtnBack;
    private ImageButton mBtnUp;
    private ImageButton mBtnDown;
    private ImageButton mBtnPause;
    private ImageButton mBtnTurnRight;
    private ImageButton mBtnTurnLeft;

    private TextView mTextView;
    private TextView mTextView2;

    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;

    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;
    Timer timer ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_main);

        initUI();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);


        Timer timer = new Timer();
        timer.schedule(new SampleTask(), 1000, 1000);
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("パーミッションが足りていません。");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast("登録中です。 少しお待ちください...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showToast("登録が完了しました。");
                            } else {
                                showToast("sdkの登録に失敗しました。ネットワークを確認してください。");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
                            Log.d(TAG, String.format("onProductChanged oldProduct:%s, newProduct:%s", oldProduct, newProduct));
                        }
                    });
                }
            });
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
        }
    };

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTitleBar() {
        if (mConnectStatusTextView == null) return;
        boolean ret = false;
        BaseProduct product = DJISimulatorApplication.getProductInstance();
        if (product != null) {
            if (product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(DJISimulatorApplication.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if (product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) product;
                    if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if (!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        updateTitleBar();
        initFlightController();
        loginAccount();

    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view) {
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        if (null != mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask.cancel();
            mSendVirtualStickDataTask = null;
            mSendVirtualStickDataTimer.cancel();
            mSendVirtualStickDataTimer.purge();
            mSendVirtualStickDataTimer = null;
        }
        super.onDestroy();
    }

    private void loginAccount() {

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "ログイン");
                    }

                    @Override
                    public void onFailure(DJIError error) {
                        showToast("ログインエラー:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {

        Aircraft aircraft = DJISimulatorApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            showToast("接続解除");
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            mFlightController.setOnboardSDKDeviceDataCallback(new FlightController.OnboardSDKDeviceDataCallback() {
                @Override
                public void onReceive(byte[] bytes) {
                    showToast(new String(bytes));
                    StringBuffer sb = new StringBuffer();
                    sb.append(new String(bytes)).append("\n");
                    final String onBoardMes = sb.toString();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            mTextView2.setText(onBoardMes);
                        }
                    });
                }
            });
//            mFlightController.setReceiveExternalDeviceDataCallback(new FlightControllerReceivedDataFromExternalDeviceCallback() {
//                @Override
//                public void onResult(byte[] data) {
//                }
//            });
            mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull final FlightControllerState flightControllerState) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            float alt = flightControllerState.getHomePointAltitude();
                            float velx = flightControllerState.getVelocityX();
                            float vely = flightControllerState.getVelocityY();
                            float velz = flightControllerState.getVelocityZ();
                            float ush = flightControllerState.getUltrasonicHeightInMeters();
                            mTextView.setText("高度1 : " + alt + "\n" + "高度2 : " + ush + "\n" + "x速度 : " + velx + "\n" + "y速度 : " + vely + "\n" + "z速度 : " + velz);
                        }
                    });
                }

            });
            mFlightController.getSimulator().setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(final SimulatorState stateData) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            String yaw = String.format("%.2f", stateData.getYaw());
                            String pitch = String.format("%.2f", stateData.getPitch());
                            String roll = String.format("%.2f", stateData.getRoll());
                            String positionX = String.format("%.2f", stateData.getPositionX());
                            String positionY = String.format("%.2f", stateData.getPositionY());
                            String positionZ = String.format("%.2f", stateData.getPositionZ());

                            mTextView.setText("Yaw : " + yaw + ", Pitch : " + pitch + ", Roll : " + roll + "\n" + ", PosX : " + positionX +
                                    ", PosY : " + positionY +
                                    ", PosZ : " + positionZ);
                        }
                    });
                }
            });
        }
    }

    private void initUI() {

        mBtnEnableVirtualStick = (Button) findViewById(R.id.btn_enable_virtual_stick);
        mBtnDisableVirtualStick = (Button) findViewById(R.id.btn_disable_virtual_stick);
        mBtnTakeOff = (Button) findViewById(R.id.btn_take_off);
        mBtnLand = (Button) findViewById(R.id.btn_land);
        mBtnRight = (ImageButton) findViewById(R.id.right);
        mBtnLeft = (ImageButton) findViewById(R.id.left);
        mBtnForward = (ImageButton) findViewById(R.id.forward);
        mBtnBack = (ImageButton) findViewById(R.id.back);
        mBtnUp = (ImageButton) findViewById(R.id.up);
        mBtnDown = (ImageButton) findViewById(R.id.down);
        mBtnPause = (ImageButton) findViewById(R.id.pause);
        mBtnTurnRight = (ImageButton) findViewById(R.id.turn_right);
        mBtnTurnLeft = (ImageButton) findViewById(R.id.turn_left);


        mBtnSimulator = (ToggleButton) findViewById(R.id.btn_start_simulator);
        mTextView = (TextView) findViewById(R.id.textview_simulator);
        mTextView2 = (TextView) findViewById(R.id.textview_simulator2);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);


        mBtnEnableVirtualStick.setOnClickListener(this);
        mBtnDisableVirtualStick.setOnClickListener(this);
        mBtnTakeOff.setOnClickListener(this);
        mBtnLand.setOnClickListener(this);
        mBtnRight.setOnClickListener(this);
        mBtnLeft.setOnClickListener(this);
        mBtnForward.setOnClickListener(this);
        mBtnBack.setOnClickListener(this);
        mBtnUp.setOnClickListener(this);
        mBtnDown.setOnClickListener(this);
        mBtnPause.setOnClickListener(this);
        mBtnTurnRight.setOnClickListener(this);
        mBtnTurnLeft.setOnClickListener(this);


        mBtnSimulator.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {

                    mTextView.setVisibility(View.VISIBLE);

                    if (mFlightController != null) {

                        mFlightController.getSimulator()
                                .start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10),
                                        new CommonCallbacks.CompletionCallback() {
                                            @Override
                                            public void onResult(DJIError djiError) {
                                                if (djiError != null) {
                                                    showToast(djiError.getDescription());
                                                } else {
                                                    showToast("シュミレータ起動");
                                                }
                                            }
                                        });
                    }

                } else {

                    mTextView.setVisibility(View.INVISIBLE);
                    if (mFlightController != null) {
                        mFlightController.getSimulator()
                                .stop(new CommonCallbacks.CompletionCallback() {
                                          @Override
                                          public void onResult(DJIError djiError) {
                                              if (djiError != null) {
                                                  showToast(djiError.getDescription());
                                              } else {
                                                  showToast("シュミレータ終了");
                                              }
                                          }
                                      }
                                );
                    }
                }
            }
        });

//        mScreenJoystickRight.setJoystickListener(new OnScreenJoystickListener() {
//
//            @Override
//            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
//                if (Math.abs(pX) < 0.02) {
//                    pX = 0;
//                }
//
//                if (Math.abs(pY) < 0.02) {
//                    pY = 0;
//                }
//
//                float pitchJoyControlMaxSpeed = 10;
//                float rollJoyControlMaxSpeed = 10;
//
//                mPitch = (float) (pitchJoyControlMaxSpeed * pX);
//
//                mRoll = (float) (rollJoyControlMaxSpeed * pY);
//
//                if (null == mSendVirtualStickDataTimer) {
//                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
//                    mSendVirtualStickDataTimer = new Timer();
//                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
//                }
//
//            }
//
//        });


//        mScreenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {
//
//            @Override
//            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
//                if (Math.abs(pX) < 0.02) {
//                    pX = 0;
//                }
//
//                if (Math.abs(pY) < 0.02) {
//                    pY = 0;
//                }
//                float verticalJoyControlMaxSpeed = 2;
//                float yawJoyControlMaxSpeed = 30;
//
//                mYaw = (float) (yawJoyControlMaxSpeed * pX);
//                mThrottle = (float) (verticalJoyControlMaxSpeed * pY);
//
//                if (null == mSendVirtualStickDataTimer) {
//                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
//                    mSendVirtualStickDataTimer = new Timer();
//                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
//                }
//
//            }
//        });

    }


    //FlightControllerの各種パラメータをセットする

    public void setFlightControllerData(float yaw, float pitch, float roll, float throttle) {

        mYaw = yaw;
        mPitch = pitch;
        mRoll = roll;
        mThrottle = throttle;

        if (null == mSendVirtualStickDataTimer)

        {
            mSendVirtualStickDataTask = new SendVirtualStickDataTask();
            mSendVirtualStickDataTimer = new Timer();
            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
        }

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btn_enable_virtual_stick:
                if (mFlightController != null) {

                    mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                showToast("バーチャルスティックを有効化しました。");
                            }
                        }
                    });
                    mFlightController.sendDataToOnboardSDKDevice(new byte[12], new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {
                            showToast("onBoardにデータ転送");
                        }
                    });

                } else {
                    showToast("機体が接続されていないか、コントローラを取得できていません。");

                }
                break;

            case R.id.btn_disable_virtual_stick:

                if (mFlightController != null) {
                    mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                showToast("バーチャルスティックを無効にしました。");
                            }
                        }
                    });
                } else {
                    showToast("機体が接続されていないか、コントローラを取得できていません。");
                }
                break;

            case R.id.btn_take_off:
                if (mFlightController != null) {
                    mFlightController.startTakeoff(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        showToast(djiError.getDescription());
                                    } else {
                                        showToast("テイクオフ!!!!!!!!!!!!!!!!!");
                                    }
                                }
                            }
                    );
                } else {
                    showToast("機体が接続されていないか、コントローラを取得できていません。");
                }

                break;

            case R.id.btn_land:
                if (mFlightController != null) {

                    mFlightController.startLanding(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        showToast(djiError.getDescription());
                                    } else {
                                        showToast("着陸を開始しました。");
                                    }
                                }
                            }
                    );

                } else {
                    showToast("機体が接続されていないか、コントローラを取得できていません。");
                }

                break;

            case R.id.right:
                setFlightControllerData(0, 0, ROLL_CONTROLL_SPEED, 0);
                showToast("RIGHT");
                break;

            case R.id.left:
                setFlightControllerData(0, 0, -ROLL_CONTROLL_SPEED, 0);
                showToast("LEFT");
                break;

            case R.id.forward:
                setFlightControllerData(0, -PITCH_CONTROLL_SPEED, 0, 0);
                showToast("FORWARD");

                break;

            case R.id.back:
                setFlightControllerData(0, PITCH_CONTROLL_SPEED, 0, 0);
                showToast("BACK");

                break;

            case R.id.up:
                setFlightControllerData(0, 0, 0, VERTICAL_THROTTLE_SPEED);
                showToast("UP");

                break;
            case R.id.down:
                setFlightControllerData(0, 0, 0, -VERTICAL_THROTTLE_SPEED);
                showToast("DOWN");
                break;

            case R.id.turn_left:
                setFlightControllerData(-YAW_CONTROLL_SPEED, 0, 0, 0);
                showToast("TURN_LEFT");
                break;

            case R.id.turn_right:
                setFlightControllerData(YAW_CONTROLL_SPEED, 0, 0, 0);
                showToast("TURN_RIGHT");
                break;

            case R.id.pause:
                setFlightControllerData(0, 0, 0, 0);
                showToast("PAUSE");

                break;

            default:
                break;
        }
    }

    // VirtualFlightControlDataに入力
    // https://developer.dji.com/iframe/mobile-sdk-doc/android/reference/dji/common/flightcontroller/DJIVirtualStickFlightControlData.html

    class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {

            if (mFlightController != null) {
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        }
                );
            }
        }
    }

    public class SampleTask extends TimerTask {
        public void run() {
            if (mFlightController != null) {

                mFlightController.sendDataToOnboardSDKDevice(new byte[12], new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError error) {
                        showToast("data request");
                    }
                });
            }
        }
    }

}
