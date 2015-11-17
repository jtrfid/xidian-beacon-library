package edu.xidian.NearestBeacon;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.distance.ModelSpecificDistanceCalculator;
import org.altbeacon.beacon.logging.LogManager;

import android.content.Context;
import android.content.res.AssetManager;

/**
 * <pre>
 * 设置缺省距离计算模型文件，为了不改变BeaconService中的设置逻辑，请使用本类设置beacon的缺省距离计算模型。
 * (1) 使用本地距离计算模型文件（assets/model-distance-calculations.json）含default模型。
 *     setDefaultDistanceCalcuator(null);  // 在应用程序的私有空间准备了model-distance-calculations.json
 * (2) 使用url指定的网上模型,如"http://data.altbeacon.org/android-distance.json"
 *     setDefaultDistanceCalcuator(String distanceModelUpdateUrl);
 * 本类仅准备了本地或网上模型文件， 后续步骤：BeaconService的onCreate()中的下列代码，完成距离模型。
 * {@code
        defaultDistanceCalculator =  new ModelSpecificDistanceCalculator(this, BeaconManager.getDistanceModelUpdateUrl());
        Beacon.setDistanceCalculator(defaultDistanceCalculator);
 * }
 * 注意：
 * 必须在绑定BeaconService之前调用setDefaultDistanceCalcuator();
 * 因为仅在第一次绑定BeaconService时，ModelSpecificDistanceCalculator()检查应用程序私有空间的CONFIG_FILE。因此每次测试必须完整卸载应用程序，才能正确验证此程序的逻辑。
 * 
 * </pre>
 * @author Dun
 *
 */
public class DefaultDistanceCalcuator {
	private static final String TAG = "DefaultDistanceCalcuator";
	
	private static final String CONFIG_FILE = "model-distance-calculations.json";
	private Context mContext;
	
	/**
	 * {@link DefaultDistanceCalcuator}
	 */
	public DefaultDistanceCalcuator(Context context) {
		this.mContext = context;
	}

	/**
	 * 设置距离计算模型
	 * @param distanceModelUpdateUrl null 使用asserts/model-distance-calculations.json; 否则使用url指定的网上模型文件,如"http://data.altbeacon.org/android-distance.json"
	 */
	public void setDefaultDistanceCalcuator(String distanceModelUpdateUrl) {
		/**
		 * 读取应用程序asserts/model-distance-calculations.json,在应用程序私有数据空间写入同名文件。
		 * java/org/altbeacon/beacon/distance/ModelSpecificDistanceCalculator.java的loadModelMapFromFile()读取此文件，以构造缺省模型。
		 */
		 if (distanceModelUpdateUrl == null) {
			try {
				String jsonString = stringFromAssertFile(); // 读取asserts/model-distance-calculations.json
				boolean ok = saveJson(jsonString); //将距离模型json数据写入应用程序私有空间中的文件：CONFIG_FILE。
				if (ok) { 
					LogManager.d(TAG, "setDefaultDistanceCalcuator ok,from asserts/" + CONFIG_FILE);
				}
				else { 
					LogManager.d(TAG, "setDefaultDistanceCalcuator error,from asserts/" + CONFIG_FILE);
				}
			} catch (IOException e) {
				LogManager.d(TAG,"Exception:" + e.toString());
			}
			// 设置一个虚假的url，目的是引起BeaconService中调用ModelSpecificDistanceCalculator()-->loadModelMap()-->在应程序私有空间找CONFIG_FILE
			// 由于以上已经存储了这个文件,因此，绑定BeaconService后，执行上述序列，loadModelMap()能够成功找到该文件。
			// 鉴于此，必须在绑定BeaconService前执行此函数。loadModelMap()仅在第一次调用时才检查此文件，一旦文件已经写入，下一次就不检查了。因此测试时，每次要完全卸载程序，才能验证此程序的逻辑。
			BeaconManager.setDistanceModelUpdateUrl("nodistanceModelUpdateUrl");
		 }
		 else { // BeaconService中调用ModelSpecificDistanceCalculator()设置距离计算模型
			 BeaconManager.setDistanceModelUpdateUrl(distanceModelUpdateUrl);
			 LogManager.d(TAG,"setDefaultDistanceCalcuator, from " + distanceModelUpdateUrl);
		 }
		
	}
	
	/*
	 * 读取
	 * assets/CONFIG_FILE文件
	 * 如：asserts/model-distance-calculations.json
	 */
	private String stringFromAssertFile() throws IOException {
		BufferedReader bufferedReader = null;
		InputStream inputStream = null;
		StringBuilder inputStringBuilder = new StringBuilder();
		
		AssetManager asset = mContext.getAssets(); 
		
		try {
			inputStream = asset.open(CONFIG_FILE);
			if (inputStream == null) {
				throw new RuntimeException("Cannot load resource at assert:"
						+ CONFIG_FILE);
			}
			bufferedReader = new BufferedReader(new InputStreamReader(
					inputStream, "UTF-8"));

			String line = bufferedReader.readLine();
			while (line != null) {
				inputStringBuilder.append(line);
				inputStringBuilder.append('\n');
				line = bufferedReader.readLine();
			}
		} finally {
			if (bufferedReader != null) {
				bufferedReader.close();
			}
			if (inputStream != null) {
				inputStream.close();
			}
		}
		
		return inputStringBuilder.toString();
	}

   /** 将距离模型json数据写入应用程序私有空间中的文件：CONFIG_FILE。
    * 此函数拷贝于java/org/altbeacon/beacon/distance/ModelSpecificDistanceCalculator.java **/
   private boolean saveJson(String jsonString) {

        FileOutputStream outputStream = null;

        try {
            outputStream = mContext.openFileOutput(CONFIG_FILE, Context.MODE_PRIVATE);
            outputStream.write(jsonString.getBytes());
            outputStream.close();
        } catch (Exception e) {
            LogManager.w(e, TAG, "Cannot write updated distance model to local storage");
            return false;
        }
        finally {
            try {
                if (outputStream != null) outputStream.close();
            }
            catch (Exception e) {}
        }
        LogManager.i(TAG, "Successfully saved new distance model file");
        return true;
    }
}
