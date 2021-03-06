package com.neusoft.qiangzi.socketservicedemo.SocketHelper;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TCPHelper {
    private final String TAG = "TCPHelper";
    private int localPort = 7000;
    private int remotePort = 7000;
    private String remoteIP = "127.0.0.1";
    private Socket mSocket = null;
    private OutputStream outputStream = null;
    private InputStream inputStream = null;
    private OnTCPReceiveListener mListener = null;
    private OnTCPEventListener eventListener = null;
    private Thread receiveThread = null;
    private InetAddress iaRemoteIP = null;
    private boolean isOpened = false;
    private boolean isStartRecv = false;

    public String receivedMsg;

    public TCPHelper() {
    }

    public TCPHelper(String remoteIP, int remotePort) {
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
    }

    public void setLocalPort(int Port) {
        this.localPort = Port;
    }

    public int getLocalPortPort() {
        return localPort;
    }

    public boolean setRemoteIP(String ip) {
        if (isIP(ip)) {
            remoteIP = ip;
            return true;
        } else return false;
    }
    public String getRemoteIP() {
        if(!remoteIP.isEmpty()){
            return remoteIP;
        }else if (mSocket != null && mSocket.isConnected()) {
            return mSocket.getRemoteSocketAddress().toString().split("/")[1];
        }else {
            return "";
        }
    }
    public void setRemotePort(int port) {
        remotePort = port;
    }

    public int getRemotePortPort() {
        return remotePort;
    }

    public boolean isOpen() {
        return isOpened;
    }

    public void openSocket() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isOpened) {
                        return;
                    }
                    mSocket = new Socket();
                    SocketAddress socketAddress = new InetSocketAddress(remoteIP, remotePort);
                    mSocket.connect(socketAddress, 5000);
                    mSocket.setSoTimeout(1000);//timeout for read
                    mSocket.setKeepAlive(true);
                    outputStream = mSocket.getOutputStream();
                    inputStream = mSocket.getInputStream();
                    isOpened = true;
                    mHandler.sendEmptyMessage(HANDLE_OPEN_SUCCESS);
                } catch (SocketTimeoutException e) {
                    Log.d(TAG, "openSocket timeout!");
                    mHandler.sendEmptyMessage(HANDLE_OPEN_TIMEOUT);
                    closeSocket();
                } catch (Exception e) {
                    Log.e(TAG, "openSocket error.e=" + e.toString());
                    mHandler.sendEmptyMessage(HANDLE_OPEN_FAILED);
                    closeSocket();
                }
            }
        }).start();

    }

    public void send(final String data) {
        if (data.isEmpty() || outputStream == null) return;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "send:" + data);
                    outputStream.write(data.getBytes());
                } catch (Exception e) {
                    Log.e(TAG, "send error.e=" + e.toString());
                    return;
                }
            }
        });
        t.start();
    }

    public void setOnTCPEventListener(OnTCPEventListener listener) {
        eventListener = listener;
    }
    public void setOnTCPReceiveListener(OnTCPReceiveListener listener) {
        mListener = listener;
    }


    private final int HANDLE_RECV_MSG = 100;
    private final int HANDLE_OPEN_SUCCESS= 101;
    private final int HANDLE_OPEN_FAILED= 102;
    private final int HANDLE_OPEN_TIMEOUT= 103;
    private final int HANDLE_SEND_ERROR= 104;
    private final int HANDLE_RECV_ERROR= 105;
    private final int HANDLE_BREAK_OFF= 106;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLE_RECV_MSG:
                    if (mListener != null) mListener.onReceived(receivedMsg);
                    break;
                case HANDLE_OPEN_SUCCESS:
                    if (eventListener != null) eventListener.onTcpEvent(TCP_EVENT.TCP_OPEN_SUCCESS);
                    break;
                case HANDLE_OPEN_TIMEOUT:
                    if (eventListener != null) eventListener.onTcpEvent(TCP_EVENT.TCP_OPEN_TIMEOUT);
                    break;
                case HANDLE_OPEN_FAILED:
                    if (eventListener != null) eventListener.onTcpEvent(TCP_EVENT.TCP_OPEN_FAILED);
                    break;
                case HANDLE_SEND_ERROR:
                    if (eventListener != null) eventListener.onTcpEvent(TCP_EVENT.TCP_SEND_ERROR);
                    break;
                case HANDLE_RECV_ERROR:
                    if (eventListener != null) eventListener.onTcpEvent(TCP_EVENT.TCP_RECV_ERROR);
                    break;
                case HANDLE_BREAK_OFF:
                    if (eventListener != null) eventListener.onTcpEvent(TCP_EVENT.TCP_BREAK_OFF);
                    break;
            }
        }
    };

    public void startReceiveData() {
        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "startReceiveData ok.");
                isStartRecv = true;
                byte[] datas = new byte[512];
                while (isStartRecv) {
                    try {
                        int len = inputStream.read(datas);
                        if(len > 0) {
                            receivedMsg = new String(datas, 0, len);
                            mHandler.sendEmptyMessage(HANDLE_RECV_MSG);
                            //java.util.Arrays.fill(datas, (byte) 0);
                            Log.d(TAG, "receiveThread:" + receivedMsg);
                        }else if(len == -1){
                            Log.e(TAG, "receiveThread: read -1");
                            mHandler.sendEmptyMessage(HANDLE_BREAK_OFF);
                            //break;
                        }
                    } catch (SocketTimeoutException e) {
                        //超时，继续接受
                        //Log.d(TAG, "receiveThread: timeout!");
                    } catch (Exception e) {
                        //异常，可能是网络中断了
                        Log.e(TAG, "receiveThread error.e=" + e.toString());
                        mHandler.sendEmptyMessage(HANDLE_BREAK_OFF);
                        break;
                    }
                }
                isStartRecv = false;
                Log.d(TAG, "receiveThread: end!");
            }
        });
        receiveThread.start();
    }

    public void stopReceiveData() {
        if (!isStartRecv) return;
        isStartRecv = false;
        try {
            if (receiveThread != null && receiveThread.isAlive()) {
                receiveThread.join();
                receiveThread = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "stopReceiveData error.e=" + e.toString());
        }
    }

    public void closeSocket() {
        stopReceiveData();
        try {
            if (mSocket != null && !mSocket.isClosed()) {
                mSocket.close();
                mSocket = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            isOpened = false;
        } catch (Exception e) {
            Log.e(TAG, "closeSocket error.e=" + e.toString());
        }
    }


    public static boolean isIP(String addr) {
        if (addr.length() < 7 || addr.length() > 15 || "".equals(addr)) {
            return false;
        }
        /**
         * 判断IP格式和范围
         */
        String rexp = "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";

        Pattern pat = Pattern.compile(rexp);

        Matcher mat = pat.matcher(addr);

        boolean ipAddress = mat.find();

        //============对之前的ip判断的bug在进行判断
        if (ipAddress == true) {
            String ips[] = addr.split("\\.");

            if (ips.length == 4) {
                try {
                    for (String ip : ips) {
                        if (Integer.parseInt(ip) < 0 || Integer.parseInt(ip) > 255) {
                            return false;
                        }

                    }
                } catch (Exception e) {
                    return false;
                }

                return true;
            } else {
                return false;
            }
        }

        return ipAddress;
    }

    public interface OnTCPReceiveListener {
        void onReceived(String data);
    }

    public enum TCP_EVENT{
        TCP_OPEN_SUCCESS,
        TCP_OPEN_FAILED,
        TCP_OPEN_TIMEOUT,
        TCP_SEND_ERROR,
        TCP_RECV_ERROR,
        TCP_BREAK_OFF
    }
    public interface OnTCPEventListener {
        void onTcpEvent(TCP_EVENT e);
    }
}
