package org.altbeacon.beacon.service;

/**
 * Interface that can be implemented to overwrite measurement and filtering
 * of RSSI values
 */
public interface RssiFilter {
    /** 添加测量值 */
    public void addMeasurement(Integer rssi);
    /** true: 无测量值 */
    public boolean noMeasurementsAvailable();
    /** 每个扫描周期结束，计算rssi均值或预测值 */
    public double calculateRssi();

}
