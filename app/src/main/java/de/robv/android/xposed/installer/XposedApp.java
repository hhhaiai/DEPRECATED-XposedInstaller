package de.robv.android.xposed.installer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.preference.PreferenceManager;

import java.io.File;

import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.NotificationUtil;
import de.robv.android.xposed.installer.util.RepoLoader;

public class XposedApp extends Application implements
		ActivityLifecycleCallbacks {
	public static final String TAG = "XposedInstaller";

	@SuppressLint("SdCardPath")
	public static final String BASE_DIR = "/data/data/de.robv.android.xposed.installer/";
	public static final String ENABLED_MODULES_LIST_FILE = XposedApp.BASE_DIR
			+ "conf/enabled_modules.list";

	private static XposedApp mInstance = null;
	private static Thread mUiThread;
	private static Handler mMainHandler;

	private boolean mIsUiLoaded = false;
	private Activity mCurrentActivity = null;
	private SharedPreferences mPref;

	public static XposedApp getInstance() {
		return mInstance;
	}

	public static void runOnUiThread(Runnable action) {
		if (Thread.currentThread() != mUiThread) {
			mMainHandler.post(action);
		} else {
			action.run();
		}
	}

	// This method is hooked by XposedBridge to return the current version
	public static int getActiveXposedVersion() {
		return -1;
	}

	public static SharedPreferences getPreferences() {
		return mInstance.mPref;
	}

	public static void getAndSetColor(Activity activity) {
		setColors(activity, getColor(activity));
	}

	public static int getColor(Activity activity) {
		SharedPreferences prefs = activity.getSharedPreferences(
				activity.getPackageName() + "_preferences", MODE_PRIVATE);
		int defaultColor = activity.getResources().getColor(
				R.color.actionBar_background);

		return prefs.getInt("colors", defaultColor);
	}

	@SuppressWarnings("all")
	private static void setColors(Activity activity, Object value) {
		int color = (int) value;
		activity.getActionBar().setBackgroundDrawable(new ColorDrawable(color));
		if (Build.VERSION.SDK_INT >= 21) {
			activity.getWindow().setStatusBarColor(darkenColor(color, 0.85f));
		}
	}

	/**
	 * @author PeterCxy https://github.com/PeterCxy/Lolistat/blob/aide/app/src/
	 *         main/java/info/papdt/lolistat/support/Utility.java
	 */
	private static int darkenColor(int color, float factor) {
		float[] hsv = new float[3];
		Color.colorToHSV(color, hsv);
		hsv[2] *= factor;
		return Color.HSVToColor(hsv);
	}

	public void onCreate() {
		super.onCreate();
		mInstance = this;
		mUiThread = Thread.currentThread();
		mMainHandler = new Handler();

		mPref = PreferenceManager.getDefaultSharedPreferences(this);
		createDirectories();
		cleanup();
		NotificationUtil.init();
		AssetUtil.checkStaticBusyboxAvailability();
		AssetUtil.removeBusybox();

		registerActivityLifecycleCallbacks(this);
	}

	private void createDirectories() {
		mkdirAndChmod("bin", 00771);
		mkdirAndChmod("conf", 00771);
		mkdirAndChmod("log", 00777);
	}

	private void cleanup() {
		if (!mPref.getBoolean("cleaned_up_sdcard", false)) {
			if (Environment.getExternalStorageState().equals(
					Environment.MEDIA_MOUNTED)) {
				File sdcard = Environment.getExternalStorageDirectory();
				new File(sdcard, "Xposed-Disabler-CWM.zip").delete();
				new File(sdcard, "Xposed-Disabler-Recovery.zip").delete();
				new File(sdcard, "Xposed-Installer-Recovery.zip").delete();
				mPref.edit().putBoolean("cleaned_up_sdcard", true).apply();
			}
		}

		if (!mPref.getBoolean("cleaned_up_debug_log", false)) {
			new File(XposedApp.BASE_DIR + "log/debug.log").delete();
			new File(XposedApp.BASE_DIR + "log/debug.log.old").delete();
			mPref.edit().putBoolean("cleaned_up_debug_log", true).apply();
		}
	}

	private void mkdirAndChmod(String dir, int permissions) {
		dir = BASE_DIR + dir;
		new File(dir).mkdir();
		FileUtils.setPermissions(dir, permissions, -1, -1);
	}

	public boolean areDownloadsEnabled() {
		if (!mPref.getBoolean("enable_downloads", true))
			return false;

		if (checkCallingOrSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)
			return false;

		return true;
	}

	public void updateProgressIndicator() {
		final boolean isLoading = RepoLoader.getInstance().isLoading()
				|| ModuleUtil.getInstance().isLoading();
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (XposedApp.this) {
					if (mCurrentActivity != null)
						mCurrentActivity
								.setProgressBarIndeterminateVisibility(isLoading);
				}
			}
		});
	}

	@Override
	public synchronized void onActivityCreated(Activity activity,
			Bundle savedInstanceState) {
		if (mIsUiLoaded)
			return;

		RepoLoader.getInstance().triggerFirstLoadIfNecessary();
		mIsUiLoaded = true;
	}

	@Override
	public synchronized void onActivityResumed(Activity activity) {
		mCurrentActivity = activity;
		updateProgressIndicator();
	}

	@Override
	public synchronized void onActivityPaused(Activity activity) {
		activity.setProgressBarIndeterminateVisibility(false);
		mCurrentActivity = null;
	}

	@Override
	public void onActivityStarted(Activity activity) {
	}

	@Override
	public void onActivityStopped(Activity activity) {
	}

	@Override
	public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
	}

	@Override
	public void onActivityDestroyed(Activity activity) {
	}
}