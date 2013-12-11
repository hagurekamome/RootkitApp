package biz.hagurekamome.rootkitapp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.FileUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;

public class MainActivity extends Activity {
	private Button getAddrButton;
	private Button getrootButton;
	private Button rebootButton;
	private TextView msgView;
	private String cachePath;
	private long prepareKernelCred = 0;
	private long commitCreds = 0;
	private long ptmxFops = 0;
	private long addr[] = {0, 0, 0};
	static {
		System.loadLibrary("getroot");
	}

	public native int native_getroot(String cachePath, long prepare_kernel_cred, long commit_creds, long ptmx_fops);
	public native int native_getaddr(long address[]);
	boolean result = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		cachePath = this.getCacheDir().toString();
		
		getAddrButton = (Button)findViewById(R.id.button1);
		getrootButton = (Button)findViewById(R.id.button2);
		getrootButton.setEnabled(false);
		rebootButton = (Button)findViewById(R.id.button3);
		rebootButton.setEnabled(false);
		msgView = (TextView)findViewById(R.id.textView1);

		
		getAddrButton.setOnClickListener(new OnClickListener(){
		
			@Override
			public void onClick(View v) {
				
				new AsyncAppTask().execute();

			}
			
		});

		getrootButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				File suFile = writeAssetToCacheFile("su", 00644);
				if(suFile == null){
					msgView.setText("su file not found");
					return;
				}

				File busyboxFile = writeAssetToCacheFile("busybox", 00755);
				if(busyboxFile == null){
					msgView.setText("busybox file not found");
					return;
				}

				File shFile = writeAssetToCacheFile("install_tool.sh", 00755);
				if(shFile == null){
					msgView.setText("install_tool.sh file not found");
					return;
				}

				File ricFile = writeAssetToCacheFile("00stop_ric", 00644);
				if(ricFile == null){
					msgView.setText("00stopric file not found");
					return;
				}

				File apkFile = writeAssetToCacheFile("Superuser.apk", 00644);
				if(apkFile == null){
					msgView.setText("SuperSU file not found");
					return;
				}

				result = getRoot(prepareKernelCred, commitCreds, ptmxFops);
				

				suFile.delete();
				busyboxFile.delete();
				shFile.delete();
				ricFile.delete();
				apkFile.delete();
				
			}
			
		});

		rebootButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String result;
				result = executeScript("reboot.sh");
				msgView.append(result);
			}
		});

	}

	private boolean getRoot(long prepare_kernel_cred, long commit_creds, long ptmx_fops) {
		int native_resuult;
		
		try{
			native_resuult = native_getroot(cachePath, prepare_kernel_cred, commit_creds, ptmx_fops);

			switch(native_resuult){
				case 0:		//getroot success
					getrootButton.setEnabled(false);
					msgView.setText("Succeeded get root!!\n\n");
					msgView.append("Please Reboot.");
					rebootButton.setEnabled(true);
					break;
				case -1:
					msgView.setText("Get Root False!!\n\n");
					msgView.append("Because I failed to get the temporary root.");
					break;
				default:
					msgView.setText("Succeeded get the temporary root.\n\n");
					msgView.append("But Script Error.\n\n");
					msgView.append("Error Code=" + Integer.toString(native_resuult));
					msgView.append("\nPlease Check\n/data/local/tmp/err.txt");
					break;
			}
			if(native_resuult == 0)
				return(true);
			else
				return(false);
		}catch(Exception e){
			Log.e("getroot", e.toString());
			msgView.setText("Exception occured!!\n\n");
			msgView.append(e.toString());
		}
		return true;
	}

	private File writeAssetToCacheFile(String name, int mode) {
		return writeAssetToCacheFile(name, name, mode);
	}

	private File writeAssetToCacheFile(String assetName, String fileName, int mode) {
		return writeAssetToFile(assetName, new File(this.getCacheDir(), fileName), mode);
	}

	private File writeAssetToFile(String assetName, File targetFile, int mode) {
		try {
			InputStream in = this.getAssets().open(assetName);
			FileOutputStream out = new FileOutputStream(targetFile);
			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) > 0){
				out.write(buffer, 0, len);
			}
			in.close();
			out.close();
			FileUtils.setPermissions(targetFile.getAbsolutePath(), mode, -1, -1);

			return targetFile;
		} catch (IOException e) {
			e.printStackTrace();
			if (targetFile != null)
				targetFile.delete();

			return null;
		}
	}

	private String executeScript(String name) {
		File scriptFile = writeAssetToCacheFile(name, 00755);
		if (scriptFile == null)
			return "Could not find asset \"" + name + "\"";

		try {
			Process p = Runtime.getRuntime().exec(
					new String[] {
						"/system/xbin/su",
						"-c",
						scriptFile.getAbsolutePath() + " 2>&1"
					});
			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = stdout.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			while ((line = stderr.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			stdout.close();
			return sb.toString();

		} catch (IOException e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return sw.toString();
		} finally {
			scriptFile.delete();
		}
	}
	
	class AsyncAppTask extends AsyncTask<Void, Void, Integer>{
		@Override
		protected void onPreExecute() {
    		msgView.setText("Searching Addresses.\nWait a few moment...");
			getAddrButton.setEnabled(false);
			
		}

		@Override
		protected Integer doInBackground(Void... params) {
			Integer bg_result = 0;
			bg_result = native_getaddr(addr);
			return bg_result;
		}

		@Override
		 protected void onPostExecute(Integer bg_result){
			if(bg_result == 0){
				result = true;

				prepareKernelCred = addr[0];
				commitCreds = addr[1];
				ptmxFops = addr[2];
				msgView.setText("Get Address Success\n\n");
				msgView.append("0x" + Long.toHexString(prepareKernelCred)+ ": prepare_kernel_cred\n\n" );
				msgView.append("0x" + Long.toHexString(commitCreds) + ": commit_creds\n\n");
				msgView.append("0x" + Long.toHexString(ptmxFops) + ": ptmx_fops\n\n");
				msgView.append("Press GetRoot Button");

				getrootButton.setEnabled(true);
				return;
			}else {
				switch(bg_result){
				case -1:
					msgView.setText("get_kallsyms_addresses error");
					break;
				case -2:
					msgView.setText("search_functions error");
					break;
				case -3:
					msgView.setText("search_ptmx_fops_address error");
					break;
					
				}
				msgView.append("Error = " + Integer.toString(bg_result));
				result = false;
				return;
			}
			 
		 }
		
	}

}
