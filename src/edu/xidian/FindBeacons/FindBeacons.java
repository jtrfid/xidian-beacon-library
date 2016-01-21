package edu.xidian.FindBeacons;

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
import org.altbeacon.beacon.service.RunningAverageRssiFilter;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;

/**
 * 查找附近的beacons
 <pre>
     // 设置FindBeacons对象
     private FindBeacons mFindBeacons;
     // 获取FindBeacons唯一实例，参数是application或activity或service上下文信息
	 mFindBeacons = FindBeacons.getInstance(this);
	 
	 // 设置前台扫描周期,ms,缺省1.1秒,1100ms
	 mFindBeacons.setForegroundScanPeriod(ms);
	 // 设置前台扫描间隔,ms，缺省0秒 
	 mFindBeacons.setForegroundBetweenScanPeriod(ms);
	 // 设置后台扫描周期,ms,缺省10秒,10000ms
	 mFindBeacons.setBackgroundScanPeriod(ms);
	 // 设置后台扫描间隔,ms,缺省5分钟,5*60*1000ms
	 mFindBeacons.setBackgroundBetweenScanPeriod(ms);
	 
	 // 开始监控(查找)beacons
	 mFindBeacons.openSearcher();
	 // 停止监控(查找)beacons
	 mFindBeacons.stopSearcher();
	 
	 // 设置获取附近所有beacons的监听对象，在每个扫描周期结束，通过该接口获取找到的所有beacons
     mFindBeacons.setBeaconsListener(mBeaconsListener);
        
     // 每次扫描周期结束，执行此回调，获取附近beacons信息
     private OnBeaconsListener mBeaconsListener = new OnBeaconsListener() {
		@Override
		public void getBeacons(Collection<Beacon> beacons) {
			// 根据需要处理传递来的beacons
		}
    	
    }; 
    
    // 查看手机蓝牙是否可用,若当前状态为不可用，则默认调用意图请求打开系统蓝牙
    mFindBeacons.checkBLEEnable();
 * 
 *  <b>注：
 *  (1) 可以在整个程序中使用一对openSearcher()和closeSearcher()绑定和解绑BeaconService。
 *  (2) 使用BackgroundPowerSaver(),自动判别程序的前后台运行，使BeaconService工作在相应的前台或后台模式，节省电池的使用。
 *      因此，一般不用setBackgroundMode()设置前后台模式。但是可以使用下列函数设置前后台扫描周期和扫描间隔时间，以ms为单位。
 *      setForegroundScanPeriod(),setForegroundBetweenScanPeriod()，缺省前台扫描周期1100ms,间隔0ms
 *      setBackgroundScanPeriod(),setBackgroundBetweenScanPeriod(),缺省后台扫描周期10s,间隔5minutes
 *      当beacon数量较大时，建议加大扫描周期，使在一个扫描周期中，发现尽可能多的beacon。使计算距离最近的beacon更精确
 *  (3) 在应用程序的AndroidManifest.xml中必须添加权限：</b>
 *  {@code
 *     <uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
 *     <uses-permission android:name="android.permission.BLUETOOTH"/>
 *     <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
 *  }
 *  (4) 在应用程序的AndroidManifest.xml中必须添加广播接收器和服务：</b>
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
public class FindBeacons {

	private final static String TAG = FindBeacons.class.getSimpleName();

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
	 * 获取附近所有beacons的监听对象,在每个扫描周期结束，通过该接口获取找到的所有beacons
	 */
	private OnBeaconsListener mBeaconsListener;

	/**
	 * 打开蓝牙的请求码
	 */
	public final static int REQUEST_ENABLE_BT = 0x101;

	/** 省电模式，前后台自动切换 **/
	@SuppressWarnings("unused")
	private BackgroundPowerSaver mBackgroundPowerSaver;

	/**
	 * the single instance for this class
	 */
	private static FindBeacons instance = null;

	/**
	 * use this method to get the beacon instance
	 * 
	 * @return the single instance for this class
	 * @param context 传入一个Activity或Service或application对象
	 */
	public static FindBeacons getInstance(Context context) {
		if (instance == null) {
			synchronized (FindBeacons.class) {
				if (instance == null) {
					instance = new FindBeacons(context);
				}
			}
		}
		return instance;
	}

	/**
	 * make the constructor private to ensure that there is only one instance.
	 */
	private FindBeacons(Context context) {
		this.mContext = context.getApplicationContext();
		
		this.mBeaconManager = BeaconManager.getInstanceForApplication(mContext);

		// 经过测试，天津的Beacon应该是Apple的Beacon，beaconTypeCode=0215
		// 其传输帧的字节序列按照以下顺序传输，但是网络上查到2013年后的Estimote beacons也是下列的字节顺序,ok
		// mBeaconManager.getBeaconParsers().add(new
		// BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

		// 也可能是AltBeacon(即Radius)的Beacon,ok
		mBeaconManager.getBeaconParsers().add(new AltBeaconParser()
						.setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

		// 设置发现beacon监听回调，看到beacon，看不到beacon; 进入，离开，临界状态
		// Specifies a class that should be called each time the BeaconService
		// sees or stops seeing a Region of beacons.
		// IMPORTANT: Only one MonitorNotifier may be active for a given
		// application. If two different activities or services set different
		// MonitorNotifier instances, the last one set will receive all the
		// notifications.
		mBeaconManager.setMonitorNotifier(mMonitorNotifier);

		// 设置测距修正回调，每个扫描周期结束，执行此回调
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

	/** beacon消费者回调接口，连接beacon service时，执行此回调 */
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
				mBeaconManager.startMonitoringBeaconsInRegion(ALL_VOLIAM_BEACONS_REGION);
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

	/** 每个扫描周期结束，测距修正回调接口 */
	RangeNotifier mRangeNotifier = new RangeNotifier() {
		/**
		 * 每个扫描周期结束，执行此回调
		 */
		@Override
		public void didRangeBeaconsInRegion(Collection<Beacon> beacons,
				Region region) {
//			// 日志记录Beacon信息
//			LogManager.d(TAG,
//					"didRangeBeaconsInRegion(),beacons=" + beacons.size());
//			for (Beacon beacon : beacons) {
//				LogManager.d(TAG, beacon.getId2()+":"+beacon.getId3() + "," + beacon.getDistance());
//				LogManager.d(TAG, "Rssi="+beacon.getRssi()+",AveRssi="+beacon.getRunningAverageRssi());
//			}
			
			// 向调用者传递找到的beacons
			mBeaconsListener.getBeacons(beacons);
		}
	};

	/** 发现beacon监听回调，看到beacon，看不到beacon; 进入，离开，临界 */
	MonitorNotifier mMonitorNotifier = new MonitorNotifier() {

		/** Called when at least one beacon in a Region is visible. */
		@Override
		public void didEnterRegion(Region region) {
			LogManager.d(TAG,"didEnterRegion(),region uniqueId= "
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
	 * 设置获取附近所有beacons的监听对象，在每个扫描周期结束，通过该接口获取找到的所有beacons
	 * 
	 * @param listener OnBeaconListener对象
	 */
	public void setBeaconsListener(OnBeaconsListener listener) {
		this.mBeaconsListener = listener;
	}

	/**
	 * 在每个扫描周期结束时，获取附近所有beacons的监听接口
	 */
	public interface OnBeaconsListener {
		/**
		 * 实现该方法，以获取附近所有beacons<br>
		 * @param beacons 附近所有beacon
		 */
		public void getBeacons(Collection<Beacon> beacons);
	}

	/**
	 * Sets the duration in milliseconds of each Bluetooth LE scan cycle to look
	 * for beacons. default 1.1s = 1100ms
	 * 
	 * @param p (ms)
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
	 * @param p (ms)
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
	 * @param p  (ms)
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
	 * @param p  (ms)
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
     * 设置RunningAverageRssiFilter的采样周期，缺省是20秒(20000毫秒)
     * 即，计算该时间段内的平均RSSI（首末各去掉10%）
     * 仅适应于RunningAverageRssiFilter
     * @param newSampleExpirationMilliseconds
     */
    public static void setSampleExpirationMilliseconds(long newSampleExpirationMilliseconds) {
    	RunningAverageRssiFilter.setSampleExpirationMilliseconds(newSampleExpirationMilliseconds);
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
				// 支持ble 但蓝牙未打开,现在打开之。
				enableBluetooth();
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
	@SuppressLint("NewApi")
	public void enableBluetooth() {
		if (android.os.Build.VERSION.SDK_INT < 18)
			return;
		((BluetoothManager) mContext
				.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter()
				.enable();
	}
	
}
