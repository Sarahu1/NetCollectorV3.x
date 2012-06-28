package com.cattsoft.collect.io.file.utils;

/**
 * org.apache.commons.lang.StringUtils.
 * @author ChenXiaohong
 *
 */
public class StringUtils {
	/** 重复字符
	 * @param str 字符
	 * @param repeat 长度
	 * @return 重复长度的字符
	 */
	public static String repeat(String str, int repeat) {
		// Performance tuned for 2.0 (JDK1.4)
		if (str == null) {
			return null;
		}
		if (repeat <= 0) {
			return "";
		}
		int inputLength = str.length();
		if (repeat == 1 || inputLength == 0) {
			return str;
		}
		int outputLength = inputLength * repeat;
		switch (inputLength) {
		case 1 :
			char ch = str.charAt(0);
			char[] output1 = new char[outputLength];
			for (int i = repeat - 1; i >= 0; i--) {
				output1[i] = ch;
			}
			return new String(output1);
		case 2 :
			char ch0 = str.charAt(0);
			char ch1 = str.charAt(1);
			char[] output2 = new char[outputLength];
			for (int i = repeat * 2 - 2; i >= 0; i--, i--) {
				output2[i] = ch0;
				output2[i + 1] = ch1;
			}
			return new String(output2);
		default :
			StringBuffer buf = new StringBuffer(outputLength);
			for (int i = 0; i < repeat; i++) {
				buf.append(str);
			}
			return buf.toString();
		}
	}
	
	/** 从串中根据键获取值.
	 * @param comment 注释
	 * @param key 键名
	 * @return 值
	 */
	public static String getCommentValue(String comment, String key) {
		// 按回车符分隔
		// System.getProperty("line.separator", "\n")
		String[] datas = comment.replaceAll("\r", "").split("\n");
		String value = "";
		for (String data : datas) {
			if(data.indexOf(key) > -1) {
				value = data.substring(data.indexOf(key)).replace(key, "");
				break;
			}
		}
		return value;
	}
}
