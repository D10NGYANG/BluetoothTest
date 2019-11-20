package com.example.bluetoothtest;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static android.widget.Toast.LENGTH_SHORT;

public class MainActivity extends AppCompatActivity {
    public TextView textView;
    public TextView something;
    public Button BTN_close;
    public ListView Bluelist;
    public Button BTN_send;
    public SwipeRefreshLayout refresh_blulist;
    public DrawerLayout drawerLayout;
    public SimpleAdapter adapter;
    public ArrayList<Map<String,String>> list= new ArrayList<Map<String,String>>();
    public BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    public Context context;

    private ConnectThread connectThread = null;
    private ConnectedThread connectedThread = null;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // 如果没有打开，就去打开
                if (!drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    drawerLayout.openDrawer(Gravity.LEFT);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //注册控件
        textView = (TextView)findViewById(R.id.textView);
        BTN_close = (Button)findViewById(R.id.BTN_close);
        Bluelist = (ListView)findViewById(R.id.Bluelist);
        something = (TextView)findViewById(R.id.something);
        BTN_send = (Button)findViewById(R.id.BTN_send);
        refresh_blulist = (SwipeRefreshLayout)findViewById(R.id.refresh_blulist);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        context = getApplicationContext();
        //注册广播接收
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);//蓝牙扫描新设备
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//匹配状态
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);//蓝牙连接成功
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);//蓝牙断开
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//蓝牙适配器扫描完成
        //intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);//蓝牙扫描状态变化
        //intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//蓝牙适配器状态
        registerReceiver(searchReceiver,intentFilter);
        //请求权限
        ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION},2);
        //打开蓝牙
        OpenBluetooth();
        //显示已匹对设备
        SeeOldBlueDevices();
        //下拉刷新蓝牙列表
        refresh_blulist.setColorSchemeResources(R.color.colorPrimary);
        refresh_blulist.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                list.clear();
                if (!bluetoothAdapter.isDiscovering()){
                    bluetoothAdapter.startDiscovery();
                    Log.e("+++++++++++++","startDiscovery");
                    Log.e("+++++++++++++",textView.getText().toString());
                    if (textView.getText().toString().indexOf("已连接") == -1){
                        textView.setText("正在搜索蓝牙设备....");
                    }
                }
                SeeOldBlueDevices();
            }
        });

        Bluelist.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {

            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                View firstView = absListView.getChildAt(i);
                if(i == 0 && (firstView == null || firstView.getTop() == 0)) {
                    /*上滑到listView的顶部时，下拉刷新组件可见*/
                    refresh_blulist.setEnabled(true);
                } else {
                    /*不是listView的顶部时，下拉刷新组件不可见*/
                    refresh_blulist.setEnabled(false);
                }
            }
        });
        //点击列表事件
        Bluelist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (bluetoothAdapter.isDiscovering()){
                    bluetoothAdapter.cancelDiscovery();
                }
                refresh_blulist.setRefreshing(false);
                textView.setText("下拉刷新蓝牙设备列表,或点击列表连接设备");
                Map<String,String> item = new HashMap<String,String>();
                item = list.get(i);
                String address = item.get("address");
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                int connectState = device.getBondState();//连接状态
                switch (connectState){
                    case BluetoothDevice.BOND_NONE://未匹对
                        try {
                            Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
                            createBondMethod.invoke(device);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case BluetoothDevice.BOND_BONDED://已匹对
                        try{
                            connectThread = new ConnectThread(device);
                            connectThread.start();
                        }catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                }
                //something.setText(address);
                Log.e("+++++++++++++",address);
            }
        });
        //BTN_send点击事件
        BTN_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String str = "123abc!?:";
                if (connectedThread == null) {
                    Toast.makeText(context,"未连接设备",LENGTH_SHORT).show();
                }else {
                    connectedThread.write(str.getBytes());
                }
            }
        });
        //BTN_close点击事件
        BTN_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectThread.cancel();
            }
        });
    }




    public void OpenBluetooth(){
        if (bluetoothAdapter == null) {
            Toast.makeText(context,"当前设备没有蓝牙", LENGTH_SHORT).show();
            return;
        }else {
            if(!bluetoothAdapter.isEnabled()){
                bluetoothAdapter.enable();
                Toast.makeText(context,"正在打开蓝牙", LENGTH_SHORT).show();
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                // 设置蓝牙可见性，最多300秒
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                context.startActivity(intent);
            }
        }
    }
    public void SeeOldBlueDevices(){
        Set<BluetoothDevice> OldbluetoothDevices = bluetoothAdapter.getBondedDevices();
        if (OldbluetoothDevices.size()>0){
            for (BluetoothDevice device:OldbluetoothDevices) {
                Map<String,String> item = new HashMap<String,String>();
                item.put("name", device.getName());
                item.put("address", device.getAddress());
                list.add(item);
                Log.e("+++++++++++++",device.getName() + " : 地址 ：" + device.getAddress());
            }
            adapter = new SimpleAdapter(this,list,android.R.layout.simple_list_item_2,new String[]{"name","address"},new int[]{android.R.id.text1,android.R.id.text2});
            Bluelist.setAdapter(adapter);
        }
    }
    private BroadcastReceiver searchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() == BluetoothDevice.BOND_NONE){
                    String str_name = device.getName();
                    String str_address = device.getAddress();
                    Map<String,String> item = new HashMap<String,String>();
                    item.put("name", str_name+"未匹配");
                    item.put("address", str_address);
                    Log.e("+++++++++++++",str_name+str_address);
                    if (list.indexOf(item) == -1){
                        list.add(item);
                    }
                    adapter.notifyDataSetChanged();
                }
            }else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                Log.e("+++++++++++++","ACTION_DISCOVERY_FINISHED");
                Toast.makeText(context,"搜索完成", LENGTH_SHORT).show();
                refresh_blulist.setRefreshing(false);
            }else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int connectState = device.getBondState();
                switch (connectState) {
                    case BluetoothDevice.BOND_NONE:
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Toast.makeText(context,"正在匹配设备", LENGTH_SHORT).show();
                        Log.e("+++++++++++++","BOND_BONDING");
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        list.clear();
                        SeeOldBlueDevices();
                        Toast.makeText(context,"匹配设备完成", LENGTH_SHORT).show();
                        Log.e("+++++++++++++","BOND_BONDED");
                        try{
                            connectThread = new ConnectThread(device);
                            connectThread.start();
                        }catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                Log.e("+++++++++++++","BluetoothDevice.ACTION_ACL_CONNECTED");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Toast.makeText(context,"连接设备成功", LENGTH_SHORT).show();
                textView.setText("已连接"+device.getName());
            }else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                Log.e("+++++++++++++","BluetoothDevice.ACTION_ACL_DISCONNECTED");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Toast.makeText(context,"设备已断开连接", LENGTH_SHORT).show();
                textView.setText("下拉刷新蓝牙设备列表,或点击列表连接设备");
            }
        }
    };

    @SuppressLint("HandlerLeak")
    private android.os.Handler handler = new android.os.Handler() {
        public void handleMessage(Message msg) {
            //Toast.makeText(MainActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
            //recText.append((String)msg.obj);
            something.setText((String)msg.obj);
        }
    };

    private class ConnectThread extends Thread {
        private final String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";
        private final BluetoothSocket socket;
        private final BluetoothDevice devices;
        public ConnectThread(BluetoothDevice device) {
            this.devices = device;
            BluetoothSocket tmp = null;
            try {
                tmp = devices.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.socket = tmp;
        }
        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                socket.connect();
                connectedThread = new ConnectedThread(socket);
                connectedThread.start();
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException ee) {
                    ee.printStackTrace();
                }
                return;
            }
        }
        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream input = null;
            OutputStream output = null;
            try {
                input = socket.getInputStream();
                output = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.inputStream = input;
            this.outputStream = output;
        }
        public void run() {
            byte[] buff = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buff);
                    Message msg = new Message();
                    msg.obj = new String(buff,0,bytes,"utf-8");
                    handler.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
