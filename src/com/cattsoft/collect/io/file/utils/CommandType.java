package com.cattsoft.collect.io.file.utils;

/** 输出目录支持命令类型
 * @author ChenXiaohong
 */
public enum CommandType {
	DIG("dig"),
	/*** dig(网站)*/
	DIG_TRACE("dig_trace"),
	/*** dig(根和顶级)*/
	DIG_NOREC("dig_norec"),
	/*** dig((根和顶级))*/
	DIG_CNNS("dig_cnns"),
	PING("ping"),
	TRACE("trace"),
	PATHCHAR("pathchar"),
	/*** 命令无效*/
	@Deprecated
	IPERF("iperf"),
	/*** 未支持类型.*/
	UNKNOW("unknow");
	
	/*** 命令类型.*/
	public final String type;
	
	/**
	 * @param type 命令类型
	 */
	private CommandType(String type) {
		this.type = type;
	}
	
	/** 由文件名或字符串信息,判断命令类型.
	 * @param name 名称
	 * @return 枚举值
	 */
	public static CommandType decide(String name) {
		if (name.toLowerCase().indexOf("ping") > -1)
			return PING;
		if (name.toLowerCase().indexOf("dig") > -1) {
			if (name.toLowerCase().indexOf("trace") > -1)
				return DIG_TRACE;
			if(name.toLowerCase().indexOf("norec") > -1)
				return DIG_NOREC;
			if(name.toLowerCase().indexOf("cn") > -1 && name.toLowerCase().indexOf("ns") > -1)
				return DIG_CNNS;
			return DIG;
		}
		if (name.toLowerCase().indexOf("trace") > -1)
			return TRACE;
		if (name.toLowerCase().indexOf("iperf") > -1)
			return IPERF;
		if (name.toLowerCase().indexOf("pathchar") > -1)
			return PATHCHAR;
		// 由名称截取命令类型
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
				"m\\d+_([\\w\\_]+)_result").matcher(name);
		if (matcher.find()) {
			String command = matcher.group(1);
			if (!"".equals(command)) {
				// 如果要支持未知命令,在此进行
			}
		}
		// 未知命令类型
		return CommandType.UNKNOW;
	}
}
