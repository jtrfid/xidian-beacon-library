package edu.xidian.FindBeacons;

import java.sql.SQLException;

import org.altbeacon.beacon.logging.LogManager;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

/**
 * 管理各个定位参考点测量的各个beacons的rssi平均值
 * 数据表：RssiInfo
 */
public class RssiDbManagerHelper extends OrmLiteSqliteOpenHelper {

	public static final String TAG = RssiDbManagerHelper.class.getSimpleName();

	public static int DATABASE_VERSION = 1;
	private String db_name;
	
	/**
	 * RssiInfo表的数据访问对象
	 */
	private Dao<RssiInfo, String> RssiInfoDao;
	
	/**
	 * 管理各个定位参考点测量的各个beacons的rssi平均值，数据表：RssiInfo
	 * @param context 如果使用DatabaseContext对象，则数据库建立在外部SD卡上;
	 *                否则，DB_PATH = "/data" + Environment.getDataDirectory().getAbsolutePath()+ "/" + context.getPackageName()+"/databases"
	 * @param db_name 数据库名称(建议带后缀.db)
	 */
	public RssiDbManagerHelper(Context context,String db_name) {
		super(context, db_name, null, DATABASE_VERSION);
		this.db_name = db_name;
	}

	/**
	 * 创建数据库
	 */
	@Override
	public void onCreate(SQLiteDatabase arg0, ConnectionSource arg1) {
		try {
			//创建数据表
			TableUtils.createTableIfNotExists(arg1, RssiInfo.class);
			LogManager.d(TAG, "table create!");
		} catch (java.sql.SQLException e) {
			Log.d(TAG,"数据库:"+db_name+",创建失败！" + e.toString());
		}
	}

	/**
	 * 更新数据库
	 */
	@Override
	public void onUpgrade(SQLiteDatabase arg0, ConnectionSource arg1, int arg2,
			int arg3) {
		//TODO 目前考虑，升级即是删除guide目录下的数据库，重新生成之。
	}

	/**
	 * 获取RssiInfo表的数据访问对象RssiInfoDao，用来操作RssiInfo表
	 * @return
	 * @throws SQLException
	 */
	public Dao<RssiInfo, String> getRssiInfoDao() throws SQLException {
		if (RssiInfoDao == null) {
			RssiInfoDao = getDao(RssiInfo.class);
		}
		return RssiInfoDao;
	}

}
