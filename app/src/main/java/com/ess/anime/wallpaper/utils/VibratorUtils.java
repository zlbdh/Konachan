package com.ess.anime.wallpaper.utils;

import android.app.Service;
import android.content.Context;
import android.os.Vibrator;

/**
 * Device vibration utilities <br/><br/>
 * <b>Required permission:</b><br/>
 * &emsp;&lt;uses-permission android:name="android.permission.VIBRATE" /&gt;
 * @author Zero
 *
 */
public class VibratorUtils {

	/**
	 * Vibrate once
	 * @param context Context
	 * @param milliseconds Vibration duration
	 */
	public static void Vibrate(Context context, long milliseconds) { 
		Vibrator vib = (Vibrator) context.getSystemService(Service.VIBRATOR_SERVICE); 
		vib.vibrate(milliseconds); 
	} 

	/**
	 * Use multiple vibration segments per pattern
	 * @param context Context
	 * @param pattern Durations in each pattern: [off, on, off, on...]
	 * @param repeat Repeat setting: -1 disables repeating, 0 repeats indefinitely
	 */
	public static void Vibrate(Context context, long[] pattern, int repeat) { 
		Vibrator vib = (Vibrator) context.getSystemService(Service.VIBRATOR_SERVICE); 
		vib.vibrate(pattern, repeat); 
	} 
}
