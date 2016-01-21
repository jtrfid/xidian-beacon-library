package edu.xidian.FindBeacons;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.os.Environment;
import android.util.Log;

/**
 * 外部数据库Context,用于支持对存储在SD卡上的数据库的访问
 * 举例说明使用方法:
   （1）离线数据库管理类OfflineBeanSqlHelper
   public class OfflineBeanSqlHelper extends OrmLiteSqliteOpenHelper {
       public OfflineBeanSqlHelper(Context context, String name) {
		   super(context, name, null, DATABASE_VERSION);
	   }
   }
   （2）DatabaseContext对象
   mContext是应用程序，activit等
   Context dContext = new DatabaseContext(mContext, Constant.FLODER_NAME + museumId);
  （3） 生成离线数据库对象，该数据库文件在sd卡的目录(Constant.FLODER_NAME + museumId)中存储
    OfflineBeanSqlHelper helper = new OfflineBeanSqlHelper(dContext, museumId + ".db");
 **/
public class DatabaseContext extends ContextWrapper {
	
	public static final String TAG = DatabaseContext.class.getSimpleName();

	private String dbDir;

	/**
	 * 构造函数
	 * 
	 * @param base
	 *            上下文环境
	 * @param path
	 *            存储数据外部文件夹名（在SD卡目录中）
	 * 
	 */
	public DatabaseContext(Context base, String path) {
		super(base);
		dbDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
	}

	/**
	 * 获得数据库路径，如果不存在，则创建对象对象
	 * 
	 * @param name
	 * @param mode
	 * @param factory
	 */
	@Override
	public File getDatabasePath(String name) {
		// 判断是否存在sd卡
		boolean sdExist = android.os.Environment.MEDIA_MOUNTED
				.equals(android.os.Environment.getExternalStorageState());
		if (!sdExist) {// 如果不存在,
			Log.e("SD卡管理：", "SD卡不存在，请加载SD卡");
			return null;
		}

		// 判断目录是否存在，不存在则创建该目录
		File dirFile = new File(dbDir);
		if (!dirFile.exists())
			dirFile.mkdirs();

		// 数据库文件是否创建成功
		boolean isFileCreateSuccess = false;
		// 判断文件是否存在，不存在则创建该文件
		String dbPath = dbDir + "/" + name;// 数据库路径
		File dbFile = new File(dbPath);
		if (!dbFile.exists()) {
			try {
				isFileCreateSuccess = dbFile.createNewFile();// 创建文件
			} catch (IOException e) {
				Log.d(TAG,dbPath + ",数据库文件创建失败！"+e.toString());
			}
		} else
			isFileCreateSuccess = true;

		// 返回数据库文件对象
		if (isFileCreateSuccess)
			return dbFile;
		else
			return null;
	}

	/**
	 * 重载这个方法，是用来打开SD卡上的数据库的，android 2.3及以下会调用这个方法。
	 * 
	 * @param name
	 * @param mode
	 * @param factory
	 */
	@Override
	public SQLiteDatabase openOrCreateDatabase(String name, int mode,
			SQLiteDatabase.CursorFactory factory) {
		SQLiteDatabase result = SQLiteDatabase.openOrCreateDatabase(
				getDatabasePath(name), null);
		return result;
	}

	/**
	 * Android 4.0会调用此方法获取数据库。
	 * 
	 * @see android.content.ContextWrapper#openOrCreateDatabase(java.lang.String,
	 *      int, android.database.sqlite.SQLiteDatabase.CursorFactory,
	 *      android.database.DatabaseErrorHandler)
	 * @param name
	 * @param mode
	 * @param factory
	 * @param errorHandler
	 */
	@Override
	public SQLiteDatabase openOrCreateDatabase(String name, int mode,
			CursorFactory factory, DatabaseErrorHandler errorHandler) {
		SQLiteDatabase result = SQLiteDatabase.openOrCreateDatabase(
				getDatabasePath(name), null);
		return result;
	}
}
