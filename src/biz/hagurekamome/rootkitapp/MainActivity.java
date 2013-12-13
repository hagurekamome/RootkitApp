package biz.hagurekamome.rootkitapp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
	private int scriptResult;
	private long prepareKernelCred = 0;
	private long commitCreds = 0;
	private long ptmxFops = 0;
	private long addr[] = {0, 0, 0};
	static {
		System.loadLibrary("getroot");
	}

	public native int native_getroot(long prepare_kernel_cred, long commit_creds, long ptmx_fops);
	public native int native_getaddr(long address[]);
	boolean result = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		getAddrButton = (Button)findViewById(R.id.button1);
		getrootButton = (Button)findViewById(R.id.button2);
		getrootButton.setEnabled(false);
		rebootButton = (Button)findViewById(R.id.button3);
		rebootButton.setEnabled(false);
		msgView = (TextView)findViewById(R.id.textView1);

		
		getAddrButton.setOnClickListener(new OnClickListener(){
		
			@Override
			public void onClick(View v) {
				
				AsyncAppTask task = new AsyncAppTask();
				task.execute();
				task = null;

			}
			
		});

		getrootButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {

				result = getRoot(prepareKernelCred, commitCreds, ptmxFops);

				if(!result)
					return;

				if(!installTool())
					return;
					
				msgView.append("Succeeded get root!!\n\n");
				msgView.append("Please Reboot.");
				rebootButton.setEnabled(true);
			}
		});

		rebootButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String str_result = executeReboot();
				msgView.append(str_result);
			}
		});

	}

	private boolean getRoot(long prepare_kernel_cred, long commit_creds, long ptmx_fops) {
		int native_resuult;
		
		try{
			native_resuult = native_getroot(prepare_kernel_cred, commit_creds, ptmx_fops);

			switch(native_resuult){
				case 0:		//getroot success
					getrootButton.setEnabled(false);
					break;
				case -1:
					msgView.append("Get Root False!!\n\n");
					msgView.append("Because I failed to get the temporary root.");
					break;
				default:
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

	private String executeCommand(String [] strCommand) {
		try {
			Process p = Runtime.getRuntime().exec(strCommand);
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

			stderr.close();
			stdout.close();
			p.waitFor();
			scriptResult = p.exitValue();
			return sb.toString();

		} catch (IOException e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return sw.toString();
		} catch (InterruptedException e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return sw.toString();
		}
	}

	private String executeReboot() {
		try {
			Process p = Runtime.getRuntime().exec("reboot");

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
			stderr.close();
			return sb.toString();

		} catch (IOException e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return sw.toString();
		}
	}
	
	class AsyncAppTask extends AsyncTask<Void, Void, Integer>{
		@Override
		protected void onPreExecute() {
    		msgView.setText("Searching Addresses.\nWait a few moment...\n\n");
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
				msgView.append("Get Address Success\n\n");
				msgView.append("0x" + Long.toHexString(prepareKernelCred)+ " : prepare_kernel_cred\n" );
				msgView.append("0x" + Long.toHexString(commitCreds) + " : commit_creds\n");
				msgView.append("0x" + Long.toHexString(ptmxFops) + " : ptmx_fops\n\n");
				msgView.append("Press GetRoot Button\n");

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

	private boolean copyFileAndSetPermission(File src, File dist, int mode){

		if(!FileUtils.copyFile(src, dist))
			return false;

		if(FileUtils.setPermissions(dist.getAbsolutePath(), mode, -1, -1) != 0)
			return false;

		return true;
	}

	private boolean installTool(){
		String str_result;

		msgView.append("\nSucceedes get temporary root.\n\n");
		
		File suFile = writeAssetToCacheFile("su", 00644);
		if(suFile == null){
			msgView.append("su file not found");
			return false;
		}

		File busyboxFile = writeAssetToCacheFile("busybox", 00755);
		if(busyboxFile == null){
			msgView.append("busybox file not found");
			return false;
		}

		File ricFile = writeAssetToCacheFile("00stop_ric", 00644);
		if(ricFile == null){
			msgView.append("00stopric file not found");
			return false;
		}

		File apkFile = writeAssetToCacheFile("Superuser.apk", 00644);
		if(apkFile == null){
			msgView.append("SuperSU file not found");
			return false;
		}

		//Disable ric
		int ricType = 0;

		if(new File("/sbin/ric").exists()){
			msgView.append("Disable ric...");
			str_result = executeCommand(new String [] {busyboxFile.getAbsolutePath(), "pkill", "/sbin/ric"});
			msgView.append(str_result);
			if(!reMount("/", "rw"))
				return false;
			str_result = executeCommand(new String [] {"/system/bin/rm", "/sbin/ric"});
			msgView.append(str_result);
			if(!reMount("/", "ro"))
				return false;
			str_result = executeCommand(new String [] {busyboxFile.getAbsolutePath(), "pkill", "/sbin/ric"});
			msgView.append(str_result);
			msgView.append("OK\n\n");
			ricType = 1;
		}
		if(new File("/systen/bin/ric").exists()){
			msgView.append("Disable ric...");
			str_result = executeCommand(new String [] {"stop", "ric", "2>&1"});
			msgView.append(str_result);
			if(!reMount("/system", "rw"))
				return false;
			str_result = executeCommand(new String [] {"rm", "/system/bin/ric", "2>&1"});
			msgView.append(str_result);
			if(!reMount("/system", "ro"))
				return false;
			msgView.append("OK\n\n");
			ricType = 2;
		}

		//remount system
		if(!reMount("/system", "rw"))
			return false;

		//Cpoy su
		msgView.append("Copy su and set permission...");
		if(!copyFileAndSetPermission(suFile, new File("/system/xbin/su"), 06755)){
			msgView.append("su file can't copy /system/xbin\n\n");
			return false;
		}
		msgView.append("OK\n\n");
		
		msgView.append("Make symbolic link su...");
		str_result =executeCommand(new String [] {"/system/bin/ln", "-s", "/system/xbin/su", "/system/bin/su"});
		if(str_result != ""){
			if(str_result.indexOf("File exists") == 0 )
				msgView.append("\n" + str_result +"\n");
		}
		msgView.append("OK\n\n");

		//Cpoy SuperSU
		msgView.append("Copy Superuser.apk and set permission...");
		if(!copyFileAndSetPermission(apkFile, new File("/system/app/Superuser.apk"), 00644)){
			msgView.append("SuperSU file can't copy.");
			return false;
		}
		msgView.append("OK\n\n");

		//Copy busybox
		msgView.append("Copy busybox and set permission...");
		if(!copyFileAndSetPermission(busyboxFile, new File("/system/xbin/busybox"), 00755)){
			msgView.append("busybox file can't copy.");
			return false;
		}
		msgView.append("OK\n\n");
		str_result =executeCommand(new String [] {"/system/bin/chown", "root.shell", "/system/xbin/busybox"});
		if(str_result != ""){
			msgView.append("\n" + str_result +"\n");
			return false;
		}

		//Install busybox
		msgView.append("Install busybox...");
		str_result =executeCommand(new String [] {"/system/xbin/busybox", "--install", "-s", "/system/xbin"});
		if(str_result != ""){
			msgView.append("\n" + str_result +"\n");
			return false;
		}
		msgView.append("OK\n\n");
		
		if(ricType == 1){
			msgView.append("Make init.d directory...");
			File initD = new File("/system/etc/init.d");
			if(!initD.exists()){
				if(!initD.mkdir()){
					msgView.append("Can't create /system/etc/init.d\n");
					return false;
				}
			}
			if(FileUtils.setPermissions(initD.getAbsolutePath(), 00755, -1, -1) != 0){
				msgView.append("Can't change permission /system/eyc/init.d\n");
				return false;
			}
			msgView.append("OK\n\n");
			
			int modefyflg = 0;
			if(new File("/system/etc/hw_config.sh").exists()){
				if(!copyFileAndSetPermission(new File("/system/etc/hw_config.sh"), new File("/data/local/tmp/hw_config.sh.org"), 00644)){
					msgView.append("Backup error hw_config.sh.\n" );
					return false;
				}
				modefyflg = 1;
			}else {
				if(new File("/system/etc/init.qcom.post_boot.sh").exists()){
					if(!copyFileAndSetPermission(new File("/system/etc/init.qcom.post_boot.sh"), new File("/data/local/tmp/init.qcom.post_boot.sh.org"), 00644)){
						msgView.append("Backup error init.qcom.post_boot.sh.\n" );
						return false;
					}
					modefyflg = 2;
				}else{
					msgView.append("hw_config.sh or init.qcom.post_boot.sh file not found\n\n");
					return false;
				}
			}

			String modefyFile = "/system/etc/hw_config.sh";
			if(modefyflg == 2)
				modefyFile = "/system/etc/init.qcom.post_boot.sh";
			
			msgView.append("Modefy " + modefyFile + "...");
			try{
				String enableInitd = "/system/xbin/busybox run-parts /system/etc/init.d\n";
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(modefyFile, true), "UTF-8"));
				bw.append(enableInitd);
				bw.close();
			}catch (FileNotFoundException e){
				msgView.append(e.toString());
				return false;
			}catch (IOException e){
				msgView.append(e.toString());
				return false;
			}
			msgView.append("OK\n\n");
		}
		
		//Copy 00stop_ric
		if(ricType == 1){
			msgView.append("Copy 00stop_ric and set permission...");
			if(!copyFileAndSetPermission(ricFile, new File("/system/etc/init.d/00stopo_ric"), 00755)){
				msgView.append("busybox file can't copy.");
				return false;
			}
			msgView.append("OK\n\n");
		}
		
		//remount /system
		if(!reMount("/system", "ro"))
			return false;

		suFile.delete();
		busyboxFile.delete();
		ricFile.delete();
		apkFile.delete();
		
		return true;
	}

	private boolean reMount(String strDir, String mode){
		String str_result = executeCommand(new String [] {"/system/bin/mount", "-o", "remount," + mode, strDir});
		if(mode == "ro"){
			if(str_result.indexOf("Device or resource busy") != 0)
				return true;
			else {
				msgView.append("");
				return false;
			}
		}
		if(str_result == "")
			return true;

		msgView.append(str_result);
		return false;
	}
}
