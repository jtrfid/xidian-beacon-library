package edu.xidian.NearestBeacon;

import java.util.Collection;

import org.altbeacon.beacon.AltBeaconParser;
import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BleNotAvailableException;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.logging.LogManager;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;

/**
 * beacon基站搜索类， 用以搜索beacon基站，开发者 通过实现callback回调接口
 * {@link #setNearestBeaconListener(int, OnNearestBeaconListener)},
 * 获取展品定位beacon或游客定位beacon
 * 
 * <pre>
 * {@code
 * public class FollowGuideFragment extends Fragment implements OnNearestBeaconListener{
 * 	private BeaconSearcher beaconSearcher;
 * 	protected void onCreate(Bundle savedInstanceState) {
 * 		super.onCreate(savedInstanceState);
 * 		setContentView(R.layout.activity_main);
 * 		//获取beaconSearcher唯一实例
 * 		beaconSearcher = BeaconSearcher.getInstance(activity);
 *       // 设定用于展品定位的最小停留时间(ms)
 * 	    beaconSearcher.setMin_stay_milliseconds(5000);
 *       // 设定用于展品定位的最小距离(m)
 * 	    beaconSearcher.setExhibit_distance(5.0) {
 *       //设置获取距离最近的beacon类型
 *       // NearestBeacon.GET_LOCATION_BEACON：游客定位beacon。可以不用设置上述的最小停留时间和最小距离
 *       // NearestBeacon.GET_EXHIBIT_BEACON：展品定位beacon
 *       beaconSearcher.setNearestBeaconType(NearestBeacon.GET_EXHIBIT_BEACON);
 * 		//设置beacon监听器
 * 		beaconSearcher.setNearestBeaconListener(this);
 *       //打开beacon搜索器，开始搜索距离最近的Beacon
 * 		beaconSearcher.openSearcher();
 * 	}
 * 
 *   // 实现OnNearestBeaconListener接口的回调函数
 * 	public void getNearestBeacon(int type,Beacon beacon) {
 *      // 自定义获取展品定位或游客定位beacon的操作
 *      // type = NearestBeacon.GET_LOCATION_BEACON:游客定位beacon; NearestBeacon.GET_EXHIBIT_BEACON:展品定位beacon
 *      // beacon的UUID，Major，Minor通过下列方法获得
 *      // beacon.getId1(); beacon.getId2(); beacon.getId3();  
 *   }
 * 	
 * 	
 * 	//destroy the fragment, close the beaconSearcher.
 * 	public void onDestroy() {
 * 		super.onDestroy();
 * 		//关闭beacon搜索器，可以不关闭，因为程序的其他部分可能还使用beaconSearcher。
 * 		beaconSearcher.closeSearcher();
 * 	}
 * }
 * 
 *  <b>注：
 *  (1) 距离计算模型文件（resources/model-distance-calculations.json）含default模型，必须拷贝至客户端app的assets目录中，否则不能进行距离估值。
 *  (2) 可以在整个程序中使用一对openSearcher()和closeSearcher()绑定和解绑BeaconService。
 *  (3) 使用BackgroundPowerSaver(),自动判别程序的前后台运行，使BeaconService工作在相应的前台或后台模式，节省电池的使用。
 *      因此，一般不用setBackgroundMode()设置前后台模式。但是可以使用下列函数设置前后台扫描周期和扫描间隔时间，以ms为单位。
 *      setForegroundScanPeriod(),setForegroundBetweenScanPeriod()，缺省前台扫描周期1100ms,间隔0ms
 *      setBackgroundScanPeriod(),setBackgroundBetweenScanPeriod(),缺省后台扫描周期10s,间隔5minutes
 *      当beacon数量较大时，建议加大扫描周期，使在一个扫描周期中，发现尽可能多的beacon。使计算距离最近的beacon更精确
 *  (4) 在应用程序的AndroidManifest.xml中必须添加权限：</b>
 *  {@code
 *  <!--  downloaded distance models from online database:
 *        "http://data.altbeacon.org/android-distance.json"
 *     -->
 *     <uses-permission android:name="android.permission.INTERNET"/>
 *     
 *     <uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
 *     <uses-permission android:name="android.permission.BLUETOOTH"/>
 *     <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
 *  }
 *  (5) 在应用程序的AndroidManifest.xml中必须添加广播接收器和服务：</b>
 *  {@code
 *         <receiver android:name="org.altbeacon.beacon.startup.StartupBroadcastReceiver">
 * 	            <intent-filter>
 * 	                <action android:name="android.intent.action.BOOT_COMPLETED"/>
 * 	                <action android:name="android.intent.action.ACTION_POWER_CONNECTED"/>
 * 	                <action android:name="android.intent.action.ACTION_POWER_DISCONNECTED"/>
 * 	            </intent-filter>
 * 	    </receiver>
 * 
 *         <service android:enabled="true"
 *             android:exported="false"
 *             android:isolatedProcess="false"
 *             android:label="beacon"
 *             android:name=".service.BeaconService"
 *             />
 * 
 *         <service android:name=".BeaconIntentProcessor"
 *             android:enabled="true"
 *             android:exported="false"
 *             />
 *  }
 * </pre>
 */
public class BeaconSearcher {

	private final static String TAG = BeaconSearcher.class.getSimpleName();

	/**
	 * application或activity或service上下文信息
	 */
	private Context mContext;

	/**
	 * BeaconManager对象，用以搜索beacon设备
	 */
	private BeaconManager mBeaconManager;

	/**
	 * 监控的Beacon区域
	 */
	private final static Region ALL_VOLIAM_BEACONS_REGION = new Region(
			"voliam", null, null, null);

	/**
	 * 打开蓝牙的请求码
	 */
	public final static int REQUEST_ENABLE_BT = 0x101;

	/**
	 * NearestBeacon.GET_LOCATION_BEACON:获取游客定位beacon;
	 * NearestBeacon.GET_EXHIBIT_BEACON:获取展品定位beacon
	 **/
	private int mGetBeaconType = NearestBeacon.GET_LOCATION_BEACON;

	/**
	 * 距离最近的beacon对象，用于获取游客定位beacon或展品定位beacon
	 **/
	private NearestBeacon mNearestBeacon = new NearestBeacon();

	/**
	 * 距离最近的beacon监听者对象
	 */
	private OnNearestBeaconListener mOnNearestBeaconListener;

	/** 省电模式，前后台自动切换 **/
	@SuppressWarnings("unused")
	private BackgroundPowerSaver mBackgroundPowerSaver;

	/**
	 * the single instance beaconSearcher
	 */
	private static BeaconSearcher instance = null;

	/**
	 * use this method to get the beacon instance
	 * 
	 * @return beacon instance
	 * @param context
	 *            传入一个Activity或Service或application对象
	 */
	public static BeaconSearcher getInstance(Context context) {
		if (instance == null) {
			synchronized (BeaconSearcher.class) {
				if (instance == null) {
					instance = new BeaconSearcher(context);
				}
			}
		}
		return instance;
	}

	/**
	 * make the constructor private to ensure that there is only one
	 * beaconSearcher instance.
	 */
	private BeaconSearcher(Context context) {
		this.mContext = context.getApplicationContext();
		
		this.mBeaconManager = BeaconManager.getInstanceForApplication(mContext);

		// 经过测试，天津的Beacon应该是Apple的Beacon，beaconTypeCode=0215
		// 其传输帧的字节序列按照以下顺序传输，但是网络上查到2013年后的Estimote beacons也是下列的字节顺序,ok
		// mBeaconManager.getBeaconParsers().add(new
		// BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

		// 也可能是AltBeacon(即Radius)的Beacon,ok
		mBeaconManager
				.getBeaconParsers()
				.add(new AltBeaconParser()
						.setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

		// 设置距离计算模型
		DefaultDistanceCalcuator defaultDistanceCalcuator = new DefaultDistanceCalcuator(
				mContext);
		// 使用asserts/model-distance-calculations.json
		defaultDistanceCalcuator.setDefaultDistanceCalcuator(null);
		// 使用url
		// defaultDistanceCalcuator.setDefaultDistanceCalcuator("http://data.altbeacon.org/android-distance.json");

		// 设置发现beacon监听回调，看到beacon，看不到beacon; 进入，离开，临界状态
		// Specifies a class that should be called each time the BeaconService
		// sees or stops seeing a Region of beacons.
		// IMPORTANT: Only one MonitorNotifier may be active for a given
		// application. If two different activities or services set different
		// MonitorNotifier instances, the last one set will receive all the
		// notifications.
		mBeaconManager.setMonitorNotifier(mMonitorNotifier);

		// 设置测距修正回调，每个扫描周期结束，根据20秒内各beacon的RSSI平均值计算它的距离，该回调获取这些beacon的距离值
		// Specifies a class that should be called each time the BeaconService
		// gets ranging data, which is nominally once per
		// second(实际上每个扫描周期，计算一次距离) when beacons are detected.
		// IMPORTANT: Only one RangeNotifier may be active for a given
		// application. If two different activities or services set different
		// RangeNotifier instances,
		// the last one set will receive all the notifications.
		mBeaconManager.setRangeNotifier(mRangeNotifier);

		// 当程序切换到后台，BeaconService自动切换到后台模式，为了省电，蓝牙扫描频率降低；程序恢复到前台，BeaconService也跟随恢复至前台
		// simply constructing this class and holding a reference to it in your
		// custom Application
		// class will automatically cause the BeaconLibrary to save battery
		// whenever the application
		// is not visible. This reduces bluetooth power usage by about 60%
		Context appContext = this.mContext.getApplicationContext();
		mBackgroundPowerSaver = new BackgroundPowerSaver(appContext);
	}

	/** beacon消费者回调 */
	BeaconConsumer mBeaconConsumer = new BeaconConsumer() {

		/**
		 * Called when the beacon service is running and ready to accept your
		 * commands through the BeaconManager 开始查找beacon
		 * */
		@Override
		public void onBeaconServiceConnect() {
			/**
			 * Tells the BeaconService to start looking for beacons that match
			 * the passed Region object. Note that the Region's unique
			 * identifier must be retained保存 to later call the
			 * stopMonitoringBeaconsInRegion method.
			 */
			try {
				// 通知BeaconService,开始监控特定区域的Beacons，一旦检测到beacons,执行MonitorNotifier接口中的回调（进入，离开，临界）
				mBeaconManager
						.startMonitoringBeaconsInRegion(ALL_VOLIAM_BEACONS_REGION);
			} catch (RemoteException e) {
				LogManager.d(TAG, "RemoteException:" + e.toString());
			}
		}

		@Override
		public Context getApplicationContext() {
			return mContext.getApplicationContext();
		}

		@Override
		public void unbindService(ServiceConnection connection) {
			mContext.unbindService(connection);
		}

		@Override
		public boolean bindService(Intent intent, ServiceConnection connection,
				int mode) {
			return mContext.bindService(intent, connection, mode);
		}

	};

	/** 测距修正回调 */
	RangeNotifier mRangeNotifier = new RangeNotifier() {
		/**
		 * 每个扫描周期结束，根据20秒内各beacon的RSSI平均值计算它的距离，该回调获取这些beacon的距离值 Called once
		 * per second (实际上是每扫描周期) to give an estimate of the mDistance to
		 * visible beacons
		 */
		@Override
		public void didRangeBeaconsInRegion(Collection<Beacon> beacons,
				Region region) {
			// 日志记录Beacon信息
			LogManager.d(TAG,
					"didRangeBeaconsInRegion(),beacons=" + beacons.size());
			for (Beacon beacon : beacons) {
				LogManager.d(TAG, beacon.getId2()+":"+beacon.getId3() + "," + beacon.getDistance());
				LogManager.d(TAG, "Rssi="+beacon.getRssi()+",AveRssi="+beacon.getRunningAverageRssi());
			}
			Beacon beacon = mNearestBeacon.getNearestBeacon(mGetBeaconType,
					beacons);
			mOnNearestBeaconListener.getNearestBeacon(mGetBeaconType, beacon);
		}
	};

	/** 发现beacon监听回调，看到beacon，看不到beacon; 进入，离开，临界 */
	MonitorNotifier mMonitorNotifier = new MonitorNotifier() {

		/** Called when at least one beacon in a Region is visible. */
		@Override
		public void didEnterRegion(Region region) {
			LogManager
					.d(TAG,
							"didEnterRegion(),region uniqueId= "
									+ region.getUniqueId());
			/**
			 * 启动测距修正 Tells the BeaconService to start looking for beacons that
			 * match the passed Region object, and providing updates on the
			 * estimated mDistance every seconds(实际上是每个扫描周期) while beacons in
			 * the Region are visible. Note that the Region's unique identifier
			 * must be retained to later call the stopRangingBeaconsInRegion
			 * method. this will provide an update once per second with the
			 * estimated distance to the beacon in the didRAngeBeaconsInRegion
			 * method.
			 */
			try {
				mBeaconManager
						.startRangingBeaconsInRegion(ALL_VOLIAM_BEACONS_REGION);
			} catch (RemoteException e) {
				LogManager.d(TAG, "RemoteException:" + e.toString());
			}
		}

		/** Called when no beacons in a Region are visible. */
		@Override
		public void didExitRegion(Region region) {
			LogManager.d(TAG,
					"didExitRegion(),region uniqueId= " + region.getUniqueId());
			/**
			 * Tells the BeaconService to stop looking for beacons that match
			 * the passed Region object and providing mDistance information for
			 * them.
			 */
			try {
				mBeaconManager
						.stopRangingBeaconsInRegion(ALL_VOLIAM_BEACONS_REGION);
			} catch (RemoteException e) {
				LogManager.d(TAG, "RemoteException:" + e.toString());
			}
		}

		/**
		 * Called with a state value of MonitorNotifier.INSIDE when at least one
		 * beacon in a Region is visible. Called with a state value of
		 * MonitorNotifier.OUTSIDE when no beacons in a Region are visible.
		 **/
		@Override
		public void didDetermineStateForRegion(int state, Region region) {
			LogManager.d(TAG, "didDetermineStateForRegion() ,region uniqueId= "
					+ region.getUniqueId() + " state="
					+ (state == 1 ? "inside" : "outside"));
		}

	};

	/**
	 * 开始监控beacons，绑定BeaconService，如果已经绑定，在BeaconManger的bind()中忽略之。
	 * 建议在应用程序中执行一次该函数即可。
	 */
	public void openSearcher() {
		// 设置beacon消费者,绑定BeaconService,BeaconSearcher的实例以产生，即绑定BeaconService
		// 进而触发mBeaconConsumer的onBeaconServiceConnect(),开始监控特定区域的Beacons。
		mBeaconManager.bind(mBeaconConsumer);
	}

	/**
	 * <pre>
	 * 调用该方法 以关闭搜索Beacon基站，解除绑定BeaconService
	 * Unbinds an Android Activity or Service to the BeaconService.
	 * This should typically be called in the onDestroy() method.
	 */
	public void closeSearcher() {
		mBeaconManager.unbind(mBeaconConsumer);
	}

	/**
	 * 设置获取距离最近的beacon监听对象
	 * 
	 * @param listener
	 *            OnNearestBeaconListener对象
	 */
	public void setNearestBeaconListener(OnNearestBeaconListener listener) {
		this.mOnNearestBeaconListener = listener;
	}

	/**
	 * 设置获取距离最近的beacon类型
	 * 
	 * @param type
	 *            NearestBeacon.GET_LOCATION_BEACON:获取游客定位beacon;
	 *            NearestBeacon.GET_EXHIBIT_BEACON:获取展品定位beacon
	 */
	public void setNearestBeaconType(int type) {
		this.mGetBeaconType = type;
	}

	/**
	 * 获取距离最近的beacon监听接口
	 */
	public interface OnNearestBeaconListener {
		/**
		 * 实现该方法，以获取展品定位beacon或游客定位beacon<br>
		 * 其类型在setNearestBeaconListener()中设置
		 * 
		 * @param type
		 *            NearestBeacon.GET_LOCATION_BEACON:游客定位beacon;
		 *            NearestBeacon.GET_EXHIBIT_BEACON:展品定位beacon
		 * @param beacon
		 *            展品定位beacon或游客定位beacon
		 */
		public void getNearestBeacon(int type, Beacon beacon);
	}

	/**
	 * 获得当前设定用于展品定位的最小距离(m)
	 * 
	 * @return the mExhibit_distance
	 */
	public double getExhizibit_distance() {
		return mNearestBeacon.getExhizibit_distance();
	}

	/**
	 * 设定用于展品定位的最小距离(m)
	 * 
	 * @param Exhibit_distance
	 *            the mExhibit_distance to set
	 */
	public void setExhibit_distance(double Exhibit_distance) {
		mNearestBeacon.setExhibit_distance(Exhibit_distance);
	}

	/**
	 * 获得当前设定用于展品定位的最小停留时间(ms)
	 * 
	 * @return the mMin_stay_milliseconds
	 */
	public long getMin_stay_milliseconds() {
		return mNearestBeacon.getMin_stay_milliseconds();
	}

	/**
	 * 设定用于展品定位的最小停留时间(ms)
	 * 
	 * @param Min_stay_milliseconds
	 *            the mMin_stay_milliseconds to set
	 */
	public void setMin_stay_milliseconds(long Min_stay_milliseconds) {
		mNearestBeacon.setMin_stay_milliseconds(Min_stay_milliseconds);
	}

	/**
	 * Sets the duration in milliseconds of each Bluetooth LE scan cycle to look
	 * for beacons. default 1.1s = 1100ms
	 * 
	 * @param p
	 *            (ms)
	 */
	public void setForegroundScanPeriod(long p) {
		mBeaconManager.setForegroundScanPeriod(p);
		try {
			mBeaconManager.updateScanPeriods(); // 保证在下一个循环扫描周期生效
		} catch (RemoteException e) {
			LogManager.d(TAG, "RemoteException:" + e.toString());
		}
	}

	/**
	 * Sets the duration in milliseconds between each Bluetooth LE scan cycle to
	 * look for beacons. defaults 0s
	 * 
	 * @param p
	 *            (ms)
	 */
	public void setForegroundBetweenScanPeriod(long p) {
		mBeaconManager.setForegroundBetweenScanPeriod(p);
		try {
			mBeaconManager.updateScanPeriods(); // 保证在下一个循环扫描周期生效
		} catch (RemoteException e) {
			LogManager.d(TAG, "RemoteException:" + e.toString());
		}
	}

	/**
	 * Sets the duration in milliseconds of each Bluetooth LE scan cycle to look
	 * for beacons. default 10s
	 * 
	 * @param p
	 *            (ms)
	 */
	public void setBackgroundScanPeriod(long p) {
		mBeaconManager.setBackgroundBetweenScanPeriod(p);
		try {
			mBeaconManager.updateScanPeriods(); // 保证在下一个循环扫描周期生效
		} catch (RemoteException e) {
			LogManager.d(TAG, "RemoteException:" + e.toString());
		}
	}

	/**
	 * Sets the duration in milliseconds spent not scanning between each
	 * Bluetooth LE scan cycle when no ranging/monitoring clients are in the
	 * background. default 5 minutes
	 * 
	 * @param p
	 *            (ms)
	 */
	public void setBackgroundBetweenScanPeriod(long p) {
		mBeaconManager.setBackgroundBetweenScanPeriod(p);
		try {
			mBeaconManager.updateScanPeriods(); // 保证在下一个循环扫描周期生效
		} catch (RemoteException e) {
			LogManager.d(TAG, "RemoteException:" + e.toString());
		}
	}

	/**
	 * This method notifies the beacon service that the application is either
	 * moving to background mode or foreground mode.
	 * 
	 * @param backgroundMode
	 *            true indicates the app is in the background
	 */
	public void setBackgroundMode(boolean backgroundMode) {
		mBeaconManager.setBackgroundMode(backgroundMode);
	}

	/**
	 * 查看手机蓝牙是否可用,若当前状态为不可用，则默认调用意图请求打开系统蓝牙
	 */
	@SuppressLint("InlinedApi")
	public boolean checkBLEEnable() throws BleNotAvailableException {
		try {
			if (mBeaconManager.checkAvailability()) {
				// 支持ble 且蓝牙已打开
				return true;
			} else {
				// 支持ble 但蓝牙未打开
				return false;
			}
		} catch (BleNotAvailableException e) {
			// 当设备没有bluetooth或没有ble时，会产生该异常
			throw new BleNotAvailableException(
					"Bluetooth LE not supported by this device");
		}
	}

	/**
	 * 打开蓝牙
	 */
	public void enableBluetooth() {
		if (android.os.Build.VERSION.SDK_INT < 18)
			return;
		((BluetoothManager) mContext
				.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter()
				.enable();
	}
	
	/**
	 * 设置Rssi滤波模型 
	 * Default class for rssi filter/calculation implementation：RunningAverageRssiFilter.class
	 * others：ArmaRssiFilter.class
	 */
    public static void setRssiFilterImplClass(Class c) {
		BeaconManager.setRssiFilterImplClass(c);
	}
    
    /***
     * 设置ArmaRssiFilter的系数c,1Hz信号变化频率，推荐值0.1；10hz，推荐值0.25 ~ 0.5
     * 仅适用于ArmaRssiFilter
     */
    public static void setDEFAULT_ARMA_SPEED(double default_arma_speed) {
    	ArmaRssiFilter.setDEFAULT_ARMA_SPEED(default_arma_speed);
    }
    
    /**
     * 设置RunningAverageRssiFilter的采样周期，缺省是20秒(20000毫秒)
     * 即，计算该时间段内的平均RSSI（首位各去掉10%）
     * 仅适应于RunningAverageRssiFilter
     * @param newSampleExpirationMilliseconds
     */
    public static void setSampleExpirationMilliseconds(long newSampleExpirationMilliseconds) {
    	RunningAverageRssiFilter.setSampleExpirationMilliseconds(newSampleExpirationMilliseconds);
    }

}
