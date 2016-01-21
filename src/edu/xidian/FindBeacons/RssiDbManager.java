package edu.xidian.FindBeacons;

import java.sql.SQLException;
import java.util.List;

import org.altbeacon.beacon.logging.LogManager;
import com.j256.ormlite.dao.Dao;

import android.content.Context;

public class RssiDbManager {
	private final static String TAG = RssiDbManager.class.getSimpleName();
	/**
	 * RssiInfo表的数据访问对象
	 */
	private Dao<RssiInfo, String> RssiInfoDao;
	
	public RssiDbManager(Context context)
	{
		// 外部数据库Context
		Context dContext = new DatabaseContext(context, "rssiRecord/");
		RssiDbManagerHelper dbHelper = new RssiDbManagerHelper(dContext,"rssi.db");
		LogManager.d(TAG, "db start");
					
		try {
			RssiInfoDao = dbHelper.getRssiInfoDao();
		} catch (SQLException e) {
			LogManager.d(TAG,"访问数据库rssi.db出错,"+e.toString());
		}
	}
	
	public void SetRssiInfo(RssiInfo data)
	{
		try {
			RssiInfoDao.createOrUpdate(data);
			LogManager.d(TAG, "Dao create!");
		} catch (SQLException e) {
			LogManager.d(TAG,"访问数据库rssi.db出错,"+e.toString());
		}
	}
	
	public List<RssiInfo> getRssiInfo()
	{
		try {
			List<RssiInfo> rssiInfo = RssiInfoDao.queryForAll();
			for (RssiInfo rssi_Info:rssiInfo) {
				rssi_Info.setRSSIs();
			}
			return rssiInfo;
		} catch (SQLException e) {
			LogManager.d(TAG,"访问数据库rssi.db出错,"+e.toString());
		}
		return null;
	}
}