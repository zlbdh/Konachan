package com.ess.anime.wallpaper.utils;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String validation and manipulation using regular expressions
 * @author Zero
 *
 */
public class StringUtils {

	/**
	 * Check whether a string contains Chinese characters
	 * @param str String to check
	 * @return true if it contains Chinese characters, otherwise false
	 */
	public static boolean isContainChinese(String str) {
		Pattern pattern = Pattern.compile("[\u4e00-\u9fa5]"); 
		Matcher matcher = pattern.matcher(str); 
		return matcher.find();
	}
	
	/**
	 * Keep only Chinese characters in the string
	 * @param str Input string
	 * @return String containing only Chinese characters
	 */
	public static String convertToChineseOnly(String str) {
		Pattern pattern = Pattern.compile("[^\u4e00-\u9fa5]");
		Matcher matcher = pattern.matcher(str);
		return matcher.replaceAll("");
	}
	
	/**
	 * Filter a string to retain only matching characters
	 * @param str Input string
	 * @param pattern Regular expression matching characters to retain
	 * @return Filtered string
	 */
	public static String filter(String str, Pattern pattern) {
		String filter = "";
		Matcher matcher = pattern.matcher(str);
		while (matcher.find()) {
			filter += matcher.group();
		}
		return filter;
	}

	/**
	 * Check whether a string is a URL
	 *
	 * @param str Input string
	 * @return Whether the string is a URL
	 **/
	public static boolean isURL(String str) {
		if (TextUtils.isEmpty(str)) {
			return false;
		}

		str = str.toLowerCase();

		String regex = "^((https|http|ftp|rtsp|mms)?://)"  //https、http、ftp、rtsp、mms

				+ "?(([0-9a-z_!~*'().&=+$%-]+: )?[0-9a-z_!~*'().&=+$%-]+@)?" //FTP user@ prefix

				+ "(([0-9]{1,3}\\.){3}[0-9]{1,3}" // IP-address URL, for example 199.194.52.184

				+ "|" // Allow IP addresses and domain names

				+ "([0-9a-z_!~*'()-]+\\.)*" // Domain prefix: www.

				+ "([0-9a-z][0-9a-z-]{0,61})?[0-9a-z]\\." // Second-level domain

				+ "[a-z]{2,6})" // first level domain- .com or .museum

				+ "(:[0-9]{1,5})?" // Port numbers have up to five digits, with a maximum of 65535

				+ "((/?)|" // a slash isn't required if there is no file name

				+ "(/[0-9a-z_!~*'().;?:@&=+$,%#-]+)+/?)$";

		return str.matches(regex);
	}

	/**
	 * Check whether a string starts with a network protocol
	 *
	 * @param str Input string
	 * @return Whether the string starts with a network protocol
	 */
	public static boolean isStartWidthProtocol(String str) {
		if (TextUtils.isEmpty(str)) {
			return false;
		}

		str = str.toLowerCase();
		String regex = "^((https|http|ftp|rtsp|mms)?://).*";
		return str.matches(regex);
	}
}
