package edu.xidian.FindBeacons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * 数据表：RssiInfo
 * 记录各个定位参考点测量的各个beacons的rssi平均值
 */
@DatabaseTable(tableName = "RssiInfo")
public class RssiInfo {

	/** 定位参考点名称 */
	@DatabaseField(id = true, columnName = "RPname", useGetSet = true)
	private String RPname;
	
	/** 
	 * 各个beacons的rssi平均值,格式:<br>
	 * major_minor:rssi,major_minor:rssi,...
	 */
	@DatabaseField(columnName = "RPrssis", useGetSet = true)
	private String RPrssis;
	
	/**
	 * key: beacon的major,minor组成的字符串major_minor;
	 * value: rssi平均值
	 */
	private Map<String,Double> RSSIs = new HashMap<String,Double>();
	
	public RssiInfo() {
		super();
	}

	public RssiInfo(String RPname, String RPrssis) {
		super();
		this.RPname = RPname;
		this.RPrssis = RPrssis;
	}

	/**
	 * @return 返回定位参考点名称
	 */
	public String getRPname() {
		return RPname;
	}

	/**
	 * 设置定位参考点名称
	 * @param RPname
	 */
	public void setRPname(String RPname) {
		this.RPname = RPname;
	}

    /** 
	 * @return 
	 * 各个beacons的rssi平均值,格式:<br>
	 * major_minor:rssi,major_minor:rssi,...
	 */
	public String getRPrssis() {
		return RPrssis;
	}

	/**
	 * 设置各个beacons的rssi平均值,格式:<br>
	 * major_minor:rssi,major_minor:rssi,...
	 * @param RPrssis
	 */
	public void setRPrssis(String RPrssis) {
		this.RPrssis = RPrssis;
		setRSSIs();
	}
	
	/**
	 * @return
	 * 获取各beacons的rssi平均值
	 * key: beacon的major,minor组成的字符串major_minor;
	 * value: rssi平均值
	 */
	public Map<String,Double> getRSSIs() {
		return RSSIs;
	}
	
	/**
	 * 设置RSSIs
	 * key: beacon的major,minor组成的字符串major_minor;
	 * value: rssi平均值
	 */
	public void setRSSIs() {
		RSSIs.clear();
		String[] str_rssis = RPrssis.split(","); // major_minor:rssi,major_minor:rssi,...
		String[] id_rssi = new String[2]; 
		for(int i=0;i<str_rssis.length;i++){
			id_rssi = str_rssis[i].split(":"); // major_minor:rssi1·	
			RSSIs.put(id_rssi[0], Double.parseDouble(id_rssi[1]));
		}
	}
}
