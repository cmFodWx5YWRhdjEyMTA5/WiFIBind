package com.idx.wifibind.wifi;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

/**
 * Created by ryan on 18-3-2.
 * Email: Ryan_chan01212@yeah.net
 */

public class WiFiAutoConnectManager {
        private static final String TAG = WiFiAutoConnectManager.class.getSimpleName();
        WifiManager wifiManager;

        // 定义几种加密方式，一种是WEP，一种是WPA，一种没有密码的, 一种密码无效的情况
        public enum WifiCipherType {
            WIFICIPHER_WEP, WIFICIPHER_WPA, WIFICIPHER_NOPASS, WIFICIPHER_INVALID
        }

        public WiFiAutoConnectManager(WifiManager wifiManager) {
            this.wifiManager = wifiManager;
        }

        // 提供一个外部接口，传入要连接的无线网
        public void connect(String ssid, String password, WifiCipherType type) {
            Thread thread = new Thread(new ConnectRunnable(ssid, password, type));
            thread.start();
        }

        // 查看以前是否也配置过这个网络
        private WifiConfiguration isExsits(String SSID) {
            List<WifiConfiguration> existingConfigs = wifiManager
                    .getConfiguredNetworks();
            for (WifiConfiguration existingConfig : existingConfigs) {
                if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
                    return existingConfig;
                }
            }
            return null;
        }

        private WifiConfiguration createWifiInfo(String SSID, String Password,
                                                 WifiCipherType Type) {
            WifiConfiguration config = new WifiConfiguration();
            config.allowedAuthAlgorithms.clear();
            config.allowedGroupCiphers.clear();
            config.allowedKeyManagement.clear();
            config.allowedPairwiseCiphers.clear();
            config.allowedProtocols.clear();
            config.SSID = "\"" + SSID + "\"";
            // config.SSID = SSID;
            // nopass
            if (Type == WifiCipherType.WIFICIPHER_NOPASS) {
                // config.wepKeys[0] = "";
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                // config.wepTxKeyIndex = 0;
            }
            // wep
            if (Type == WifiCipherType.WIFICIPHER_WEP) {
                if (!TextUtils.isEmpty(Password)) {
                    if (isHexWepKey(Password)) {
                        config.wepKeys[0] = Password;
                    } else {
                        config.wepKeys[0] = "\"" + Password + "\"";
                    }
                }
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                config.wepTxKeyIndex = 0;
            }
            // wpa
            if (Type == WifiCipherType.WIFICIPHER_WPA) {
                config.preSharedKey = "\"" + Password + "\"";
                config.hiddenSSID = true;
                config.allowedAuthAlgorithms
                        .set(WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedPairwiseCiphers
                        .set(WifiConfiguration.PairwiseCipher.TKIP);
                // 此处需要修改否则不能自动重联
                // config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                config.allowedPairwiseCiphers
                        .set(WifiConfiguration.PairwiseCipher.CCMP);
                config.status = WifiConfiguration.Status.ENABLED;

            }
            return config;
        }

        // 打开wifi功能
        private boolean openWifi() {
            boolean open = true;
            if (!wifiManager.isWifiEnabled()) {
                open = wifiManager.setWifiEnabled(true);
            }
            return open;
        }

        // 关闭WIFI
        private void closeWifi() {
            if (wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(false);
            }
        }

        class ConnectRunnable implements Runnable {
            private String ssid;
            private String password;
            private WifiCipherType type;

            public ConnectRunnable(String ssid, String password, WifiCipherType type) {
                this.ssid = ssid;
                this.password = password;
                this.type = type;
            }

            @Override
            public void run() {
                // 打开wifi
                openWifi();
                // 开启wifi功能需要一段时间(我在手机上测试一般需要1-3秒左右)，所以要等到wifi
                // 状态变成WIFI_STATE_ENABLED的时候才能执行下面的语句
                while (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING) {
                    try {
                        // 为了避免程序一直while循环，等待100毫秒检测……
                        Thread.sleep(100);

                    } catch (InterruptedException ie) {
                        Log.e(TAG, ie.toString());
                    }
                }

                WifiConfiguration tempConfig = isExsits(ssid);
                if (tempConfig != null) {
                    // wifiManager.removeNetwork(tempConfig.networkId);
                    boolean b = wifiManager.enableNetwork(tempConfig.networkId, true);
                } else {
                    WifiConfiguration wifiConfig = createWifiInfo(ssid, password, type);
                    if (wifiConfig == null) {
                        Log.d(TAG, "wifiConfig is null!");
                        return;
                    }

                    int netID = wifiManager.addNetwork(wifiConfig);
                    boolean enabled = wifiManager.enableNetwork(netID, true);
                    Log.d(TAG, "enableNetwork status enable=" + enabled);
                    boolean connected = wifiManager.reconnect();
                    Log.d(TAG, "enableNetwork connected=" + connected);
                }
            }
        }

        private static boolean isHexWepKey(String wepKey) {
            final int len = wepKey.length();
            // WEP-40, WEP-104, and some vendors using 256-bit WEP (WEP-232?)
            if (len != 10 && len != 26 && len != 58) {
                return false;
            }
            return isHex(wepKey);
        }

        private static boolean isHex(String key) {
            for (int i = key.length() - 1; i >= 0; i--) {
                final char c = key.charAt(i);
                if (!(c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
                        && c <= 'f')) {
                    return false;
                }
            }
            return true;
        }

        // 获取ssid的加密方式
        public static WifiCipherType getCipherType(Context context, String ssid) {
            WifiManager wifiManager = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);

            List<ScanResult> scanResults = wifiManager.getScanResults();
            for (ScanResult scResult : scanResults) {
                if (!TextUtils.isEmpty(scResult.SSID) && scResult.SSID.equals(ssid)) {
                    String capabilities = scResult.capabilities;
                    if (!TextUtils.isEmpty(capabilities)) {
                        if (capabilities.contains("WPA")
                                || capabilities.contains("wpa")) {
                            return WifiCipherType.WIFICIPHER_WPA;
                        } else if (capabilities.contains("WEP")
                                || capabilities.contains("wep")) {
                            return WifiCipherType.WIFICIPHER_WEP;
                        } else {
                            return WifiCipherType.WIFICIPHER_NOPASS;
                        }
                    }
                }
            }
            return WifiCipherType.WIFICIPHER_INVALID;
        }
}
