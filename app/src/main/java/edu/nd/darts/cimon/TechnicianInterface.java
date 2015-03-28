package edu.nd.darts.cimon;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;

public class TechnicianInterface extends Activity {
	Switch wifi, bluetooth, accelerometer;
	Context context=this;
	String active_switch="";
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_technician_interface);
		wifi=(Switch)findViewById(R.id.switchWiFi);
		bluetooth=(Switch)findViewById(R.id.switchBluetooth);
		accelerometer=(Switch)findViewById(R.id.switchAccelerometer);
		wifi=(Switch)findViewById(R.id.switchWiFi);
		wifi=(Switch)findViewById(R.id.switchWiFi);

		wifi.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				// TODO Auto-generated method stub
				if(isChecked){
									
				}


			}
		});

		bluetooth.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				// TODO Auto-generated method stub
				if(isChecked){
									
				}

			}
		});
		
		accelerometer.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				// TODO Auto-generated method stub
				if(isChecked){
									
				}

			}
		});


	}






}
