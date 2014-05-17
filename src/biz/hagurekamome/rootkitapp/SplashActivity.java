package biz.hagurekamome.rootkitapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

public class SplashActivity extends Activity{
	
	Button OkButton;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.slpash);
		OkButton = (Button)findViewById(R.id.buttonOK);
		
		OkButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO 自動生成されたメソッド・スタブ
				Handler handler = new Handler();  

				handler.postDelayed(new Runnable() {  

					@Override 
					public void run() {  

						startActivity(new Intent(SplashActivity.this, MainActivity.class));  

						finish();

					}
				
				}, 0);
			}
		});

	}

	@Override
	protected void onDestroy(){
		super.onDestroy();
		finish();
	}
}
