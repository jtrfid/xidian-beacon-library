package edu.xidian.FindBeacons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * 每次扫描周期结束，记录定位到最近参考点的时间；
 * 在一定的时间段内，统计次数最多的定位参考点。
 * 该段时间即为在该定位参考点的停留时间，从而定位到该定位参考点对应的展品。
 * @author djt
 */
public class NearestRefPoint {
	
	/** 默认最小停留时间(ms),用于展品定位 */
	public static long MIN_STAY_MILLISECONDS = 3000L; // 3s
	
	/** 最小停留时间(ms) */
	private long mMin_stay_milliseconds;
	
	/**
	 * 每个扫描周期结束，记录最近定位参考点的时间
	 * key：定位参考点名称；value：时间戳序列(ms),即定位到该参考点的时间1,时间2,...
	 */
	private HashMap<String,ArrayList<Long>> mRefPoint = new HashMap<String,ArrayList<Long>>();
	
	/**
	 * 统计次数最多的定位参考点。
	 * @param stay_milliseconds 停留时间 
	 */
	public NearestRefPoint(long stay_milliseconds) {
		this.mMin_stay_milliseconds = stay_milliseconds;
	}
	
	/**
	 * 统计次数最多的定位参考点。
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
	 * 获取停留时间内，时间序列最多（即次数做多）的最近参考点
	 * @return 时间序列最多（即次数做多）的最近参考点
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
			if(maxNum < timestamps.size()){
				maxNum = timestamps.size();
				maxRPname = RPname;
			}
			// 如果该参考点无时间戳序列，从mRefPoint删除之
			if(timestamps.size() == 0)
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
