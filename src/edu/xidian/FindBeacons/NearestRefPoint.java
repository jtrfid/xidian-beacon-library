package edu.xidian.FindBeacons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.logging.LogManager;

/**
 * 找出停留时间内，次数最多的最近参考点，次数>=2
 * 每次扫描周期结束，记录定位到最近参考点的时间；
 * 在一定的时间段内，统计次数最多的定位参考点。
 * 该段时间即为在该定位参考点的停留时间，从而定位到该定位参考点对应的展品。
 * @author djt
 */
public class NearestRefPoint {
	
	private final static String TAG = NearestRefPoint.class.getSimpleName();
	
	/** 默认最小停留时间(ms),用于展品定位 */
	public static long MIN_STAY_MILLISECONDS = 5000L; // 3s
	
	/** 最小停留时间(ms) */
	private long mMin_stay_milliseconds;
	
	/**
	 * 每个扫描周期结束，记录最近定位参考点的时间
	 * key：定位参考点名称；value：时间戳序列(ms),即定位到该参考点的时间1,时间2,...
	 */
	private HashMap<String,ArrayList<Long>> mRefPoint = new HashMap<String,ArrayList<Long>>();
	
	/**
	 * 找出停留时间内，次数最多的最近参考点，次数>=2
	 * @param stay_milliseconds 停留时间 
	 */
	public NearestRefPoint(long stay_milliseconds) {
		this.mMin_stay_milliseconds = stay_milliseconds;
	}
	
	/**
	 * 找出停留时间内，次数最多的最近参考点，次数>=2
	 * 默认停留时间{@link #MIN_STAY_MILLISECONDS}
	 */
	public NearestRefPoint() {
		this.mMin_stay_milliseconds = MIN_STAY_MILLISECONDS;
	}

	/**
	 * 定位到某参考点
	 * @param RPname 定位参考点名称
	 */
	public void addRefPoint(String RPname){
		// 获取该定位参考点的时间戳序列
		ArrayList<Long> timestamps = mRefPoint.get(RPname);
		if (timestamps == null) {
			timestamps = new ArrayList<Long>();
			mRefPoint.put(RPname, timestamps);
		}
		timestamps.add(System.currentTimeMillis());
	}
	
	/**
	 * 本次扫描周期结束，获取最近参考点，并添加至mRefPoint，供统计时使用
	 * @param beacons   本次扫描周期结束，找到的beacon集合
	 * @param mRssiInfo 指纹训练阶段存储在数据库中的各个定位参考点的beacons的rssi平均值
	 * @return 获取最近参考点
	 */
	public String getNearestRefPoint(Collection<Beacon> beacons,List<RssiInfo> mRssiInfo){
		String str;
    	// 定位参考点名称
		String RPname;
		/**
		 * key: beacon的major,minor组成的字符串major_minor;
		 * value: rssi平均值
		 */
		Map<String,Double> RSSIs = new HashMap<String,Double>();
		
		Double diff_sum = 0.0;    // 单位平方差
		Double min_value = 0.0;   // 最小单位平方差对应
		String min_RPname = null; // 最小单位平方差对应的定位参考点
		for(RssiInfo rssi_info : mRssiInfo) { // RP,遍历每个定位参考点
			diff_sum = 0.0; 
			RPname = rssi_info.getRPname();
			RSSIs = rssi_info.getRSSIs();
			for (Beacon beacon : beacons) { // 遍历本次扫描周期测到的各个beacon
				// becaon的两个id(major,minor)，rssi及其平均值
				String key = beacon.getId2()+"_"+beacon.getId3();
				Double rssi = beacon.getRunningAverageRssi();
				Double rssi_db = RSSIs.get(key);
				// 单位平方差
                if (rssi_db != null) 
					diff_sum = diff_sum + (rssi-rssi_db)*(rssi-rssi_db)/beacons.size();
				else {
					str = "数据库中无key="+key;
					LogManager.d(TAG, str);
				}
			}  // RSSIs
			if (min_value == 0.0) {
				min_value = diff_sum;
				min_RPname = RPname;
			}
			else if(min_value > diff_sum) {
				min_value = diff_sum;
				min_RPname = RPname;
			}
		} // for RP
		
		// 添加至mRefPoint，供统计时使用
		addRefPoint(min_RPname);
		str = "最近参考点："+min_RPname;
		LogManager.d(TAG, str);
		
        return min_RPname;
    }
	
	/**
	 * 获取停留时间内，时间序列最多（即次数做多）的最近参考点；并且次数大于等于2
	 * @return 时间序列最多（即次数做多）的最近参考点；并且次数大于等于2。若没有这样的参考点，返回null
	 */
	public String getNearestRefPoint(){
		String RPname = null;
		ArrayList<Long> timestamps = null;
		long current = System.currentTimeMillis();
		int maxNum = 0;
		String maxRPname = null;
		
		// 遍历每个参考点，找出时间序列最多（即次数做多）的参考点名称
		Iterator<String> it = mRefPoint.keySet().iterator();
		while(it.hasNext()) {
			RPname = it.next();
			timestamps = mRefPoint.get(RPname); // 时间戳序列
			Iterator<Long> it_timestamp = timestamps.iterator();
			// 剔除大于停留时间的时间戳
			while(it_timestamp.hasNext()){
				long timestamp = it_timestamp.next();
				if((current - timestamp) > mMin_stay_milliseconds) {
					it_timestamp.remove(); // 删除迭代器的next()指向的元素，这里是timestamp
				}
			}
			int size = timestamps.size();
			if(maxNum < size && size >=2 ){
				maxNum = size;
				maxRPname = RPname;
			}
			// 如果该参考点无时间戳序列，从mRefPoint删除之
			if(size == 0)
			  it.remove(); // 删除迭代器的next()指向的元素，这里是RPname(key)对应的键值对
		}
		
		return maxRPname;
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
	 * 返回所有记录的参考点及其时间戳序列，用于测试
	 * @return [参考点名称:时间戳1,时间戳2,...],[参考点名称:时间戳1,时间戳2,...]
	 */
	public String RefPoints(){
		String str = "";
		ArrayList<Long> timestamps = null;
		String RPname = null;
				
		// 遍历每个参考点
		Iterator<String> it = mRefPoint.keySet().iterator();
		while(it.hasNext()) {
			RPname = it.next();
			str += "[" + RPname + ":";
			timestamps = mRefPoint.get(RPname);
//			for(long timestamp:timestamps){
//				str += timestamp + ",";
//			}
			int length = timestamps.size();
			for(int i = 0; i < length; i++){
				if (i==length-1) str += timestamps.get(i);
				else str += timestamps.get(i) + ",";
			}
			str += "]";
		}
		
		return str;
	}

}
