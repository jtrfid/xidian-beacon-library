package edu.xidian.NearestBeacon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.altbeacon.beacon.Beacon;

/**
 * <pre>
 * 一、展品定位
 *   展品beacon，距离最近(m)(并且小于最小距离NEAREST_DISTANCE)，逗留时间大于最小停留时间(ms)(MIN_STAY_MILLISECONDS) 
 *       NearestBeacon mNearestBeacon = new NearestBeacon();
 *       mNearestBeacon.setExhibit_distance(3);          // 3m, default NEAREST_DISTANCE
 *       mNearestBeacon.setMin_stay_milliseconds(3000);  // 3000ms, default MIN_STAY_MILLISECONDS
 *       // 在RangeNotifier接口的回调函数didRangeBeaconsInRegion()中执行：
 *       Beacon beacon = mNearestBeacon.getNeaestBeacon(NearestBeacon.GET_EXHIBIT_BEACON,beacons);
 *   客户端注意：在播放展品介绍期间，getNeaestBeacon(type=NearestBeacon.GET_EXHIBIT_BEACON)返回的符合以上条件的beacon.
 *   (1)与当前播放展品对应的beacon相同，则忽略之，继续播放该展品。
 *   (2)与当前播放展品对应的beacon不同，则停止当前播放，转而播放收到beacon对应的展品。
 *   (3)返回null,处理方式同(1)
 * 
 * 二、游客定位
 *   返回距离最近(m)的beacon
 *   NearestBeacon mNearestBeacon = new NearestBeacon();
 *   // 在RangeNotifier接口的回调函数didRangeBeaconsInRegion()中执行：
 *   Beacon beacon = mNearestBeacon.getNeaestBeacon(NearestBeacon.GET_LOCATION_BEACON,beacons);
 * @author Dun
 *
 */
public class NearestBeacon {
		
	/** 默认最近距离(m),用于展品定位 **/
	public static double NEAREST_DISTANCE = 3.0;
	
	/** 默认最小停留时间(ms),用于展品定位 */
	public static long MIN_STAY_MILLISECONDS = 3000L; // 3s
	
	/** 获取游客定位beacon */
	public static int GET_LOCATION_BEACON = 0;
	
	/** 获取展品定位beacon */
	public static int GET_EXHIBIT_BEACON = 1;
	
	/** 用于展品定位的最小距离(m) **/
	private double mExhibit_distance;
	
	/** 最小停留时间(ms) */
	private long mMin_stay_milliseconds;
	
	/** 本次扫描周期计算的最小距离的beacon，即距离最近的beacon */
	private Beacon mNeaestBeacon = null;
	
	/** mNeaestBeacon对应的距离 */
	private double mNeaestBeacon_distance = -1.0D;
	
	/** 上一扫描周期存储的符合条件的展品定位beacon，距离小于mExhibit_distance，逗留时间大于mMin_stay_milliseconds */
	private Beacon mExhibitBeacon = null;
	
	/** mExhibitBeacon对应的时间戳 */
	private long mTimestamp = 0L;
	
	/** 根据距离排序beacons，用于计算距离最近的beacon */
	private ArrayList<BeaconForSort> mBeaconList = new ArrayList<BeaconForSort>();
    
	/**
	 * 展品定位使用默认最小距离和最小停留时间<br>
	 * 默认最小距离,NearestBeacon.NEAREST_DISTANCE
	 * 默认最小停留时间(ms)，NearestBeacon.MIN_STAY_MILLISECONDS
	 * @see NearestBeacon#NEAREST_DISTANCE
	 * @see NearestBeacon#MIN_STAY_MILLISECONDS
	 */
	public NearestBeacon() {
		mExhibit_distance = NEAREST_DISTANCE;
		mMin_stay_milliseconds = MIN_STAY_MILLISECONDS;
	}
	
	/**
	 * 参数设定展品定位时的最小距离和最小停留时间
	 * @param nearest_distance 最小距离(m)
	 * @param min_stay_time 最小停留时间(ms)
	 */
    public NearestBeacon(double nearest_distance,long min_stay_time) {
    	mExhibit_distance = nearest_distance;
    	mMin_stay_milliseconds = min_stay_time;
	}
    	
	/**
	 * 获得当前设定用于展品定位的最小距离(m)
	 * @return the mExhibit_distance
	 */
	public double getExhizibit_distance() {
		return mExhibit_distance;
	}

	/**
	 * 设定用于展品定位的最小距离(m)
	 * @param Exhibit_distance the mExhibit_distance to set
	 */
	public void setExhibit_distance(double Exhibit_distance) {
		this.mExhibit_distance = Exhibit_distance;
	}

	/**
	 * 获得当前设定用于展品定位的最小停留时间(ms)
	 * @return the mMin_stay_milliseconds
	 */
	public long getMin_stay_milliseconds() {
		return mMin_stay_milliseconds;
	}

	/**
	 * 设定用于展品定位的最小停留时间(ms)
	 * @param Min_stay_milliseconds the mMin_stay_milliseconds to set
	 */
	public void setMin_stay_milliseconds(long Min_stay_milliseconds) {
		this.mMin_stay_milliseconds = Min_stay_milliseconds;
	}
	
	/**
	 * 每个扫描周期结束，获取展品定位beacon或游客定位beacon<br>
	 * 注:展品定位beacon: 距离小于最小距离，并且逗留时间大于最小停留时间的beacon<br>
	 *    游客定位beacon: 距离最近的beacon，展品的beacon也可以用于定位游客位置 <br>
	 *    应该没有同时要求返回两种beacon的情况存在。因此，用type区分返回beacon的类型是可行的。<br>
	 *    在RangeNotifier接口的回调函数didRangeBeaconsInRegion()中调用此函数。<br>
	 *    每个扫描周期结束，根据20秒内各beacon的RSSI平均值计算它的距离，该回调获取这些beacon的距离值。<br>
	 * @param type NearestBeacon.GET_LOCATION_BEACON:返回游客定位beacon; NearestBeacon.GET_EXHIBIT_BEACON:返回展品定位beacon
	 * @param beacons 本次扫描周期发现的beacon,BeaconService对这些beacon进行了距离修正。
	 * @return 根据type，返回展品定位beacon或游客定位beacon或null
	 */
	public Beacon getNearestBeacon(int type,final Collection<Beacon> beacons) {
		Beacon beacon = nearestBeacon(beacons);
		if (type == GET_LOCATION_BEACON) return beacon;
		if (type == GET_EXHIBIT_BEACON) return exhibitBeacon(); // 注意必须在nearestBeacon(beacons)后调用。
		return null;
	}

	/** 
	 * 每个扫描周期结束，计算距离最近的beacon。用于游客定位。<br>
	 * @param beacons 本次扫描周期发现的beacon
	 * @return 距离最近的beacon; 如果本次扫描，没有beacon发现，返回null
	 * */
	private Beacon nearestBeacon(final Collection<Beacon> beacons) {
		if (beacons.size() == 0) {  // 本次扫描周期没有发现beacon
			mNeaestBeacon = null;
			mNeaestBeacon_distance = -1.0D;
			return null;
		}
		mBeaconList.clear();
		Iterator<Beacon> iterator = beacons.iterator();
		while (iterator.hasNext()) {
			Beacon beacon = iterator.next();
			BeaconForSort bfs = new BeaconForSort();
			bfs.beacon = beacon;
			bfs.distance = beacon.getDistance();
			mBeaconList.add(bfs);
		}
		Collections.sort(mBeaconList);
		mNeaestBeacon = mBeaconList.get(0).beacon;
		mNeaestBeacon_distance = mBeaconList.get(0).distance;
		return mNeaestBeacon;
	}	
	
	/** 
	 * 每个扫描周期结束，执行此函数，找出符合条件的beacon。用于展品定位<br>
	 * 条件：距离最近(并且小于NEAREST_DISTANCE)，逗留时间大于最小停留时间(MIN_STAY_MILLISECONDS)
	 * 注意，函数的调用必须在nearestBeacon()之后调用
	 * @return 符合条件的beacon; 不符合条件，返回null.
	 */
	private Beacon exhibitBeacon() {
		// 如果本次扫描没有发现beacon
		if (mNeaestBeacon == null) return null;
		
		// 如果本次扫描周期最小距离beacon(mNeaestBeacon)大于展品定位的最小距离，本次扫描周期没有符合条件的展品定位beacon
		if (mNeaestBeacon_distance > mExhibit_distance) return null;
		
		// 如果是第一次发现的beacon(mNeaestBeacon),并且它的距离小于展品定位最小距离(mExhibit_distance),则初始化该beacon是展品定位的beacon(mExhibitBeacon)
		if (mExhibitBeacon == null) {
			mExhibitBeacon = mNeaestBeacon;
			mTimestamp = System.currentTimeMillis();
			return null; // 第一次存储查品定位beacon，逗留时间肯定不符合条件，因此返回null
		}
		
		// 如果本次扫描周期最小距离beacon(mNeaestBeacon)与上一扫描周期存储的展品beacon(mExhibitBeacon)不同，则替换mExhibitBeacon
		if (!mExhibitBeacon.equals(mNeaestBeacon)) {
			mExhibitBeacon = mNeaestBeacon;
			mTimestamp = System.currentTimeMillis();
			return null; // 刚刚替换距离最近的beacon，逗留时间肯定不符合条件，因此返回null
		}
		
		// 如果本次扫描周期最小距离beacon(mNeaestBeacon)与上一扫描周期存储的展品beacon(mExhibitBeacon)相同，则判断是否满足最小停留时间
		if (System.currentTimeMillis() - mTimestamp >= mMin_stay_milliseconds) {
			return mExhibitBeacon;
		}
		return null;
	}
	
	/** 用于根据距离排序beacons */
	private class BeaconForSort implements Comparable<BeaconForSort> {
		Beacon beacon;
		double distance;
		@Override
		public int compareTo(BeaconForSort arg0) {
			if  (Math.abs(this.distance - arg0.distance) < 0.001)  // 0.001毫米级精度
				return 0;
		    if (this.distance < arg0.distance) 
		    	return -1;
		    else 
		    	return 1; 
		}	
	}	
}
